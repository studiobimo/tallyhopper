#!/usr/bin/env bash
# PreToolUse guard for AI coding agents (Claude Code: .claude/settings.json,
# Codex: .codex/hooks.json). Both pass the tool call as JSON on stdin and
# treat exit code 2 with a reason on stderr as "block this call".
#
# Enforces the same rules as the git hooks, before the agent acts:
#   - gh pr create / gh stack submit / git push  -> PR size limit
#   - git checkout -b / git switch -c / git branch <name>
#     / git branch -m|-c [<old>] <new>                   -> Conventional Branch
#
# CI (pr-checks) remains the authoritative gate; this is a guardrail.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

command -v jq >/dev/null 2>&1 || exit 0
cmd="$(jq -r '.tool_input.command // empty' 2>/dev/null || true)"
[[ -n "${cmd}" ]] || exit 0

block() {
    printf '%s\n' "$1" >&2
    exit 2
}

# Only the first line matters for matching; heredoc bodies can contain anything.
first_line="${cmd%%$'\n'*}"

# Help output never changes anything.
if [[ "${first_line}" =~ [[:space:]](--help|-h)([[:space:]]|$) ]]; then
    exit 0
fi

# --- PR size -------------------------------------------------------------
if [[ "${first_line}" =~ (^|[[:space:]\;\&\|])(gh[[:space:]]+pr[[:space:]]+create|gh[[:space:]]+stack[[:space:]]+submit|git[[:space:]]+push)([[:space:]]|$) ]]; then
    size_args=()
    if [[ "${first_line}" =~ gh[[:space:]]+pr[[:space:]]+create.*(--base|-B)[[:space:]=]+[\"\']?([^[:space:]\"\']+) ]]; then
        size_args=(--base "${BASH_REMATCH[2]}")
    fi
    if ! out="$("${here}/check-pr-size.sh" ${size_args[@]+"${size_args[@]}"} 2>&1)"; then
        block "Blocked by agent-guard (PR size rule):
${out}"
    fi
fi

# --- Branch names --------------------------------------------------------
new_branch=""
if [[ "${first_line}" =~ git[[:space:]]+(checkout|switch)[[:space:]]+(-b|-B|-c|-C|--create)[[:space:]]+[\"\']?([^[:space:];&|\"\']+) ]]; then
    new_branch="${BASH_REMATCH[3]}"
elif [[ "${first_line}" =~ git[[:space:]]+branch[[:space:]]+(-[mMcC]|--move|--copy)[[:space:]]+(.*)$ ]]; then
    # Rename/copy: `<new>` alone renames the current branch; with `<old> <new>`
    # the second name is the one being created.
    args="${BASH_REMATCH[2]%%[;&|<>]*}"
    args="${args% [0-9]}"
    names=()
    for b in ${args}; do
        b="${b//[\"\']/}"
        [[ -z "${b}" || "${b}" == -* ]] && continue
        names+=("${b}")
    done
    if ((${#names[@]} >= 2)); then
        new_branch="${names[1]}"
    elif ((${#names[@]} == 1)); then
        new_branch="${names[0]}"
    fi
elif [[ "${first_line}" =~ git[[:space:]]+branch[[:space:]]+[\"\']?([^-[:space:];&|\"\'][^[:space:];&|\"\']*)[\"\']?([[:space:]]|$) ]]; then
    new_branch="${BASH_REMATCH[1]}"
elif [[ "${first_line}" =~ gh[[:space:]]+stack[[:space:]]+(init|add)[[:space:]]+(.*)$ ]]; then
    # Stop at the next command separator or redirection.
    args="${BASH_REMATCH[2]%%[;&|<>]*}"
    args="${args% [0-9]}"
    for b in ${args}; do
        b="${b//[\"\']/}"
        [[ -z "${b}" || "${b}" == -* ]] && continue
        if ! out="$("${here}/check-branch-name.sh" "${b}" 2>&1)"; then
            block "Blocked by agent-guard (branch naming rule):
${out}"
        fi
    done
fi

if [[ -n "${new_branch}" ]]; then
    if ! out="$("${here}/check-branch-name.sh" "${new_branch}" 2>&1)"; then
        block "Blocked by agent-guard (branch naming rule):
${out}"
    fi
fi

exit 0
