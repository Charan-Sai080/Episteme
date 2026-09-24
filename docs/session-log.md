# Session Log — Code Context & Impact Graph

This file is an append-only chronological record of all design discussions, decisions, and open questions.  
It is the **resumability record** for this project — read this first when picking up where we left off.

`requirements.md` and `context.md` remain the source of truth for scope.  
`docs/` contains all detailed discussion documents.

---

## Session 1 — 2026-08-24

### Participants
- Charan (owner/architect)
- Antigravity AI (design assistant)

### What Was Discussed

#### 1. Engineering Constitution Loaded
- File: `/Users/charancherry/dev/ccWorkSpace/.claude/CLAUDE.md`
- Key principle relevant to this project: **plan before implementing**, production-quality software, minimize technical debt.

#### 2. Project Context Reviewed
- `requirements.md` — read and understood in full (Draft, finalized 2026-08-05)
- `context.md` — session context from prior Claude Code session reviewed

**Summary of prior state**:
- Problem: coding agents re-derive context on every task; knowledge transfer is slow for humans
- Architecture: one shared graph (Tree-sitter → SQLite), two renderers — Query API (agents) + Doc Generator (humans)
- Phased roadmap: Phase 1 Symbol Index → Phase 2 Human Docs → Phase 3 Incremental Updates → Phase 4 Typed Dataflow
- Open questions from prior session (Section 10 of requirements.md): target language, MCP vs CLI, SQLite versioning

#### 3. Target Language Decision
- **Resolved: Java** (and its libraries)
- This affects: Tree-sitter grammar choice, semantic analysis engine, build tooling

#### 4. Design Discussion — Analysis Engine (Open Question 1)
Detailed discussion of how IntelliJ and SonarQube do reference resolution.

**How IntelliJ does it**:
- PSI (Program Structure Interface) — richer than AST
- Stub index (lightweight structural info for fast lookup across files)
- Scope-chain walking + type resolution via Java compiler model
- Result: exact file:line resolution across generics, interfaces, inheritance

**How SonarQube does it**:
- Eclipse Compiler for Java (ECJ) → AST + semantic model
- Rule traversal across the semantic AST
- Not regex — proper type-aware analysis
- Also reads JaCoCo/Surefire XML for test/coverage integration

**Options evaluated**:
| Option | Accuracy | Notes |
|---|---|---|
| Tree-sitter only | ~60% | Syntactic, no type resolution |
| JavaParser + JavaSymbolSolver | ~80% | Lightweight, Apache 2.0 |
| Spoon (ECJ-backed) | ~95% | Compiler-grade, same stack as SonarQube |
| JDT.LS (Language Server) | ~99% | Heavy, best for Phase 4 |
| **Hybrid: Tree-sitter + Spoon** | ~95% | **Recommended** — tree-sitter for file manifest, Spoon for semantic extraction |

**Status: Recommendation made, decision pending confirmation from Charan**

#### 5. Design Discussion — Agent Integration (Open Question 2)
**Options evaluated**: CLI only, MCP only, Hybrid CLI + MCP

**Recommendation: Hybrid CLI + MCP**
- Build all commands as CLI first
- Wrap as MCP server so any AI-enabled IDE can discover and call tools without hardcoded commands
- Aligns with end goal: "integrable into any AI-enabled IDE, a tool in the orchestration layer"

**Status: Recommendation made, decision pending (stdio vs network MCP server)**

#### 6. Design Discussion — SQLite Store Versioning (Open Question 3)
**Options evaluated**: Checked-in, gitignored+rebuild, CI-generated, gitignored+manifest-incremental

**Recommendation: Option D — gitignore + manifest table**
- `files` manifest table in SQLite: `path`, `content_hash`, `last_indexed_commit`, `status`
- Re-index uses `git diff --name-only <last_commit>..HEAD` — only re-parses changed files
- Solves staleness risk (Risk #2 in requirements.md) directly

**Status: Recommendation made, pending `--force-reindex` flag decision**

#### 7. Design Discussion — SonarQube-Style Quality Integration (Open Question 4)
**Proposed quality metrics schema**:
```sql
CREATE TABLE quality_metrics (
  symbol_id   INTEGER REFERENCES symbols(id),
  metric_name TEXT,   -- 'cyclomatic_complexity', 'test_coverage', 'code_smells'
  value       REAL,
  source      TEXT    -- 'spoon', 'jacoco', 'surefire'
);
```

**Recommendation**: Complexity (from Spoon, free) in Phase 1; coverage + test results in Phase 2.

**Status: Pending decision**

#### 8. Karpathy LLM Wiki Analysis
Full analysis of Andrej Karpathy's second-brain / LLM Wiki pattern (April 2026 gist).

**8 ideas borrowed**:

| # | Idea | Impact | Phase |
|---|---|---|---|
| 1 | Three-operation model (ingest/query/lint) → adds `lint` command | High | Phase 1 |
| 2 | `index.md` catalog → adds `impact-index catalog` command | High | Phase 1 |
| 3 | `log.md` audit trail → adds `docs/index-log.md` | Medium | Phase 1 |
| 4 | Compile-once anti-RAG philosophy | Validates whole project premise | — |
| 5 | Orphan detection in lint → classifies dead code / dangling refs / external calls | High | Phase 1 |
| 6 | Source/entity/concept/synthesis taxonomy → structures `docs/` output | Medium | Phase 2 |
| 7 | Multi-agent compatibility → confirms MCP + Agent Skills publication | Medium | Phase 3 |
| 8 | Knowledge compounding → delta reporting on re-index | Medium | Phase 3 |

**Key distinction maintained**: Karpathy uses LLM-judgment extraction (acceptable for personal notes). We use deterministic Spoon extraction for the graph (correctness-critical). LLM synthesis is ONLY for the human doc layer (Phase 2+).

**Full analysis**: `docs/karpathy-ideas-analysis.md`

---

### Documents Produced This Session

| File | Description |
|---|---|
| `docs/design-discussion.md` | Full design discussion with all 4 open questions, options, recommendations |
| `docs/karpathy-ideas-analysis.md` | Deep analysis of Karpathy's second brain pattern applied to this project |
| `docs/session-log.md` | This file — master session log |

---

### Open Decisions (as of end of Session 1)

1. **Analysis Engine**: Hybrid Tree-sitter + Spoon recommended — awaiting confirmation
2. **MCP Server mode**: Local stdio (per-project) vs network (shared) — awaiting decision
3. **Store versioning**: Option D (gitignore + manifest incremental) — awaiting confirmation
4. **Quality metrics in Phase 1**: Complexity only, or also coverage/tests? — awaiting decision
5. **Implementation language of the tool itself**: Java (natural fit with Spoon) vs Python — awaiting decision

---

### Next Steps (When Resuming)

- [ ] Resolve the 5 open decisions above
- [ ] Fold Karpathy's ideas (`lint`, `catalog`, `index-log.md`) formally into `requirements.md`
- [ ] Update `context.md` with session 1 outcomes
- [ ] Begin Phase 1 architecture design (schema, module layout, build structure)
- [ ] Per CLAUDE.md: architecture design must precede implementation

---

*Log last updated: 2026-08-24*
