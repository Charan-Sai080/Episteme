<div align="center">
  <h1>🏛️ Episteme</h1>
  <p><b>The AI Codebase Orchestration & Knowledge Graph Engine</b></p>
  <p>
    <img src="https://img.shields.io/badge/Java-21-blue.svg" alt="Java 21" />
    <img src="https://img.shields.io/badge/Storage-SQLite-003B57.svg" alt="SQLite" />
    <img src="https://img.shields.io/badge/Parser-Spoon-orange.svg" alt="Spoon" />
    <img src="https://img.shields.io/badge/License-PolyForm%20Noncommercial-red.svg" alt="License" />
  </p>
</div>

---

## 📖 Overview

Modern AI coding agents (like Claude, Cursor, and Copilot) suffer from "Context Window Exhaustion" and hallucination when dealing with massive enterprise codebases. They rely on reading flat text files or fuzzy vector searches (RAG), which often fail to understand deep architectural dependencies.

**Episteme replaces guessing with mathematical proof.** 

It is a deterministic static-analysis engine that parses Java repositories into an ultra-compressed SQLite Knowledge Graph. By extracting **Semantic Contracts** (exact signatures and annotations) and **Execution Traces** (data flows, reads, writes, and calls), Episteme provides AI agents with a perfect map of the codebase.

## ✨ Key Features

* **Compiler-Grade Accuracy:** Powered by **Spoon** (Eclipse Compiler for Java), Episteme understands full type resolution, generics, and lambdas perfectly.
* **Extreme Compression:** Semantic Contracts and AST data are binary-compressed using **MessagePack** and stored in **SQLite BLOBs**, keeping the database tiny and lightning-fast.
* **Recursive Graph Traversal:** Built-in SQLite Recursive CTEs allow instant answers to deep architectural questions (e.g., *"If I change this API endpoint, what database queries are impacted downstream?"*).
* **Progressive Disclosure API (WIP):** An embedded Javalin server designed specifically for the **Model Context Protocol (MCP)**, allowing AI agents to navigate the codebase hierarchically without crashing their context windows.
* **Deduplication:** Automatically deduplicates external standard libraries (`java.util.*`) via lightweight symbol stubbing.

## 🚀 Quick Start

### 1. Build the Engine
Requires Java 21 and Maven.
```bash
mvn clean package
```
This generates the executable fat-jar in the `target/` directory.

### 2. Index a Repository
Build the `graph.db` Knowledge Base for any Java project.
```bash
java -jar target/episteme-engine-1.0.0-SNAPSHOT.jar index --project /path/to/your/java/project
```

### 3. Query the Graph (Coming Soon in Phase 3)
Start the embedded MCP/REST server for AI agents to connect to.
```bash
java -jar target/episteme-engine-1.0.0-SNAPSHOT.jar serve --port 8080
```

## 🏗️ Architecture Roadmap

Episteme is built in strict, test-driven phases:
- [x] **Phase 1: Ingestion Engine** (Spoon AST parsing, SQLite WAL storage, Binary Compression).
- [x] **Phase 2: Graph Resolution** (Inter-file symbol mapping, Edges, Recursive SQL Traversal).
- [ ] **Phase 3: Orchestration API** (Javalin REST Server, Progressive Disclosure for MCP).
- [ ] **Phase 4: Proactive Security** (Autonomous Bug Hunting, SAST, Source-to-Sink Taint Tracking).

For a detailed breakdown of the architecture, definitions, and internal workings, please see the `docs/` and `.claude/` directories.

## ⚖️ License

This project is licensed under the **PolyForm Noncommercial License 1.0.0**. 

You are free to read, modify, and use this software for learning, research, or personal projects. **Commercial use is strictly prohibited without a commercial license.** 

If you wish to use Episteme in a commercial product, production environment, or as part of a paid service, you must contact the author to acquire an Enterprise Commercial License. See `LICENSE` for exact details.
