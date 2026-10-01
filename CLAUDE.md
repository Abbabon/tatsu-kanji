# CLAUDE.md

## Git Commit Authorship

**All commits in this repository must be authored and committed by the `Abbabon` GitHub user.**

- Name: `Abbabon`
- Email: `1280330+Abbabon@users.noreply.github.com`

This identity is set in the repo-local git config (`.git/config`), so normal `git commit`
calls pick it up automatically. Do **not** override it with `--author`, `-c user.name=...`,
or `-c user.email=...`.

Before committing, verify the identity is still correct:

```bash
git config user.name    # -> Abbabon
git config user.email   # -> 1280330+Abbabon@users.noreply.github.com
```

If it is not, restore it with:

```bash
git config --local user.name "Abbabon"
git config --local user.email "1280330+Abbabon@users.noreply.github.com"
```

Pushes and PRs go through `gh`, whose active account is machine-wide. Check it before
pushing:

```bash
gh auth status                                    # -> Abbabon active
gh auth switch --hostname github.com --user Abbabon
```

If any commit ever lands with a different author or committer, rewrite history so that
`Abbabon` is the only author/committer:

```bash
FILTER_BRANCH_SQUELCH_WARNING=1 git filter-branch -f --env-filter '
export GIT_AUTHOR_NAME="Abbabon"
export GIT_AUTHOR_EMAIL="1280330+Abbabon@users.noreply.github.com"
export GIT_COMMITTER_NAME="Abbabon"
export GIT_COMMITTER_EMAIL="1280330+Abbabon@users.noreply.github.com"
' --tag-name-filter cat -- --all
```

Do not add co-author trailers (`Co-Authored-By:`) or any other trailer that attributes the
commit to a different person or tool.

## Running tests

Always run the tests on the iOS Simulator, never the macOS destination (it launches the app and takes over the machine):

```bash
xcodebuild -project Tatsu.xcodeproj -scheme Tatsu -destination 'platform=iOS Simulator,name=iPhone 16 Pro' CODE_SIGNING_ALLOWED=NO test
```

If the simulator can't be found by name, use `id=<UDID>` from `xcrun simctl list devices available`.

Never drive the Mac UI with osascript/System Events keystrokes or clicks without asking the user first. Mac-only UI behaviour goes into a manual checklist for the user.
