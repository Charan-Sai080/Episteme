# Karpathy LLM Wiki → Code Context & Impact Graph: Ideas Analysis

**Source**: Andrej Karpathy's LLM Wiki pattern (gist published April 2026) + NicholasSpisak/second-brain implementation  
**Purpose**: Extract ideas that apply to or improve the Code Context & Impact Graph project  
**Date**: 2026-08-24

---

## What Karpathy's Project Actually Does

The core idea: instead of doing RAG (fetch raw chunks at query time), maintain a **persistent, LLM-written, interlinked markdown wiki** that compounds over time.

```
raw/           ← immutable inbox (articles, papers, transcripts)
  └── assets/
wiki/           ← LLM-maintained, interlinked, growing
  ├── sources/     ← one summary per source
  ├── entities/    ← people, orgs, products, tools
  ├── concepts/    ← ideas, frameworks, theories
  ├── synthesis/   ← comparisons, themes, analyses
  ├── index.md     ← master catalog (one-liner per page)
  └── log.md       ← append-only audit trail
output/        ← generated reports and artifacts
```

**Three operations** drive the system:
1. **Ingest** — add a source → LLM reads it, updates 10–15 wiki pages, cross-links them, updates `index.md` + `log.md`
2. **Query** — ask a question → LLM reads `index.md` first to identify relevant pages, then synthesizes answer
3. **Lint** — periodic health check → finds orphan pages, broken links, contradictions, stale claims

**The philosophy**: LLM is the librarian. Human is the curator. Knowledge compounds and cross-links instead of sitting as disconnected raw text.

---

## The Core Difference (Critical — Don't Blur This)

| Dimension | Karpathy's Wiki | Code Context & Impact Graph |
|---|---|---|
| Raw input | Articles, papers, transcripts | Java source files |
| Extraction engine | **LLM** (judgment-based, lossy) | **Spoon/Tree-sitter** (deterministic, exact) |
| Why extraction model matters | Occasional drift is cheap for personal notes | Impact queries must be exactly correct — "what calls X" cannot be approximate |
| Structured store | Obsidian markdown vault | SQLite (symbols + edges + files tables) |
| Index | `index.md` (LLM-written summary) | Graph store (machine-queryable) |
| Human view | Wiki pages + wikilinks + Obsidian graph | `architecture.md` + Mermaid diagrams |
| Agent/machine view | The wiki pages themselves | Query API (`impact-index query <symbol>`) |
| Change tracking | `log.md` (append-only) | `files` manifest table + `last_indexed_commit` |

**Key principle**: Our project deliberately requires deterministic extraction because impact-analysis answers must be exactly right. Karpathy's LLM-judgment extraction is acceptable for personal notes where drift is cheap. We cannot afford that for a tool that tells an AI agent "this method has 3 callers" and then it edits all 3.

---

## Ideas Worth Borrowing (8 Direct Applications)

---

### Idea 1 — The Three-Operation Model (Ingest / Query / Lint)

**What Karpathy does**: Three clean, named verbs: `ingest`, `query`, `lint`. Everything the system does maps to one of these. This gives users a mental model and gives the CLI a clean interface.

**Direct mapping to our project**:

| Karpathy | Code Context & Impact Graph |
|---|---|
| `ingest` (add source → update wiki) | `impact-index index <repo>` (index/re-index repo → update graph) |
| `query` (ask question → consult wiki) | `impact-index query <symbol>` (query graph for callers/readers/writers) |
| `lint` (health check → find orphans/contradictions) | `impact-index lint` ← **this operation does not exist yet in our design** |

**What our `lint` command should do**:
- Find symbols in `symbols` table that have no edges (orphan symbols — likely parsing failures or dead code)
- Find edges that point to non-existent symbol IDs (dangling references — file was renamed, class deleted)
- Detect when `last_indexed_commit` is far behind `HEAD` (stale graph)
- Report files in the manifest with `status = 'stale'`
- Optionally: flag methods with cyclomatic complexity > threshold (code smell warning)

**Value**: This is a concrete addition to the CLI design. We have `index` and `query` but lint was missing.

---

### Idea 2 — index.md as the Fast-Path Entry Point

**What Karpathy does**: Before answering any query, the LLM reads `index.md` first — a compact master catalog with one-liner summaries of all wiki pages. This prevents the agent from needing to scan every file.

**Direct mapping**:  
Our equivalent is `impact-index query <symbol>` hitting the SQLite index. But there's a problem: when an AI agent has **no idea what symbols exist** (e.g., a new developer onboarding), they can't query specific names. They need a catalog first.

**Proposed addition — `impact-index catalog`**:
Generate a human-readable + machine-readable summary of the codebase structure:

```
# Repository Index — last indexed: commit abc1234 (2026-08-24)

## Packages (6)
- com.example.service      — 12 classes, 89 methods
- com.example.repository   — 5 classes, 34 methods
- com.example.model        — 18 classes, 0 methods (pure data)
- com.example.controller   — 8 classes, 44 methods
- com.example.util         — 3 classes, 22 methods
- com.example.config       — 2 classes, 6 methods

## Top Entry Points (most referenced classes)
- OrderService             — 47 inbound edges
- UserRepository           — 31 inbound edges
- PaymentGateway           — 28 inbound edges
```

This becomes the **context window anchor** for any AI agent entering the project cold. Instead of loading the whole repo, the agent reads the catalog, identifies the relevant package/class, then calls `query` on the specific symbol.

**This is the direct equivalent of Karpathy's `index.md` for code.**

---

### Idea 3 — log.md as Visible Audit Trail

**What Karpathy does**: Every operation appends to `log.md` with a timestamp and what changed. This solves the "when was this last updated?" question without needing to read every page.

**Direct mapping**:  
We have `last_indexed_commit` in the manifest table, but it's buried in SQLite. We should surface it visibly.

**Proposed addition — `docs/index-log.md`** (written by `impact-index index`):

```markdown
# Index Log

## 2026-08-24T10:30:00 — Full Index (commit abc1234)
- Files indexed: 312
- Symbols extracted: 4,821
- Edges extracted: 18,432
- Duration: 42s
- Warnings: 3 (see lint report)

## 2026-08-24T11:15:00 — Incremental Re-index (commit def5678)
- Files changed: 7
- Symbols added: 12, removed: 3, modified: 8
- Edges added: 34, removed: 11
- Duration: 2.1s
```

This gives both humans and agents an instant answer to "is this graph current?" without running a query.

---

### Idea 4 — The Ingest-First, Compile-Once Philosophy (Anti-RAG)

**What Karpathy does**: Instead of re-reading raw files every query, compile once into structured form and keep it current. The cost of compilation is paid upfront; queries are then cheap.

**Direct mapping**:  
This is the core of our Phase 1 (index once, query fast). The insight to borrow:

RAG over raw Java source files is what every coding agent does today (grep, read files, re-derive). Our tool makes the compilation step explicit and persistent.

**The way to pitch this tool**:
> "This is Karpathy's LLM Wiki pattern applied to codebases — but instead of LLM-judgment summarization, we use deterministic static analysis (Spoon) because code impact queries must be exact."

---

### Idea 5 — Orphan Detection During Lint

**What Karpathy does**: Lint scans for orphan pages (pages with no inbound or outbound links) and missing cross-links.

**Direct mapping**:  
In our graph, orphans and dangling references are correctness bugs, not just housekeeping:

- **Orphan symbol**: A class/method in `symbols` with no edges at all. Could mean:
  - Dead code (legitimately unused — worth surfacing)
  - Parsing failure (Spoon missed the references)
  - External API entry point (called via reflection, HTTP, etc.)
  
- **Dangling edge**: An edge in `edges` where `to_symbol` doesn't exist in `symbols`. Means the target was deleted or renamed but the edge wasn't cleaned up.

- **Missing class from library**: A method call resolves to `java.util.List#add` but `java.util.List` is not in our `symbols` table (because we only index source, not JDK). This should be documented, not silent.

**The lint command should classify these** and report them clearly:
```
ORPHANS (likely dead code or external entry points):
  - com.example.LegacyMigrationJob (0 inbound edges, 0 outbound edges)
  
DANGLING REFERENCES (stale edges — source was deleted or renamed):
  - com.example.OldPaymentService referenced in 3 edges but no longer in symbols

UNRESOLVED EXTERNAL CALLS (library methods not in index — expected):
  - java.util.List, java.util.Map, org.springframework.* (112 references)
  → These are expected external dependencies. See docs/known-limitations.md.
```

---

### Idea 6 — Source / Entity / Concept / Synthesis Taxonomy → Code Equivalent

**What Karpathy does**: Wiki pages are typed — `sources/`, `entities/`, `concepts/`, `synthesis/`.

**Direct mapping**:  

| Karpathy Wiki Type | Code Equivalent | Where Generated |
|---|---|---|
| `sources/` | Raw class files (immutable) | The Java source itself |
| `entities/` | Individual class/service documentation | Per-class doc in `docs/classes/` |
| `concepts/` | Module/package architecture docs | `docs/modules/` |
| `synthesis/` | Cross-cutting concerns, data flow summaries | `docs/architecture.md` |

**Proposed output structure**:
```
docs/
  ├── index.md              ← catalog (Idea 2)
  ├── index-log.md          ← audit trail (Idea 3)
  ├── architecture.md       ← system-level Mermaid diagrams (Phase 2)
  ├── modules/              ← per-package summaries
  │   ├── service.md
  │   ├── repository.md
  │   └── controller.md
  ├── classes/              ← per-class reference docs (auto-generated)
  │   ├── OrderService.md
  │   └── UserRepository.md
  └── known-limitations.md  ← unresolved externals, dynamic dispatch, etc.
```

---

### Idea 7 — Multi-Agent Compatibility by Design

**What Karpathy does**: The wiki schema is agent-agnostic. Multiple agents (Claude Code, Codex, Cursor, Gemini CLI) all follow the same rules. The system works with 40+ agents via the Agent Skills standard.

**Direct mapping**:  
This is exactly why we should build the MCP server, not just a CLI. Any agent that supports MCP can call `impact-index` without knowing the implementation.

**Additional idea**: Publish our tool to the **Agent Skills open standard** (agentskills.io). This makes our tool installable with:
```
npx skills add <your-org>/code-context-graph
```
And immediately usable from Claude Code, Cursor, Codex, Gemini CLI, and 40+ other agents without any custom integration.

---

### Idea 8 — Knowledge Compounding (The Most Important Idea)

**What Karpathy does**: Knowledge doesn't just accumulate — it **compounds**. When new information is ingested, it updates existing pages, adds new cross-links, and causes synthesis pages to be rewritten. The wiki becomes more connected and useful over time, not just bigger.

**Direct mapping**:  
Our graph already does this mechanically (Spoon re-parses → edges are updated). But we can borrow the *framing and output behavior*:

1. **On every incremental re-index, surface what changed** (not just "re-indexed 7 files"):
   ```
   Re-index complete (commit def5678):
   ✓ OrderService.processPayment — 2 new callers added (PaymentController, RefundJob)
   ✓ UserRepository.findByEmail — 1 caller removed (LegacyAuthService deleted)
   ✗ PaymentGateway — 3 dangling edges (class was renamed, run lint)
   ```

2. **The graph gets more accurate over time**, not just bigger. As new code is added, previously "orphan" symbols may get callers. The lint command should surface these improvements.

3. **The architecture.md should regenerate and diff itself** — "since last index, OrderService gained 2 new dependencies on ExternalPaymentAPI. Architecture diagram updated."

---

## What NOT to Borrow

### LLM-based extraction for the graph

Karpathy's system lets the LLM decide what's important, what to link, what to name concepts. For a personal wiki of articles, this is fine — occasional drift is cheap.

**For our impact analysis, this would be a correctness bug.** If an AI agent asks "what calls `processPayment()`" and the answer is wrong because an LLM summarizer missed a reference, the agent edits code with false confidence. We must keep Spoon's deterministic extraction for the `symbols` and `edges` tables.

**However**: the *doc generation layer* (Phase 2) CAN use LLM judgment — just like Karpathy does — for:
- Writing human-readable module summaries
- Generating the `synthesis/` layer (cross-cutting architecture observations)
- Writing `known-limitations.md` by reasoning about what we see in the graph

**The two-layer principle**: deterministic extraction for the graph (correctness-critical), LLM synthesis for the docs (lossy-by-design).

---

## Proposed Unified Architecture (Post-Analysis)

```
Java Repository (source files)
       │
       ▼
┌─────────────────────────────────────────┐
│  INDEXER (Deterministic Layer)          │
│  git ls-files → manifest table          │
│  Tree-sitter (file discovery)           │
│  Spoon/ECJ (semantic extraction)        │
│                                         │
│  Output: SQLite (symbols + edges +      │
│          files + quality_metrics)       │
└─────────────────┬───────────────────────┘
                  │
        ┌─────────┴──────────┐
        ▼                    ▼
┌──────────────┐    ┌────────────────────────────────────┐
│ QUERY API    │    │ DOC GENERATOR (LLM Synthesis Layer) │
│ (exact,      │    │                                     │
│  exhaustive) │    │ docs/                               │
│              │    │  ├── index.md      ← catalog        │
│ CLI + MCP    │    │  ├── index-log.md  ← audit trail    │
│ server       │    │  ├── architecture.md ← Mermaid      │
└──────────────┘    │  ├── modules/     ← pkg summaries   │
                    │  ├── classes/     ← class docs      │
                    │  └── known-limitations.md           │
                    └────────────────────────────────────┘

Five CLI Verbs (shaped by Karpathy):
  impact-index index    → run/re-run indexer
  impact-index query    → query the graph
  impact-index lint     → health check (orphans, dangling, staleness)
  impact-index catalog  → generate index.md for agent onboarding  ← NEW from Karpathy
  impact-index docs     → run doc generator (Phase 2)
```

---

## Summary: What to Formally Add to requirements.md

| Idea | Source | Priority | Phase |
|---|---|---|---|
| `lint` command (orphans, dangling edges, staleness) | Karpathy | High | Phase 1 |
| `catalog` command / `docs/index.md` generation | Karpathy | High | Phase 1 |
| `docs/index-log.md` audit trail | Karpathy | Medium | Phase 1 |
| Typed doc output structure (modules/, classes/, synthesis/) | Karpathy | Medium | Phase 2 |
| LLM synthesis layer for human docs (separate from deterministic graph) | Karpathy | Medium | Phase 2 |
| Agent Skills / MCP registry publication | Karpathy community | Low | Phase 3 |
| Delta reporting on re-index (what changed, not just "7 files updated") | Karpathy | Medium | Phase 3 |
