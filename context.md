# Session Context — Code Context & Impact Graph

Purpose: resumability record of design discussion so far. This is a session log, not a spec — `requirements.md` remains the source of truth for scope; this file captures reasoning/decisions still pending formalization.

Last updated: 2026-08-05

---

## 1. Where things stand

`requirements.md` is read and understood (Draft, finalized 2026-08-05). Summary of that doc:

- **Problem**: knowledge transfer is slow for humans; coding agents re-derive repo context on every task with no persistent structure.
- **Architecture**: one shared graph (Tree-sitter → SQLite `symbols`/`edges` tables), two renderers — a Query API for agents (exhaustive, exact) and a Doc Generator for humans (`architecture.md` + Mermaid, curated/lossy by design).
- **Explicit non-goals**: no CFG/DFG, no CPG/Joern-style analysis, no runtime tracking, single language/project on day one.
- **Phased roadmap**: Phase 1 Symbol Impact Index → Phase 2 Human Docs → Phase 3 Incremental Updates (git-diff-based) → Phase 4 (deferred) typed dataflow.
- **Open questions (Section 10, still unresolved as of last read)**:
  1. Which repo/language is the first Phase 1 target?
  2. MCP server (any agent) vs. project-local CLI only?
  3. How is the SQLite graph store handled in version control — checked-in, gitignored + rebuild-on-clone, or CI-generated?

## 2. Discussion: tree → crawler indexing proposal

User proposed: CLI tool first runs `tree` (with proper depth) to produce a file checklist, then a crawler walks the repo against that checklist, parsing each file and updating the graph.

**Assessment given**: the two-phase shape (discovery, then extraction) is correct and valuable — it decouples enumeration from parsing, enabling resumability, parallel parsing, and a natural hook for Phase 3 incremental re-indexing. However, using the `tree` *command* itself as the manifest source was flagged as the wrong tool:

- `tree`'s output is ASCII art for terminal display, not a machine-parseable list — would require regex-parsing box-drawing characters.
- Doesn't respect `.gitignore` by default (needs manual `-I` exclude patterns, inconsistent across OS/tree versions).

**Recommended replacement**:
- Use `git ls-files` (tracked files, respects `.gitignore`) plus optionally `git ls-files --others --exclude-standard` (untracked-but-not-ignored) to build the file list. Deterministic, zero-parsing, already how git scopes the repo.
- Filter by extension/language for the target Tree-sitter grammar.
- Persist as a real **manifest table** in SQLite (`files`: path, content_hash, last_indexed_commit, status) rather than an ephemeral in-memory checklist.
- This manifest table doubles as the answer to **Risk #2 in requirements.md** (graph staleness / "last indexed at commit X" marker), and is what Phase 3's incremental re-index would diff against via `git diff --name-only` since the last indexed commit.

This has **not yet been written into requirements.md** — pending user decision on whether to formalize it as the resolution to open questions 1–3 (partially) before moving into actual architecture design.

## 3. Discussion: relation to Andrej Karpathy's "LLM Wiki" / second-brain (Obsidian) project

Researched via web search (project published as a GitHub gist by Karpathy, ~April 2026; pattern replicated in community repos e.g. `NicholasSpisak/second-brain`).

**How it works**: raw material (articles, papers, transcripts) dropped into a `raw/` folder → an LLM reads it, writes structured wiki pages, cross-references them, maintains an index → user browses the result in Obsidian via `[[wikilinks]]` and graph view. Output structure: `wiki/sources/`, `wiki/entities/`, `wiki/concepts/`, `wiky/synthesis/`, `index.md` (master catalog), `log.md` (chronological record). Framed as "the LLM is the librarian, you're the curator."

**Comparison drawn**:

| | Code Context Graph (this project) | Karpathy's LLM Wiki |
|---|---|---|
| Raw input | Source files in repo | Raw articles/papers/transcripts |
| Extraction engine | Tree-sitter (deterministic parse) | LLM (judgment-based summarization) |
| Structured store | SQLite `symbols`/`edges` | Obsidian vault (`sources/`, `entities/`, `concepts/`, `synthesis/`) |
| Master index | Graph store, queryable | `index.md` |
| Change log | "last indexed at commit X" marker | `log.md` |
| Human view | `architecture.md` + Mermaid (curated, lossy by design) | Wiki pages + wikilinks + graph view |
| Agent/machine view | Query API, exhaustive, exact | The wiki pages themselves |

**Key conclusion**: same overall shape (raw → structured store → curated human view + graph view), but the extraction trust model differs on purpose. This project deliberately requires deterministic, exhaustive extraction (Tree-sitter AST) because impact-analysis answers ("what calls X") must be exactly correct — matches the non-goals in requirements.md Section 3, which explicitly rules out lossy/ambiguous extraction for the graph layer. Karpathy's LLM-judgment extraction is acceptable for personal notes where occasional drift is cheap, but would be a correctness bug here.

**One idea worth borrowing**: the `index.md` + `log.md` pattern is a clean, minimal precedent for a visible staleness marker — directly reinforces the manifest-table recommendation in Section 2 above.

## 4. Open threads / next decision points

- [ ] Decide whether to fold the manifest-table (git ls-files–based discovery + staleness marker) into `requirements.md` as the resolution to Section 10's open questions 1 and 3.
- [ ] Section 10 open question 2 (MCP server vs. project-local CLI) still fully open — not discussed yet.
- [ ] No implementation has started. Per CLAUDE.md, planning/architecture design should precede implementation.
