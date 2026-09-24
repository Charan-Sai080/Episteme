# Code Context & Impact Graph (AI Codebase Orchestration Engine)

**Status:** Locked & Finalized Architecture (2026-09-24)
**Target Language:** Java (for the tool itself and the target analysis)

## 1. Vision & Core Philosophy
An **AI Codebase Orchestration Engine** designed to sit beneath AI coding agents (Claude Code, Cursor, Copilot) and provide them with a mathematically proven, deterministic knowledge base of a Java project. 

It solves the massive context-window problem and hallucination loop of modern AI by providing **Semantic Contracts** and **Execution Traces** rather than forcing the AI to read raw files.

### Key Value Propositions
1. **Contract-First AI Generation:** Agents propose a "stub" (contract); the engine validates its safety against the graph; the agent implements it.
2. **Context Optimization (MCP):** Progressive disclosure of code structure saves tokens, money, and context bloat.
3. **Bug Bounty & Security:** Deep reachability and Data Flow tracking allows for autonomous zero-day discovery and SCA/SAST scanning.
4. **Instant Onboarding:** Semantic summaries allow agents to explain complex business logic instantly.

---

## 2. Technology Stack
* **File Discovery:** `git ls-files` (incremental re-indexing via manifest)
* **Parser & Extractor:** **Spoon** (Eclipse Compiler for Java - ECJ backend)
* **Storage:** **SQLite** (Single-file, local, WAL mode)
* **Data Serialization:** Protobuf / MessagePack (inside SQLite BLOBs for extreme compression)
* **Agent Integration:** **MCP Server** (Model Context Protocol)
* **Human Output:** Mermaid (architecture diagrams), Markdown

---

## 3. Database Schema Concept

```sql
-- Manifest for incremental updates
CREATE TABLE files (id INTEGER PRIMARY KEY, path TEXT, content_hash TEXT, last_indexed TEXT);

-- The Identity
CREATE TABLE symbols (id INTEGER PRIMARY KEY, kind INTEGER, name TEXT, fqn TEXT, file_id INTEGER);

-- The "Soul" (Semantic Contract & Flow)
CREATE TABLE contracts (
  symbol_id INTEGER PRIMARY KEY,
  modifiers INTEGER,       -- Bitmask
  return_type TEXT,
  parameters BLOB,         -- Binary compressed array
  annotations BLOB,        -- Binary compressed array
  data_flow BLOB,          -- Semantic Execution Trace (e.g., READS, WRITES, CALLS)
  javadoc TEXT
);

-- The Call Graph
CREATE TABLE edges (id INTEGER PRIMARY KEY, from_symbol INTEGER, to_symbol INTEGER, edge_type TEXT);
```

---

## 4. Feature Phases

### Phase 1: The Core Intelligence Engine
* Spoon batch-parsing of Java projects.
* Extraction of Semantic Contracts, Signatures, and Execution Traces.
* SQLite storage implementation.
* Basic CLI: `impact-index index` and `impact-index query`.

### Phase 2: Agent Integration & Context Optimization
* Implement the **MCP Server**.
* `catalog` tool: progressive disclosure of project structure.
* `validate_contract` tool: allows AI to submit a proposed method signature for graph compatibility checking.

### Phase 3: Security & Bug Hunting Layer
* Implement SAST rule engine over the Data Flow execution traces.
* CVE reachability analysis.
* `impact-index scan` for autonomous vulnerability discovery.

### Phase 4: CI/CD & Human Layer
* Incremental git-diff indexing.
* Generation of `architecture.md` and Mermaid charts.

---

## 5. CLI Interface Map
* `impact-index index` : Builds the SQLite knowledge base.
* `impact-index query` : Returns the semantic contract and side-effects of a symbol.
* `impact-index lint` : Finds orphans, dead code, and dangling edges.
* `impact-index catalog`: Generates top-level context for AI onboarding.
* `impact-index scan` : Runs the security / bug bounty engine.
