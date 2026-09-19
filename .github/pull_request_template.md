<!-- markdownlint-disable-file MD041 -->
## What & why

<!-- One or two sentences. Link issues with "Closes #123". -->

## Checklist

- [ ] PR title is a [Conventional Commit](https://www.conventionalcommits.org/en/v1.0.0/) (it becomes the squash commit)
- [ ] ≤20 files changed (otherwise split with `gh stack`)
- [ ] `make -C .devtools check` passes locally
- [ ] Tests added or updated (JUnit for pure logic, GameTest for in-game behavior)
- [ ] Behavior stays additive: no existing items are removed, replaced or extracted
- [ ] Docs, roadmap or ADR updated if behavior or decisions changed
