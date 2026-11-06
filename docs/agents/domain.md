# Domain Docs

How the engineering skills should consume this repo's domain documentation when exploring the codebase.

**Layout: single-context.** This repo (`nongyu-app-plus`, a Kotlin + Jetpack Compose Android app with a single
`:app` Gradle module) keeps one glossary shared by the whole repo, at the root. There are no per-module
glossaries and no `GLOSSARY-MAP.md`.

## Before exploring, read these

- **`GLOSSARY.md`** at the repo root — the single glossary for this repo.
- **`docs/adr/`**: read ADRs that touch the area you're about to work in. Architecture decisions for this app
  live in `docs/adr/`.

If any of these files don't exist, **proceed silently**. Don't flag their absence; don't suggest creating them upfront. The `/domain-modeling` skill (reached via `/grill-with-docs` and `/improve-codebase-architecture`) creates them lazily when terms or decisions actually get resolved.

## File structure

Single-context repo (this repo):

```
/
├── GLOSSARY.md                        ← one glossary for the whole repo
├── docs/
│   ├── adr/                           ← architecture decisions
│   └── agents/                        ← skill configuration (this file, issue tracker, triage labels)
├── app/                               ← the only Gradle module
└── src/
```

`GLOSSARY.md` and `docs/adr/` are created on first real use, not upfront. `docs/adr/` may exist as an empty
directory before the first ADR is written.

## Use the glossary's vocabulary

When your output names a domain concept (in an issue title, a refactor proposal, a hypothesis, a test name), use the term as defined in `GLOSSARY.md`. Don't drift to synonyms the glossary explicitly avoids.

If the concept you need isn't in the glossary yet, that's a signal: either you're inventing language the project doesn't use (reconsider) or there's a real gap (note it for `/domain-modeling`).

## Flag ADR conflicts

If your output contradicts an existing ADR, surface it explicitly rather than silently overriding:

> _Contradicts ADR-0007 (event-sourced orders), but worth reopening because…_
