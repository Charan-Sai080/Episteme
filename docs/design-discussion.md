# Code Context & Impact Graph — Design Discussion & Implementation Plan

**Target Language:** Java  
**Status:** Pre-implementation — design discussion, awaiting decisions  
**Last Updated:** 2026-08-24

---

## Big Picture Vision

> "An AI tool for developing projects and helping anyone get started with existing old projects without problems — integrable into any AI-enabled IDE — a tool in the orchestration layer."

This reframes the project beyond a simple symbol indexer. The end goal is a **context intelligence layer** that any AI agent or IDE plugin can call to answer:

- *"What does this class/method touch?"*
- *"What will break if I change this?"*
- *"Explain the architecture of this module to someone new."*
- *"What are the quality/risk signals for this file?"*

The tool sits **between the AI agent and the raw codebase**, pre-computing and persisting answers that would otherwise require the agent to re-read thousands of files on every task.

```
┌─────────────────────────────────────────────────────────┐
│                  Orchestration Layer                    │
│  ┌───────────┐  ┌──────────────┐  ┌─────────────────┐  │
│  │ AI Agent  │  │  IDE Plugin  │  │  CI/CD Pipeline │  │
│  └─────┬─────┘  └──────┬───────┘  └────────┬────────┘  │
│        └───────────────┼───────────────────┘            │
│                        ▼                               │
│         ┌──────────────────────────────┐               │
│         │   Code Context & Impact MCP  │  ← THIS TOOL  │
│         │   (Query API + Doc Gen)      │               │
│         └─────────────┬────────────────┘               │
│                       ▼                               │
│         ┌──────────────────────────────┐               │
│         │  Graph Store (SQLite)        │               │
│         │  symbols | edges | files     │               │
│         └─────────────┬────────────────┘               │
│                       ▼                               │
│              Java Repository / Classpath              │
└─────────────────────────────────────────────────────────┘
```

---

## Open Question 1 — Java Analysis Engine

### The Core Problem

Tree-sitter (the original v1 plan) is syntactic only — it identifies that something is a `method_declaration` but does not know what type that variable is, or what class a method call resolves to. **IntelliJ's "Go to Definition" works because it does full semantic resolution** — it walks the scope chain, checks classpath, resolves generics, follows inheritance hierarchies. We need to decide how close to that we want to be in v1.

### How IntelliJ Does It (Internally)

IntelliJ uses its **PSI (Program Structure Interface)** model. The relevant mechanism:

1. Parse source → PSI tree (richer than raw AST)
2. Build a **stub index** — lightweight structural info (class names, method signatures) without fully parsing every file
3. On "Find References" or "Go to Definition":
   - Walk scope chain upward (block → method → class → file → package → classpath)
   - Hit the stub index to narrow down candidate declarations
   - Fully resolve types using the Java compiler's type model
4. Return exact file:line for the declaration

SonarQube uses the same principle: **Eclipse Compiler for Java (ECJ)** parses source into an AST enriched with semantic info, then rules traverse it. SonarJava is not a regex scanner — it's a proper type-aware rule engine.

### Option Comparison

| Option | Depth | Setup Cost | Accuracy | Java Library Support | Notes |
|---|---|---|---|---|---|
| **A — Tree-sitter (Java grammar)** | Syntactic only | Very low | ~60% on cross-file refs | Poor (no classpath) | Fast, resilient to broken code. Can't resolve `MyService.doThing()` to a class definition. |
| **B — JavaParser + JavaSymbolSolver** | Syntactic + optional semantic | Low-Medium | ~80% with classpath configured | Good (Maven/Gradle classpath) | Apache 2.0 license. Lightweight add-on model. Well documented. |
| **C — Spoon** | Full semantic (compiler-grade) | Medium | ~95%+ | Excellent (ECJ-backed) | Built on Eclipse JDT. First-class API for type hierarchies, overrides, subtype checks. Exactly what SonarQube uses. |
| **D — Eclipse JDT Language Server (jdt.ls)** | Full semantic (LSP) | High | ~99% | Excellent | Requires running JVM + LSP server process. Best accuracy (what VS Code Java uses), but complex to embed. Better as Phase 4. |
| **E — Hybrid: Tree-sitter (file index) + Spoon (semantic pass)** | Two-phase | Medium | ~95% | Excellent | Tree-sitter builds the initial file manifest fast + handles broken files; Spoon does the type-aware symbol/edge extraction. Cleanest separation of concerns. |

### Recommendation

> **Option E — Hybrid Tree-sitter + Spoon**

- **Tree-sitter** handles the **file manifest phase**: enumerate files, detect language, handle partial/broken files gracefully, provide fast re-check on file change.
- **Spoon** handles the **semantic extraction phase**: per-file AST with full type resolution (classes, interfaces, inheritance, method call targets resolved to their declaring class). This gives IntelliJ-level accuracy.
- **JavaSymbolSolver** (Option B) is a reasonable fallback if Spoon's ECJ dependency proves problematic.

> **Decision needed**: Compiler-grade accuracy (Spoon/JDT) or best-effort syntactic (Tree-sitter/JavaParser) for v1?  
> The accuracy gap is large for Java — generics, interfaces, Spring beans, and `@Autowired` are invisible to purely syntactic tools.

---

## Open Question 2 — Agent Integration: MCP Server vs CLI vs Hybrid

### Option Comparison

| Option | Discoverability | Latency | Multi-agent | Stateful | Best For |
|---|---|---|---|---|---|
| **A — CLI only** | Low (must know commands) | Lowest | Poor | No | Simple scripts, inner-loop dev use |
| **B — MCP Server only** | High (tools auto-discovered) | Medium | Excellent | Yes | Enterprise, multi-agent orchestration |
| **C — Hybrid: CLI + MCP wrapper** | High | Low (CLI) / Medium (MCP) | Excellent | Optional | Production: CLI for speed, MCP for discoverability |

### Recommendation

> **Option C — Build CLI first, expose as MCP server**

1. Implement all commands as a clean CLI first (`impact-index index`, `impact-index query <symbol>`, `impact-index docs`)
2. Wrap the CLI behind an MCP server that exposes the same commands as typed, discoverable tools
3. The MCP server is thin — it validates arguments and calls the same library code directly

> **Decision needed**: Should the MCP server be a local stdio server (per-project, started by the IDE) or a network server (shared across projects/agents)?

---

## Open Question 3 — SQLite Graph Store: Version Control Strategy

### Options

| Option | Pros | Cons |
|---|---|---|
| **A — Checked-in snapshot** | Always available, works offline | Binary file in git, grows over time, merge conflicts |
| **B — `.gitignore`d, rebuild on clone** | Clean repo, always deterministic | Clone requires full re-index (slow for large repos) |
| **C — CI-generated artifact** | Automated, authoritative | Requires CI, not available locally before first run |
| **D — `.gitignore`d + manifest-based incremental (recommended)** | Fast incremental re-index, no binary in repo, deterministic | Requires `git ls-files` integration from day one |

### The Manifest Proposal

```sql
CREATE TABLE files (
  id                  INTEGER PRIMARY KEY,
  path                TEXT NOT NULL,
  content_hash        TEXT NOT NULL,
  last_indexed_commit TEXT,
  status              TEXT DEFAULT 'indexed'  -- indexed | stale | excluded
);
```

On re-index, `git diff --name-only <last_indexed_commit>..HEAD` yields only the changed files. Only those are re-parsed. This directly solves the staleness problem.

### Recommendation

> **Option D** — SQLite store in `.gitignore`, rebuilt incrementally via manifest table.

> **Decision needed**: Should we include a `--force-reindex` CLI flag that blows away and rebuilds from scratch?

---

## Open Question 4 — SonarQube-Style Quality Integration

### What SonarQube Does (Relevant to Us)

1. **ECJ → AST + semantic model** (same stack as Spoon — both ECJ-based)
2. **Rule traversal**: for each AST node, check active rules (complexity, naming, null-safety, etc.)
3. **Taint analysis**: track data from untrusted source → sink (SQL injection, XSS)
4. **Test result integration**: reads JUnit XML reports, links test failures to specific methods
5. **Coverage**: reads JaCoCo/Cobertura XML to annotate lines with coverage data

### Integration Options

| Capability | Source | Integration Effort |
|---|---|---|
| **Code complexity per method** | Spoon AST traversal (cyclomatic complexity) | Low — computable from same AST we already parse |
| **Code smell rules** | Reuse SonarJava rule annotations on our AST | Medium — SonarJava is open source (LGPL); rules can be borrowed |
| **Test coverage** | Read existing JaCoCo XML output | Low — parse XML, annotate `symbols` table with coverage % |
| **Test failure linkage** | Read Surefire/JUnit XML reports | Low — parse XML, store failed tests in `test_results` table |
| **Taint/security analysis** | SonarJava's taint engine (complex) | High — Phase 4+ only |

### Proposed Schema Addition

```sql
CREATE TABLE quality_metrics (
  symbol_id   INTEGER REFERENCES symbols(id),
  metric_name TEXT,   -- 'cyclomatic_complexity', 'test_coverage', 'code_smells'
  value       REAL,
  source      TEXT    -- 'spoon', 'jacoco', 'surefire'
);
```

> **Decision needed**: Should quality metrics (complexity, coverage) be in Phase 1 or Phase 2?  
> Recommendation: complexity is free (computed during Spoon parse), coverage/test results are Phase 2.

---

## Proposed Revised Phased Roadmap

### Phase 1 — Java Symbol Impact Index (agent-facing)
- **File Discovery**: `git ls-files` → `files` manifest table in SQLite
- **Parser**: Tree-sitter (file enumeration, fast re-check) + Spoon (semantic extraction)
- **Graph Store**: `symbols`, `edges`, `files` tables
- **Quality**: Cyclomatic complexity per method (free from Spoon AST, no extra cost)
- **CLI**: `impact-index index <repo>`, `impact-index query <symbol>`, `impact-index lint`, `impact-index catalog`
- **Acceptance**: query any Java class/method → all callers, readers, writers with file:line; resolves across class hierarchy

### Phase 2 — Human Documentation + Quality Dashboard
- **Doc Generator**: `architecture.md` with C4-style Mermaid diagrams
- **Quality Integration**: Read JaCoCo coverage XML + Surefire test result XML → annotate symbols
- **MCP Server**: Wrap CLI as MCP server with typed, discoverable tools
- **Acceptance**: A new engineer reads generated docs + coverage report and understands module structure

### Phase 3 — Incremental Updates
- **Git-diff-based re-index**: Only re-parse changed files via manifest table
- **Watch mode**: Optional file-system watcher triggers re-index on save
- **Acceptance**: Changing one file updates graph in < 2 seconds on a 100k-line repo

### Phase 4 — Multi-IDE Integration + Advanced Analysis
- **JDT.LS integration**: Replace Spoon with JDT Language Server for runtime-accurate resolution (Spring beans, DI, dynamic proxies)
- **Taint analysis**: Security vulnerability tracking (SQL injection, XSS paths)
- **IDE plugin**: VS Code / IntelliJ plugin that queries the MCP server inline

---

## Technology Stack (Revised for Java)

| Concern | Choice | Why |
|---|---|---|
| File discovery | `git ls-files` + manifest table | Deterministic, respects `.gitignore`, incremental-friendly |
| File enumeration | Tree-sitter (Java grammar) | Fast, handles broken/partial files |
| Semantic extraction | **Spoon** (ECJ-backed) | Compiler-grade accuracy, type resolution, inheritance, generics |
| Storage | SQLite | Zero-ops, sufficient for single-project scale |
| Quality metrics | Spoon AST (complexity) + JaCoCo/Surefire XML (coverage/tests) | Free from existing parse; integrates with Maven/Gradle test pipeline |
| Agent integration | CLI + MCP server | CLI for development; MCP for IDE/agent discoverability |
| Diagrams | Mermaid | Renders in Markdown/Artifacts natively |
| Build system | Maven or Gradle (the tool itself is a Java project) | Eats its own dog food |

---

## Risks (Updated)

| Risk | Mitigation |
|---|---|
| Spoon startup time (JVM + ECJ) is slow | Run as a long-lived daemon/server process, not per-query |
| Spring/CDI dependency injection makes static references incomplete | Document as known limitation; Phase 4 adds JDT.LS for DI resolution |
| Graph staleness if manifest re-index lags | `status` field + visible "last indexed at commit X" in all outputs |
| Scope creep toward full Joern/CodeQL | Hard non-goal boundary; revisit only in Phase 4 with evidence |
| MCP schema bloat consuming agent context window | Keep tools minimal: 3–5 tools (`index`, `query`, `lint`, `catalog`, `docs`); lazy-load full schema |

---

## Decisions Still Needed

1. **Analysis Engine**: Tree-sitter + Spoon hybrid (recommended) vs JavaParser vs JDT.LS?
2. **Agent Integration**: MCP + CLI hybrid (recommended) — local stdio or network MCP server?
3. **Store Versioning**: Option D (`.gitignore` + manifest-based incremental) confirmed?
4. **Quality Scope in Phase 1**: Complexity only (recommended), or also coverage/tests?
5. **Implementation Language**: The tool itself — Java (Spoon is a Java library, natural fit) or Python with subprocess calls?
