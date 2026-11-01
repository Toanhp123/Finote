# Git workflow

Binding for every human and agent in this repo. `.claude/hooks/git_guard.py` enforces it for Claude Code, including subagents. If the hook blocks a command, stop and tell the user. Do not work around it.

## Branches

| Branch | From | Merges into | Merge type | Purpose |
|---|---|---|---|---|
| `main` | none | none | none | Released code. Every commit is tagged or a merge |
| `dev` | `main` | none | none | Daily integration. `release/*` branches from it |
| `feature/<name>` | `dev` | `dev` | `--squash` | New feature |
| `fix/<name>` | `dev` | `dev` | `--squash` | Bug not yet released |
| `chore/<name>` | `dev` | `dev` | `--squash` | Build, docs, refactor, tests |
| `release/x.y.z` | `dev` | `main`, then `dev` | `--no-ff` | Freeze a version |
| `hotfix/x.y.z` | `main` | `main`, then `dev` | `--no-ff` | Urgent fix to a released version |

- `<name>` is lowercase words joined by hyphens: `feature/parser-vcb`.
- No direct commits on `main` or `dev`.
- Never squash into `main`, and never squash `release/*` or `hotfix/*`. A squash gives `main` and `dev` different history, and later merges conflict.
- Keep one topic per branch.

## Daily flow

```bash
git switch dev
git switch -c feature/parser-vcb
# write code and tests, commit in small steps
./gradlew.bat testDebugUnitTest
# reviewer checks the whole branch diff
git switch dev
git merge --squash feature/parser-vcb
git commit -m "feat(parser): add VCB balance regex"
```

After a squash, `git branch -d` refuses because the commits are not reachable from `dev`. Confirm the squash commit exists, then the user deletes the branch with `git branch -D` in their own terminal.

## Release flow

```bash
git switch dev
git switch -c release/1.0.0
# bump versionName and versionCode in app/build.gradle.kts
git commit -m "build: bump version to 1.0.0"
./gradlew.bat testDebugUnitTest
git switch main
git merge --no-ff --no-edit release/1.0.0
git tag -a v1.0.0 -m "Release 1.0.0"
git switch dev
git merge --no-ff --no-edit release/1.0.0
```

A hotfix follows the same steps. It starts with `git switch -c hotfix/1.0.1 main` and merges into `main`, then `dev`.

- Version: `MAJOR.MINOR.PATCH`. Hotfix raises PATCH. `versionCode` always goes up.
- Tags: `vX.Y.Z`, on `main` only. Never move or delete a pushed tag.

## Commits

```text
<type>(<scope>): <subject>

<body: what changed and why, wrapped at 72 characters>
```

- Type: `feat`, `fix`, `refactor`, `test`, `docs`, `build`, `ci`, `chore`, `perf`.
- Scope, optional: `parser`, `dedup`, `pairing`, `balance`, `db`, `notif`, `ui`, `ota`, `build`. A new scope needs an edit here and in `git_guard.py`.
- Breaking change: add `!` before the colon and a `BREAKING CHANGE:` footer. Example: `feat(db)!: rename account table`.
- Subject: English, imperative, lowercase start, at most 72 characters, no final period.
- Body: optional for a small change. Explain why, not how.
- No `Co-Authored-By` trailer or any other AI attribution, for humans and agents alike.
- One commit does one thing. Each commit on `dev` must build.

## Before each commit

1. Stage files by name. Never `git add -A`, `git add .`, or `git commit -a`.
2. Read `git diff --cached`. Every line must belong to this commit.
3. After a logic change (parser, dedup, pairing, balance), run `./gradlew.bat testDebugUnitTest` and read the output.
4. Check privacy. See below.

## Privacy and secrets

Never commit:

- Real bank notifications, balances, or account numbers. This includes test fixtures: replace amounts, names, and account digits with made-up values. The hook cannot detect this, so `reviewer` checks it.
- `local.properties`, keystores (`*.jks`, `*.keystore`), `google-services.json`, API keys.

If a secret reaches a commit, stop and tell the user. Do not rewrite history alone.

## Agent rules

- `coder` and `reviewer` never run `commit`, `merge`, `push`, `tag`, or `rebase`.
- The main session commits only when the user asks.
- `reviewer` runs once per branch, on the whole diff, before the squash into `dev`. Not on each commit.
- Merge, tag, and push always ask the user first.
- Only the user pushes `main`.
- An approval covers one action. Approval to commit is not approval to push.

## Blocked commands

| Group | Commands |
|---|---|
| Rewrite history | `push --force`, `push --force-with-lease`, `reset --hard`, `rebase -i`, rebase of `main` or `dev`, `filter-branch`, `filter-repo`, `reflog expire` |
| Lose work | `clean -f`, `checkout -- <path>`, `checkout .`, `restore <path>`, `branch -D`, `stash drop`, `stash clear`, `tag -d` |
| Skip checks | `--no-verify`, `commit -n`, `--no-gpg-sign`, `-c core.hooksPath=` |
| Careless staging | `add -A`, `add .`, `add -u`, `add -f`, `commit -a` |
| Wrong place | commit on `main` or `dev`, push of `main`, a merge that breaks the branch table, a bad branch, tag, or commit name |
| Attribution | `Co-Authored-By` in a commit or merge message |

The user may run any of these in their own terminal. The hook only binds Claude Code.

## Setup

The first commit goes on `main`. The hook allows this once, while `main` has no commits. Then:

```bash
git branch dev
```

When a remote is added, open pull requests instead of merging locally, and turn on branch protection for `main` and `dev`.

## Maintaining the hook

Change this file and `git_guard.py` together, then run:

```bash
python .claude/hooks/test_git_guard.py
```
