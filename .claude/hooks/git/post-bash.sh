#!/usr/bin/env bash
# hook-version: 1.0.0
# git/post-bash.sh
HOOK_VERSION="1.0.0"
#
# Claude Code PostToolUse hook — enforces git.md standards after Bash tool executes.
#
# Rules enforced:
#   1. Remind to push after every commit           (soft remind — exit 0)
#   2. Remind to clean up branch after PR merge    (soft remind — exit 0)
#
# Input: JSON on stdin with shape:
#   { "tool_name": "Bash", "tool_input": { "command": "..." }, "tool_response": { "output": "..." } }

set -uo pipefail

# --- Helpers ---
info() { printf '\033[1;34m%s\033[0m\n' "$*" >&2; }
dim()  { printf '\033[2m%s\033[0m\n' "$*" >&2; }

# --- Parse input ---
INPUT=$(cat)
CMD=$(echo "$INPUT" | jq -r '.tool_input.command // ""')
OUTPUT=$(echo "$INPUT" | jq -r '.tool_response.output // ""')

# ── 1. Remind to push after every commit ───────────────────────────────────
if echo "$CMD" | grep -qE "^git commit"; then
  # Check the output confirms a commit was made (not a no-op / error)
  if echo "$OUTPUT" | grep -qE "\[dev/"; then
    BRANCH=$(git branch --show-current 2>/dev/null || echo "")
    info "REMINDER: Push your commit now."
    dim "  git push origin $BRANCH"
  fi
fi

# ── 2. Remind to clean up branch after merge ───────────────────────────────
if echo "$CMD" | grep -q "gh pr merge" && echo "$OUTPUT" | grep -qi "merged"; then
  BRANCH=$(git branch --show-current 2>/dev/null || echo "")
  info "REMINDER: Clean up the feature branch."
  dim "  git worktree remove ../worktrees/<feature-slug>"
  dim "  git branch -d $BRANCH"
  dim ""
  info "REMINDER: Invoke the trigger-blog skill if this feature is user-facing."
fi

exit 0
