# Product Lifecycle, Phases, and Milestones

**Date:** 2026-09-24  
**Purpose:** To define the end-to-end flow of the product (how it actually works in practice) and break down the development roadmap into distinct, achievable milestones to ensure all targets are met.

---

## 1. The End-to-End Product Flow (How It Works)

This is the exact lifecycle of how a repository moves from raw code to an intelligent, agent-ready asset.

### Step 1: The Ingestion (Parsing the Soul)
* **Action:** The developer runs `impact-index index` on a massive enterprise project.
* **Under the Hood:** 
    * `git ls-files` finds all tracked Java files.
    * Spoon (the compiler engine) loads the entire module into memory in batch mode.
    * It extracts **Semantic Contracts** (Signatures, Parameters, Annotations) and **Execution Traces** (Reads, Writes, Calls).
    * It compresses this data into binary Protobuf/MessagePack BLOBs and saves it to a local SQLite file (`graph.db`).

### Step 2: The Agent Query (Progressive Disclosure)
* **Action:** An AI agent (e.g., Cursor, Claude) begins working on a ticket to "Update the checkout flow."
* **Under the Hood:**
    * Before editing, the AI calls the MCP server: `catalog()`.
    * The server responds instantly with a high-level map of the 3 main packages involved in checkout.
    * The AI drills down: `query_contract('CheckoutService')`.
    * The server returns the Semantic Contract, telling the AI exactly what dependencies it requires and what side-effects it has, without the AI needing to read 5,000 lines of source code.

### Step 3: Contract-First Validation (The Holy Grail)
* **Action:** The AI agent wants to modify a method. It proposes a change.
* **Under the Hood:**
    * The AI sends a proposed new contract to `validate_contract()`.
    * The SQLite knowledge base simulates the change against the call graph. It flags that changing this method signature will break 12 other files.
    * The AI adjusts its plan before writing a single line of actual code.

### Step 4: Proactive Security / Bug Bounty
* **Action:** The user runs `impact-index scan`.
* **Under the Hood:**
    * The security engine queries the Execution Traces.
    * It finds a path where `[READ: User Input]` flows directly to `[CALL: Database.execute]` without hitting a `[VALIDATE]` contract.
    * It generates a report identifying a high-severity zero-day SQL Injection, including the exact file and line numbers.

---

## 2. Project Milestones & Phases

To build this massive vision, we must divide and conquer.

### Phase 1: The Core Intelligence Engine (Weeks 1-4)
* **Goal:** Successfully parse a complex Java project into a compressed SQLite graph.
* **Milestone 1:** Setup Java (Maven/Gradle) project and integrate Spoon.
* **Milestone 2:** Design and implement the SQLite schema with compressed binary BLOBs for contracts.
* **Milestone 3:** Successfully build a `graph.db` from a real-world open-source Java project (e.g., Spring PetClinic).

### Phase 2: The Context Optimizer (MCP Server) (Weeks 5-8)
* **Goal:** Serve the knowledge base to AI agents efficiently.
* **Milestone 4:** Implement the local STDIO MCP Server.
* **Milestone 5:** Build the `catalog` and `query` tools using Semantic Paging (Progressive Disclosure).
* **Milestone 6:** End-to-End Test: Connect Claude Code or Cursor to the local MCP server and have it successfully understand a codebase without reading raw files.

### Phase 3: The "Soul" Validation (Contract-First AI) (Weeks 9-11)
* **Goal:** Allow AI to plan safely.
* **Milestone 7:** Implement the `validate_contract` MCP tool.
* **Milestone 8:** Write the graph-traversal logic to detect breaking changes when a contract is modified.

### Phase 4: Proactive Security & Bug Bounty Engine (Weeks 12-16)
* **Goal:** Autonomous zero-day discovery and reachability analysis.
* **Milestone 9:** Implement Dependency Scanning (SCA) querying NVD/OSV databases.
* **Milestone 10:** Implement SAST rules engine over Execution Traces (SQLi, XSS, Auth Bypass).
* **Milestone 11:** Test against known vulnerable repositories (e.g., WebGoat) to prove zero false-positives.

### Phase 5: Commercialization & Enterprise Wrap
* **Milestone 12:** Build CI/CD GitHub Actions wrapper.
* **Milestone 13:** Launch Freemium Open Source version.
* **Milestone 14:** Begin enterprise sales / Bug Bounty farming.
