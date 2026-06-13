#!/usr/bin/env bash
# hook-version: 1.0.0
# git/pre-bash.sh
HOOK_VERSION="1.0.0"
#
# Claude Code PreToolUse hook — enforces git.md standards before Bash tool executes.
#
# Rules enforced:
#   1. Block commits directly to main/master       (hard block — exit 1)
#   2. Warn if new branch missing dev/ prefix      (soft warn — exit 0)
#   3. Block gh pr create if branch is behind main (hard block — exit 1)
#   4. Block gh pr merge without --squash          (hard block — exit 1)
#
# Note: force push, reset --hard, and rm -rf are blocked via settings.json
#       deny permissions — no hook needed for those.
#
# Input: JSON on stdin with shape { "tool_name": "Bash", "tool_input": { "command": "..." } }

set -uo pipefail

# --- Helpers ---
red()  { printf '\033[1;31m%s\033[0m\n' "$*" >&2; }
warn() { printf '\033[1;33m%s\033[0m\n' "$*" >&2; }
dim()  { printf '\033[2m%s\033[0m\n' "$*" >&2; }

# --- Parse input ---
INPUT=$(cat)
CMD=$(echo "$INPUT" | jq -r '.tool_input.command // ""')

# ── 1. Block commits directly to main ──────────────────────────────────────
if echo "$CMD" | grep -qE "^git commit"; then
  BRANCH=$(git branch --show-current 2>/dev/null || echo "")
  if [ "$BRANCH" = "main" ] || [ "$BRANCH" = "master" ]; then
    red "BLOCKED: You are on '$BRANCH'."
    red "Direct commits to main are not allowed per git.md standards."
    dim ""
    dim "Create a feature worktree first:"
    dim "  git worktree add ../worktrees/<feature-slug> -b dev/<feature-slug>"
    exit 1
  fi
fi

# ── 2. Warn if new branch doesn't use dev/ prefix ──────────────────────────
if echo "$CMD" | grep -qE "git (checkout|switch) -b "; then
  BRANCH=$(echo "$CMD" | grep -oP "(?<=-b )\S+" | head -1)
  if [ -n "$BRANCH" ] && ! echo "$BRANCH" | grep -q "^dev/"; then
    warn "WARNING: Branch '$BRANCH' does not follow the dev/<feature-slug> naming convention."
    warn "Rename to: dev/$BRANCH"
    dim "See git.md — Branch Naming section."
    # Soft warn: exit 0 so Claude sees the warning but can still proceed
  fi
fi

# ── 3. Block PR creation if branch is behind main ────────────────────────
if echo "$CMD" | grep -q "gh pr create"; then
  # Fetch latest main silently
  git fetch origin main --quiet 2>/dev/null || true
  BEHIND=$(git rev-list --count HEAD..origin/main 2>/dev/null || echo "0")
  if [ "$BEHIND" -gt 0 ]; then
    red "BLOCKED: Your branch is $BEHIND commit(s) behind origin/main."
    red "Merge main into your branch before creating a PR."
    dim ""
    dim "Run:"
    dim "  git fetch origin"
    dim "  git merge origin/main"
    dim "  # Resolve any conflicts, then push"
    exit 1
  fi
fi

# ── 4. Block PR merge without --squash ─────────────────────────────────────
if echo "$CMD" | grep -q "gh pr merge"; then
  if ! echo "$CMD" | grep -q -- "--squash"; then
    red "BLOCKED: PRs must be squash merged per git.md standards."
    red "Add --squash to your command."
    dim ""
    dim "Correct form:"
    dim "  gh pr merge <number> --squash --delete-branch"
    exit 1
  fi
fi

exit 0
