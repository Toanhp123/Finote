"""Run with: python .claude/hooks/test_git_guard.py"""
import json
import os
import subprocess
import sys
import tempfile
import unittest

GUARD = os.path.join(os.path.dirname(os.path.abspath(__file__)), "git_guard.py")


def run_git(cwd, *args):
    subprocess.run(
        ["git", "-c", "user.name=t", "-c", "user.email=t@t", "-C", cwd, *args],
        check=True,
        capture_output=True,
    )


def guard(command, cwd, tool="Bash", agent=""):
    payload = {"tool_name": tool, "tool_input": {"command": command}, "cwd": cwd}
    if agent:
        payload["agent_type"] = agent
    return subprocess.run(
        [sys.executable, GUARD], input=json.dumps(payload).encode(), capture_output=True
    ).returncode


class Base(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(ignore_cleanup_errors=True)
        self.addCleanup(self.tmp.cleanup)
        self.repo = self.tmp.name
        run_git(self.repo, "init", "-b", "main")

    def seed(self):
        run_git(self.repo, "commit", "--allow-empty", "-m", "chore: initial commit")
        run_git(self.repo, "branch", "dev")

    def checkout(self, branch):
        run_git(self.repo, "checkout", branch)

    def blocked(self, command, **kwargs):
        self.assertEqual(guard(command, self.repo, **kwargs), 2, command)

    def allowed(self, command, **kwargs):
        self.assertEqual(guard(command, self.repo, **kwargs), 0, command)


class DangerousCommands(Base):
    def test_blocked_anywhere(self):
        self.seed()
        self.checkout("dev")
        run_git(self.repo, "checkout", "-b", "feature/parser-vcb")
        for command in [
            "git push --force",
            "git push -f origin feature/parser-vcb",
            "git push -uf origin feature/parser-vcb",
            "git push --force-with-lease",
            "git push origin +feature/parser-vcb",
            "git push origin :dev",
            "git push origin main",
            "git reset --hard",
            "git reset --hard HEAD~1",
            "git rebase -i dev",
            "git filter-branch --all",
            "git clean -fd",
            "git checkout -- app/build.gradle.kts",
            "git checkout .",
            "git restore app/build.gradle.kts",
            "git branch -D feature/old",
            "git branch -d main",
            "git commit --no-verify -m 'feat: add x'",
            "git commit -n -m 'feat: add x'",
            "git merge --no-gpg-sign dev",
            "git -c core.hooksPath=/dev/null commit -m 'feat: add x'",
            "git add -A",
            "git add .",
            "git add --all",
            "git add -f local.properties",
            "git commit -am 'feat: add x'",
            "git stash clear",
            "git tag -d v1.0.0",
            "git config core.hooksPath /dev/null",
            "git config --local core.hooksPath hooks",
            "git config --global core.hooksPath .git/hooks",
            "git config --replace-all core.hooksPath x",
            "git config core.hookspath ''",
            "git config --unset core.hooksPath",
            "git config --local --unset-all core.hooksPath",
            "git config unset core.hooksPath",
            "git config unset --all core.hooksPath",
            "git config --local unset core.hooksPath",
            "git config set core.hooksPath x",
            "git config set --local core.hooksPath /dev/null",
            "git config set core.hooksPath",
            "git config edit",
            "git config edit --global",
            "git config -e",
            "git config --edit",
        ]:
            self.blocked(command)

    def test_hidden_inside_compound_or_wrappers(self):
        self.seed()
        for command in [
            "git status && git reset --hard",
            "git status; git reset --hard",
            "git status\ngit reset --hard",
            "cd . && git reset --hard",
            "git -C . reset --hard",
            "/usr/bin/git reset --hard",
            "git.exe reset --hard",
            "FOO=1 git reset --hard",
            "timeout 30 git reset --hard",
            "sh -c 'git reset --hard'",
            'bash -c "git status && git reset --hard"',
            "for b in x; do git reset --hard; done",
        ]:
            self.blocked(command)

    def test_powershell(self):
        self.seed()
        self.blocked("git status; git reset --hard", tool="PowerShell")
        self.blocked("git -C D:\\nope reset --hard", tool="PowerShell")

    def test_powershell_script_blocks(self):
        self.seed()
        self.checkout("dev")
        self.allowed("git status; if ($?) { git switch -c chore/x }", tool="PowerShell")
        self.blocked("if ($?) { git switch -c chore/x main }", tool="PowerShell")
        self.blocked("if ($?) { git status } else { git push --force }", tool="PowerShell")

    def test_text_in_message_is_not_a_command(self):
        self.seed()
        self.checkout("dev")
        run_git(self.repo, "checkout", "-b", "chore/notes")
        self.allowed("git commit -m 'docs: explain git reset --hard; never run it'")

    def test_safe_commands_pass(self):
        self.seed()
        self.checkout("dev")
        run_git(self.repo, "checkout", "-b", "feature/parser-vcb")
        for command in [
            "git status",
            "git log --oneline -5",
            "git diff --cached",
            "git add app/build.gradle.kts docs/git-workflow.md",
            "git restore --staged app/build.gradle.kts",
            "git push -u origin feature/parser-vcb",
            "git branch --show-current",
            "git branch -a",
            "git branch -d feature/old",
            "git tag",
            "git clean -n",
            "git config core.hooksPath .githooks",
            "git config --local core.hooksPath .githooks",
            "git config core.hooksPath",
            "git config --get core.hooksPath",
            "git config set core.hooksPath .githooks",
            "git config set --local core.hooksPath .githooks",
            "git config get core.hooksPath",
            "git config list",
            "git config set user.name tester",
            "git config unset user.name",
            "git config user.name tester",
            "git config --unset user.name",
            "git stash",
            "gradlew.bat assembleDebug",
            "echo digit",
        ]:
            self.allowed(command)

    def test_non_shell_tool_ignored(self):
        payload = {"tool_name": "Read", "tool_input": {"file_path": "git"}, "cwd": self.repo}
        done = subprocess.run(
            [sys.executable, GUARD], input=json.dumps(payload).encode(), capture_output=True
        )
        self.assertEqual(done.returncode, 0)


class Branches(Base):
    def test_commit_blocked_on_protected(self):
        self.seed()
        self.blocked("git commit -m 'feat: add x'")
        self.blocked("git push")
        self.checkout("dev")
        self.blocked("git commit -m 'feat: add x'")

    def test_first_commit_allowed_on_main(self):
        self.allowed("git commit -m 'chore: initial commit'")
        self.allowed("git branch dev")

    def test_names_and_bases(self):
        self.seed()
        self.checkout("dev")
        for command in [
            "git checkout -b feature/parser-vcb",
            "git switch -c fix/dedup-window",
            "git switch -c chore/ci",
            "git branch release/1.0.0",
            "git checkout -b hotfix/1.0.1 main",
        ]:
            self.allowed(command)
        for command in [
            "git checkout -b Feature/Parser",
            "git checkout -b feature/parser_vcb",
            "git checkout -b wip",
            "git checkout -b release/1.0",
            "git checkout -B feature/x",
            "git switch -C feature/x",
            "git checkout -b hotfix/1.0.1",
            "git checkout -b feature/x main",
            "git worktree add -b temp ../x dev",
        ]:
            self.blocked(command)

    def test_tracks_branch_inside_compound(self):
        self.seed()
        self.checkout("dev")
        run_git(self.repo, "checkout", "-b", "feature/x")
        self.blocked("git checkout dev && git commit -m 'feat: add x'")
        self.allowed("git checkout dev && git merge --squash feature/x && git commit -m 'feat: add x'")
        self.blocked("git checkout main && git push")

    def test_tag_rules(self):
        self.seed()
        self.allowed("git tag -a v1.0.0 -m 'Release 1.0.0'")
        self.blocked("git tag 1.0.0")
        self.checkout("dev")
        self.blocked("git tag v1.0.0")


class Merges(Base):
    def test_merge_targets(self):
        self.seed()
        self.checkout("dev")
        self.allowed("git merge --squash feature/x")
        self.allowed("git merge --no-ff release/1.0.0")
        self.allowed("git merge --no-ff hotfix/1.0.1")
        self.allowed("git merge --ff-only origin/dev")
        self.blocked("git merge feature/x")
        self.blocked("git merge --no-ff feature/x")
        self.blocked("git merge --squash release/1.0.0")
        self.blocked("git merge main")
        self.checkout("main")
        self.allowed("git merge --no-ff release/1.0.0")
        self.allowed("git merge --no-ff --no-edit hotfix/1.0.1")
        self.blocked("git merge dev")
        self.blocked("git merge --no-ff dev")
        self.blocked("git merge --squash release/1.0.0")
        self.blocked("git merge release/1.0.0")

    def test_squash_commit_on_dev_allowed_only_after_squash(self):
        self.seed()
        self.checkout("dev")
        run_git(self.repo, "checkout", "-b", "feature/x")
        run_git(self.repo, "commit", "--allow-empty", "-m", "wip")
        with open(os.path.join(self.repo, "a.txt"), "w") as f:
            f.write("x")
        run_git(self.repo, "add", "a.txt")
        run_git(self.repo, "commit", "-m", "feat: add a")
        self.checkout("dev")
        self.blocked("git commit -m 'feat: add a'")
        run_git(self.repo, "merge", "--squash", "feature/x")
        self.allowed("git commit -m 'feat: add a'")
        self.blocked("git commit -m 'bad message'")


class Subjects(Base):
    def setUp(self):
        super().setUp()
        self.seed()
        self.checkout("dev")
        run_git(self.repo, "checkout", "-b", "feature/x")

    def test_valid(self):
        for subject in [
            "feat(parser): add VCB balance regex",
            "fix(balance): keep snapshot in one transaction",
            "docs: add git workflow",
            "feat(db)!: rename table",
            "ci: run unit tests",
        ]:
            self.allowed(f"git commit -m '{subject}'")

    def test_invalid(self):
        for subject in [
            "add parser",
            "Feat: add parser",
            "feat: Add parser",
            "feat(parser) add regex",
            "feat(wallet): add regex",
            "feat: add parser.",
            "update: add parser",
            "feat: " + "x" * 70,
        ]:
            self.blocked(f"git commit -m '{subject}'")

    def test_heredoc_message(self):
        good = "git commit -m \"$(cat <<'EOF'\nfeat(parser): add regex\n\nBody line.\nEOF\n)\""
        bad = "git commit -m \"$(cat <<'EOF'\nadd regex\n\nBody line.\nEOF\n)\""
        self.allowed(good)
        self.blocked(bad)

    def test_powershell_here_string(self):
        self.allowed("git commit -m @'\nfeat: add regex\n\nBody.\n'@", tool="PowerShell")
        self.blocked("git commit -m @'\nadd regex\n'@", tool="PowerShell")

    def test_message_file(self):
        with open(os.path.join(self.repo, "msg.txt"), "w") as f:
            f.write("add regex\n")
        self.blocked("git commit -F msg.txt")
        with open(os.path.join(self.repo, "msg.txt"), "w") as f:
            f.write("feat: add regex\n")
        self.allowed("git commit -F msg.txt")

    def test_coauthor_trailer_blocked(self):
        for command in [
            "git commit -m 'feat: add x' -m 'Co-Authored-By: Claude <noreply@anthropic.com>'",
            "git commit -m 'feat: add x' --trailer 'co-authored-by: Claude <c@a.com>'",
            "git commit -m 'feat: add x' --trailer='Co-Authored-By: Claude <c@a.com>'",
            "git commit -m \"$(cat <<'EOF'\nfeat: add x\n\nCo-Authored-By: Claude <c@a.com>\nEOF\n)\"",
            "git merge --no-ff -m 'Co-Authored-By: Claude <c@a.com>' fix/x",
        ]:
            self.blocked(command)
        self.blocked(
            "git commit -m @'\nfeat: add x\n\nCo-Authored-By: Claude <c@a.com>\n'@", tool="PowerShell"
        )
        with open(os.path.join(self.repo, "msg.txt"), "w") as f:
            f.write("feat: add x\n\nCo-Authored-By: Claude <c@a.com>\n")
        self.blocked("git commit -F msg.txt")
        self.allowed("git commit -m 'docs: ban co-authored-by trailers'")

    def test_editor_commit_blocked(self):
        self.blocked("git commit")
        self.allowed("git commit --amend --no-edit")


class Subagents(Base):
    def test_coder_cannot_commit_or_merge(self):
        self.seed()
        self.checkout("dev")
        run_git(self.repo, "checkout", "-b", "feature/x")
        for command in ["git commit -m 'feat: add x'", "git merge --squash fix/y", "git push"]:
            self.blocked(command, agent="coder")
            self.blocked(command, agent="reviewer")
        self.allowed("git add a.txt", agent="coder")
        self.allowed("git diff", agent="reviewer")
        self.allowed("git commit -m 'feat: add x'", agent="")


if __name__ == "__main__":
    unittest.main()
