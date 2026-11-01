#!/usr/bin/env python3
"""PreToolUse guard: blocks git commands that break docs/git-workflow.md."""
import json
import os
import re
import shlex
import subprocess
import sys
from types import SimpleNamespace

PROTECTED = ("main", "dev")
NO_GIT_WRITE_AGENTS = ("coder", "reviewer")
AGENT_FORBIDDEN = ("commit", "merge", "push", "tag", "rebase", "cherry-pick")

BRANCH_RE = re.compile(
    r"(feature|fix|chore)/[a-z0-9]+(-[a-z0-9]+)*|(release|hotfix)/\d+\.\d+\.\d+|dev"
)
SHORT_LIVED_RE = re.compile(r"(feature|fix|chore)/")
RELEASE_RE = re.compile(r"(release|hotfix)/")
TAG_RE = re.compile(r"v\d+\.\d+\.\d+")
SUBJECT_RE = re.compile(
    r"(feat|fix|refactor|test|docs|build|ci|chore|perf)"
    r"(\((parser|dedup|pairing|balance|db|notif|ui|ota|build)\))?!?: "
    r"[a-z0-9`].*[^.\s]"
)
MAX_SUBJECT = 72
COAUTHOR_RE = re.compile(r"(?im)(^|=|\n)\s*co-authored-by\s*[:=]")

PUNCTUATION = ";&|()<>\n"
WRAPPERS = {
    "command", "builtin", "time", "nice", "nohup", "sudo", "env", "exec",
    "timeout", "xargs", "do", "then", "else", "if", "while", "{",
}
SHELLS = {"sh", "bash", "zsh", "dash", "pwsh", "powershell", "cmd"}
SHELL_FLAGS = {"-c", "-lc", "-command", "/c"}


class Blocked(Exception):
    pass


def exe(token):
    return re.sub(r"\.exe$", "", re.split(r"[\\/]", token)[-1].lower())


def git(cwd, *args):
    try:
        done = subprocess.run(
            ["git", "-C", cwd, *args], capture_output=True, text=True, errors="replace"
        )
    except OSError:
        return 1, ""
    return done.returncode, done.stdout.strip()


def load_state(ctx):
    if ctx.loaded == ctx.cwd:
        return
    ctx.loaded = ctx.cwd
    ctx.head = git(ctx.cwd, "symbolic-ref", "--short", "-q", "HEAD")[1]
    ctx.born = git(ctx.cwd, "rev-parse", "-q", "--verify", "HEAD")[0] == 0
    ctx.merge_head = git(ctx.cwd, "rev-parse", "-q", "--verify", "MERGE_HEAD")[0] == 0
    squash_msg = git(ctx.cwd, "rev-parse", "--git-path", "SQUASH_MSG")[1]
    ctx.squash = bool(squash_msg) and os.path.exists(os.path.join(ctx.cwd, squash_msg))


def has(args, *flags):
    return any(a in flags for a in args)


def short(args, letters):
    """True when a clustered short flag such as -fd contains one of letters."""
    return any(
        re.fullmatch(r"-[A-Za-z]+", a) and not a.startswith("-m") and set(a[1:]) & set(letters)
        for a in args
    )


def positional(args, value_flags=()):
    result, skip = [], False
    for arg in args:
        if skip:
            skip = False
        elif arg in value_flags:
            skip = True
        elif arg == "--":
            break
        elif not arg.startswith("-"):
            result.append(arg)
    return result


def remote_name(ref):
    return ref[len("origin/"):] if ref.startswith("origin/") else ref


def valid_branch(name):
    if not BRANCH_RE.fullmatch(name):
        raise Blocked(
            f"branch name '{name}' is invalid. Use feature/x, fix/x, chore/x "
            "(lowercase, hyphens), release/x.y.z, or hotfix/x.y.z."
        )


def new_branch(name, start, ctx):
    valid_branch(name)
    expected = "main" if name == "dev" or name.startswith("hotfix/") else "dev"
    base = remote_name(start) if start else ctx.head
    if base != expected:
        raise Blocked(f"'{name}' must branch from {expected}, not '{base}'.")
    ctx.head = name


def check_switch(rest, ctx, create_flags, force_create_flags):
    if has(rest, "--", ".", "-f", "--force", "--discard-changes"):
        raise Blocked("this discards working tree changes. Ask the user.")
    if has(rest, *force_create_flags):
        raise Blocked("force-creating a branch resets it. Ask the user.")
    pos = positional(rest)
    if has(rest, *create_flags) and pos:
        new_branch(pos[0], pos[1] if len(pos) > 1 else "", ctx)
    elif pos:
        ctx.head = remote_name(pos[0])


def check_checkout(rest, ctx):
    check_switch(rest, ctx, ("-b",), ("-B",))


def check_git_switch(rest, ctx):
    check_switch(rest, ctx, ("-c", "--create"), ("-C", "--force-create"))


def check_branch(rest, ctx):
    if short(rest, "DMCf") or has(rest, "--force"):
        raise Blocked("forced branch delete, rename, or reset. Use -d or ask the user.")
    pos = positional(rest)
    if has(rest, "-d", "--delete"):
        if set(pos) & set(PROTECTED):
            raise Blocked("main and dev cannot be deleted.")
    elif has(rest, "-m", "--move"):
        if set(pos[:-1]) & set(PROTECTED) or (len(pos) == 1 and ctx.head in PROTECTED):
            raise Blocked("main and dev cannot be renamed.")
        if pos:
            valid_branch(pos[-1])
    elif pos and not any(a.startswith("-") for a in rest):
        new_branch(pos[0], pos[1] if len(pos) > 1 else "", ctx)


def check_add(rest, ctx):
    if has(rest, "--all", "--update", "--force", ".", "*", ":/", "./") or short(rest, "Auf"):
        raise Blocked("stage files by name. Do not use -A, -u, -f, or '.'.")


def read_message(rest, ctx):
    """Returns the message text, "" when none is given, None when it cannot be read."""
    for i, arg in enumerate(rest):
        if arg in ("-m", "--message"):
            return rest[i + 1] if i + 1 < len(rest) else ""
        if arg.startswith("--message="):
            return arg.split("=", 1)[1]
        if arg.startswith("-m"):
            return arg[2:]
        path = None
        if arg in ("-F", "--file"):
            path = rest[i + 1] if i + 1 < len(rest) else ""
        elif arg.startswith("--file="):
            path = arg.split("=", 1)[1]
        if path is not None:
            if path == "-":
                return None  # shortcut: -F - reads stdin, so the hook cannot see it
            try:
                with open(os.path.join(ctx.cwd, path), encoding="utf-8") as f:
                    return f.read()
            except OSError:
                return None
    return ""


def subject_of(message):
    heredoc = re.search(r"<<-?\s*['\"]?\w+['\"]?[^\n]*\n(.*)", message, re.S)
    if heredoc:
        message = heredoc.group(1)
    lines = message.strip("@ \r\n").splitlines()
    return lines[0].rstrip() if lines else ""


def check_subject(subject):
    if len(subject) > MAX_SUBJECT or not SUBJECT_RE.fullmatch(subject):
        raise Blocked(
            f"commit subject '{subject}' is invalid. Use type(scope): lowercase imperative "
            f"text, at most {MAX_SUBJECT} chars, no final period. See docs/git-workflow.md."
        )


def check_no_coauthor(texts):
    if any(COAUTHOR_RE.search(text) for text in texts):
        raise Blocked("Co-Authored-By trailers are not allowed. Remove the line.")


def check_commit(rest, ctx):
    check_no_coauthor(rest)
    if short(rest, "an") or has(rest, "--all"):
        raise Blocked("stage files by name. Do not use commit -a or -n.")
    if ctx.head in PROTECTED and ctx.born and not (ctx.merge_head or ctx.squash):
        raise Blocked(f"no direct commits on {ctx.head}. Use a branch from docs/git-workflow.md.")
    if ctx.merge_head:
        return
    if not ctx.parsed:
        return  # shortcut: unparsable quoting, the message check is skipped
    message = read_message(rest, ctx)
    if message is None or has(rest, "--no-edit"):
        return
    if not message:
        raise Blocked("pass the message with -m or -F. An editor cannot open here.")
    check_no_coauthor([message])
    check_subject(subject_of(message))
    ctx.born, ctx.squash = True, False


def check_merge(rest, ctx):
    if has(rest, "--abort", "--quit", "--continue"):
        return
    check_no_coauthor(rest)
    pos = positional(rest, ("-m", "-F", "--message", "--file", "-s", "-X", "--strategy"))
    source = remote_name(pos[0]) if pos else ""
    squash, no_ff = "--squash" in rest, "--no-ff" in rest
    if source == ctx.head:
        return
    if ctx.head == "main" and not (RELEASE_RE.match(source) and no_ff and not squash):
        raise Blocked("main takes only release/* or hotfix/* with --no-ff.")
    if ctx.head == "dev":
        if SHORT_LIVED_RE.match(source):
            if not squash:
                raise Blocked("merge feature/, fix/, chore/ into dev with --squash.")
        elif not (RELEASE_RE.match(source) and no_ff and not squash):
            raise Blocked("dev takes feature/, fix/, chore/ with --squash, or release/, hotfix/ with --no-ff.")
    ctx.squash = ctx.squash or squash


def check_push(rest, ctx):
    if (
        any(a.startswith("--force") for a in rest)
        or has(rest, "--mirror", "--delete", "--prune", "--all")
        or short(rest, "fd")
    ):
        raise Blocked("force, delete, mirror, and --all pushes are not allowed.")
    pos = positional(rest, ("-o", "--push-option", "--repo", "--receive-pack"))
    refs = pos[1:]
    if any(r.startswith(("+", ":")) for r in refs):
        raise Blocked("forced or deleting refspecs are not allowed.")
    targets = [ctx.head if r == "HEAD" else r.split(":")[-1] for r in refs] or [ctx.head]
    if "main" in targets:
        raise Blocked("only the user pushes main.")


def check_rebase(rest, ctx):
    if has(rest, "--abort", "--continue", "--skip", "--quit"):
        return
    if has(rest, "-i", "--interactive") or short(rest, "i"):
        raise Blocked("interactive rebase cannot run here.")
    if ctx.head in PROTECTED:
        raise Blocked(f"never rebase {ctx.head}.")


def check_reset(rest, ctx):
    if "--hard" in rest:
        raise Blocked("git reset --hard destroys work. Ask the user.")


def check_clean(rest, ctx):
    if short(rest, "f") or "--force" in rest:
        raise Blocked("git clean -f deletes files. Ask the user.")


def check_restore(rest, ctx):
    if "--staged" not in rest or short(rest, "W") or "--worktree" in rest:
        raise Blocked("git restore discards changes. Only --staged is allowed.")


def check_tag(rest, ctx):
    if has(rest, "-l", "--list") or not rest:
        return
    if short(rest, "d") or has(rest, "--delete", "--force") or short(rest, "f"):
        raise Blocked("tags are not deleted or moved.")
    pos = positional(rest, ("-m", "-F", "--message", "--file"))
    if pos and not TAG_RE.fullmatch(pos[0]):
        raise Blocked(f"tag '{pos[0]}' is invalid. Use vX.Y.Z.")
    if pos and ctx.head != "main":
        raise Blocked("tags go on main only.")


def check_stash(rest, ctx):
    if rest and rest[0] in ("drop", "clear"):
        raise Blocked("dropping a stash loses work. Ask the user.")


def check_reflog(rest, ctx):
    if rest and rest[0] in ("expire", "delete"):
        raise Blocked("rewriting the reflog removes the recovery path.")


def check_worktree(rest, ctx):
    if rest and rest[0] == "add" and "-b" in rest:
        i = rest.index("-b")
        if i + 1 < len(rest):
            valid_branch(rest[i + 1])


def check_forbidden(rest, ctx):
    raise Blocked("history rewriting tools are not allowed.")


CHECKS = {
    "add": check_add,
    "branch": check_branch,
    "checkout": check_checkout,
    "clean": check_clean,
    "commit": check_commit,
    "filter-branch": check_forbidden,
    "filter-repo": check_forbidden,
    "merge": check_merge,
    "push": check_push,
    "rebase": check_rebase,
    "reflog": check_reflog,
    "reset": check_reset,
    "restore": check_restore,
    "stash": check_stash,
    "switch": check_git_switch,
    "tag": check_tag,
    "worktree": check_worktree,
}


def check_git(args, ctx):
    i = 0
    while i < len(args) and args[i].startswith("-"):
        flag = args[i]
        if flag in ("-C", "-c", "--git-dir", "--work-tree"):
            value = args[i + 1] if i + 1 < len(args) else ""
            if flag == "-C":
                ctx.cwd = os.path.join(ctx.cwd, value)
            if flag == "-c" and re.match(r"(?i)(core\.hookspath|commit\.gpgsign)=", value):
                raise Blocked("do not override hooks or signing.")
            i += 2
        else:
            i += 1
    if i >= len(args):
        return
    sub, rest = args[i], args[i + 1:]
    if has(rest, "--no-verify", "--no-gpg-sign"):
        raise Blocked("--no-verify and --no-gpg-sign are not allowed.")
    if ctx.agent in NO_GIT_WRITE_AGENTS and sub in AGENT_FORBIDDEN:
        raise Blocked(f"{ctx.agent} never runs git {sub}. Report back to the main session.")
    load_state(ctx)
    check = CHECKS.get(sub)
    if check:
        check(rest, ctx)


def tokenize(command, tool):
    """Returns (segments, parsed). parsed is False when quoting could not be read."""
    try:
        lexer = shlex.shlex(command, posix=True, punctuation_chars=PUNCTUATION)
        lexer.whitespace_split = True
        lexer.whitespace = " \t\r"
        lexer.commenters = ""
        if tool == "PowerShell":
            lexer.escape = ""
        tokens = list(lexer)
    except ValueError:
        # shortcut: fallback ignores quoting, so a message line can false-positive
        return [part.split() for part in re.split(r"[;&|\n]+", command)], False
    segments, current = [], []
    for token in tokens:
        if set(token) <= set(PUNCTUATION):
            segments.append(current)
            current = []
        else:
            current.append(token)
    segments.append(current)
    return segments, True


def check_command(command, tool, ctx):
    segments, ctx.parsed = tokenize(command, tool)
    for segment in segments:
        while segment and (
            re.fullmatch(r"\w+=.*|\d+[smhd]?", segment[0], re.S) or exe(segment[0]) in WRAPPERS
        ):
            segment = segment[1:]
        if not segment:
            continue
        name = exe(segment[0])
        if name in ("cd", "set-location", "pushd") and len(segment) > 1:
            target = os.path.join(ctx.cwd, segment[1])
            if os.path.isdir(target):
                ctx.cwd = target
        elif name in SHELLS:
            for i, arg in enumerate(segment[1:-1], 1):
                if arg.lower() in SHELL_FLAGS:
                    check_command(segment[i + 1], tool, ctx)
                    break
        elif name == "git":
            check_git(segment[1:], ctx)


def main():
    raw = sys.stdin.buffer.read().decode("utf-8", "replace")
    try:
        data = json.loads(raw)
        tool = data.get("tool_name", "")
        command = (data.get("tool_input") or {}).get("command", "")
        if tool in ("Bash", "PowerShell") and "git" in command:
            ctx = SimpleNamespace(
                cwd=data.get("cwd") or os.getcwd(),
                agent=data.get("agent_type", ""),
                loaded=None,
                parsed=True,
                head="",
                born=False,
                merge_head=False,
                squash=False,
            )
            check_command(command, tool, ctx)
    except Blocked as blocked:
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
        print(f"git-guard: {blocked} Do not work around this; tell the user.", file=sys.stderr)
        sys.exit(2)
    except Exception as error:
        if "git" in raw:
            print(f"git-guard crashed: {error!r}. Tell the user.", file=sys.stderr)
            sys.exit(2)


if __name__ == "__main__":
    main()
