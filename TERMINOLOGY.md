# Project Terminology & Ubiquitous Language

**Purpose:** To establish a shared vocabulary between the developer and AI assistants. This prevents assumptions, ensures precise communication, and acts as the definitive source for what specific terms mean in the context of this project.

---

## 1. Core Architecture Terms

### The Knowledge Base (or "The Graph")
* **What it IS:** A highly compressed, deterministic SQLite database (`graph.db`) containing exact mathematical proofs of how the codebase works (symbols, edges, contracts).
* **What it is NOT:** It is *not* a Vector Database. It is *not* a RAG (Retrieval-Augmented Generation) text index. It does not use embeddings to "guess" relationships. 

### The Contract (or "The Soul")
* **What it IS:** The semantic signature of a method or class. It includes the exact inputs (parameters), outputs (return types), modifiers, annotations, and JavaDoc.
* **What it is NOT:** It is *not* the actual implementation body (the `for` loops, `if` statements, etc.). It is the API boundary that an AI or human must respect to interact with that code.

### Semantic Execution Trace (The "Exact Flow")
* **What it IS:** A high-level, structured sequence of side-effects a method performs, stored inside its Contract. (e.g., `[READS: UserInput] -> [CALLS: AuthAPI] -> [WRITES: Database]`).
* **What it is NOT:** It is *not* a line-by-line copy of the source code. It is an abstract summary of *what* the code does to the system state.

## 2. Security & Bug Hunting Terms

### Reachability
* **What it IS:** Mathematical proof from our call graph that a specific vulnerable method or dependency is *actually executed* by the application code.
* **What it is NOT:** Just checking if a vulnerable library is listed in a `pom.xml` file.

### SAST (Static Application Security Testing)
* **What it IS:** In our context, querying the Semantic Execution Traces to find dangerous data flows (e.g., user input flowing into a SQL execution without passing through a validation contract).
* **What it is NOT:** Running external heavy enterprise scanners. This is done natively using our own graph.

## 3. Technology & Agent Terms

### MCP Server (Model Context Protocol)
* **What it IS:** The API layer we are building. It is the bridge that allows external AI agents to ask questions to our Knowledge Base.

### The Agent (or "AI Agent")
* **What it IS:** The external intelligence (like Claude Code, Cursor, GitHub Copilot, or a custom bug bounty script) that *uses* our tool. 
* **What it is NOT:** Our project itself is *not* an AI agent. Our project is the Orchestration Engine/Knowledge Base that makes the Agent smart.

### Spoon (The Extractor)
* **What it IS:** The Java library we use to parse source code. It uses a real Java compiler (ECJ) to understand types, inheritance, and generic signatures perfectly.
* **What it is NOT:** It is not a simple text parser or regex engine. 
