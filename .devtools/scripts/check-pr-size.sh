#!/usr/bin/env bash
# Fails when the current branch changes more files than a PR may contain.
#
# Usage: check-pr-size.sh [--base <branch>] [--max <n>]
#
# Base resolution: --base, then $PR_BASE, then this branch's parent in a
# gh stack, then the remote default branch. Each stack layer is therefore
# measured against the layer below it.
#
# Interim local copy; canonical version will live in studiobimo/.github
# (pre-commit hook `pr-size`, see studiobimo/.github#10).
set -euo pipefail

max="${PR_MAX_FILES:-20}"
base="${PR_BASE:-}"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --base) base="$2"; shift 2 ;;
        --max) max="$2"; shift 2 ;;
        *) echo "unknown argument: $1" >&2; exit 64 ;;
    esac
done

branch="$(git symbolic-ref --quiet --short HEAD 2>/dev/null || true)"

stack_parent() {
    command -v gh >/dev/null 2>&1 || return 0
    # `gh stack view --json` lists branches bottom-to-top; a layer's base is the one below it.
    gh stack view --json 2>/dev/null | jq -r --arg b "${branch}" '
        ([.branches[].name] | index($b)) as $i
        | if $i == null then empty
        elif $i == 0 then .trunk
        else .branches[$i - 1].name end' 2>/dev/null || true
}

default_branch() {
    git symbolic-ref --quiet --short refs/remotes/origin/HEAD 2>/dev/null | sed 's#^origin/##' || echo main
}

[[ -n "${base}" ]] || base="$(stack_parent)"
[[ -n "${base}" ]] || base="$(default_branch)"
[[ -n "${base}" ]] || base="main"

# Prefer the local ref (stack parents are local branches), fall back to origin.
if git rev-parse --verify --quiet "${base}" >/dev/null; then
    base_ref="${base}"
elif git rev-parse --verify --quiet "origin/${base}" >/dev/null; then
    base_ref="origin/${base}"
else
    echo "⚠ Base '${base}' not found; skipping PR size check." >&2
    exit 0
fi

merge_base="$(git merge-base "${base_ref}" HEAD)"
count="$(git diff --name-only "${merge_base}" HEAD | wc -l | tr -d ' ')"

if (( count <= max )); then
    echo "✔ ${count}/${max} files changed vs ${base_ref}"
    exit 0
fi

cat >&2 <<MSG
✖ This branch changes ${count} files vs ${base_ref} (max ${max}).

    Split it into a stack of smaller PRs:
    gh extension install github/gh-stack   # once
    gh stack init <first-branch>           # or adopt existing branches
    gh stack add <next-branch>             # commit a slice, repeat
    gh stack submit                        # push and open linked PRs

    Docs: https://docs.github.com/en/pull-requests/get-started/about-stacked-prs
MSG
exit 1
