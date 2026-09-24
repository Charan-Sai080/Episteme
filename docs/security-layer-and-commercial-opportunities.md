# Security Intelligence Layer & Commercial Opportunities

**Context**: Expansion of the Code Context & Impact Graph project  
**Author session**: 2026-08-25  
**Audience**: Final year B.Tech student exploring project extensions + career opportunities

---

## Part 1 — The Security Intelligence Idea: Is It Real?

Yes. What you're describing already exists as a multi-billion dollar industry. Understanding exactly what exists — and where the gap is — is critical before adding this to the project.

Let me break down what "CVE and zero-day vulnerability detection" actually means in practice, because these are two very different things.

---

## The Two Types of Vulnerabilities (This Distinction is Critical)

### Type 1 — Known CVEs (Common Vulnerabilities and Exposures)

These are **publicly documented** vulnerabilities with an assigned ID like `CVE-2021-44228` (Log4Shell).

They live in public databases:
- **NVD** — National Vulnerability Database (US government)
- **OSV** — Open Source Vulnerabilities (Google)
- **GitHub Advisory Database**

**Example**: Your project uses `log4j-core:2.14.1`. The NVD says this exact version has Log4Shell. This is a known CVE — we just need to check if you're using a vulnerable version.

**This is what tools like OWASP Dependency-Check, Snyk, and Grype do today.** It's called **Software Composition Analysis (SCA)**.

### Type 2 — Zero-Day / Novel Vulnerabilities

These are **undiscovered bugs** in your own code that haven't been reported anywhere.

**Example**: You wrote a method that takes user input and passes it directly to a SQL query without sanitization. No CVE exists for this because it's specific to your code. This is what **SAST (Static Application Security Testing)** tools detect using taint analysis.

**This is what CodeQL, Semgrep, and SonarQube do.**

### What Our Project Can Realistically Do

Our graph already has everything needed to power **both** — but with different levels of effort:

| Capability | Effort | Uses Our Graph? | What's New? |
|---|---|---|---|
| Known CVE detection (SCA) | Low | ✅ Yes — we already parse `pom.xml` during indexing | Just query NVD/OSV API |
| **Reachability-aware CVE** | Medium | ✅ Yes — call graph tells us if vulnerable code is actually called | Our key differentiator |
| SAST patterns (SQL injection, XSS) | Medium | ✅ Yes — taint source → sink via edge traversal | Add Semgrep-style rules |
| True zero-day discovery | Very High | ⚠️ Partially — needs full data flow analysis | Phase 4+ |

---

## Part 2 — How the Security Layer Works (Technical Design)

### Layer A — Dependency Vulnerability Scanning (SCA)

**What we already have**: During indexing, Spoon reads your `pom.xml` or `build.gradle` to resolve the classpath. We already know which libraries you depend on.

**What we add**:

```
Step 1: Extract dependency list from pom.xml
        → org.apache.logging.log4j:log4j-core:2.14.1
        → org.springframework:spring-web:5.3.10
        → com.fasterxml.jackson.core:jackson-databind:2.12.3
        ...

Step 2: Query OSV/NVD API for each dependency + version
        → log4j-core 2.14.1 → CVE-2021-44228 (CRITICAL, CVSS 10.0)
        → jackson-databind 2.12.3 → CVE-2022-42003 (HIGH, CVSS 7.5)

Step 3: Store results in new table
```

**New database table**:
```sql
CREATE TABLE vulnerabilities (
  id            INTEGER PRIMARY KEY,
  dependency    TEXT,           -- "org.apache.logging.log4j:log4j-core"
  version       TEXT,           -- "2.14.1"
  cve_id        TEXT,           -- "CVE-2021-44228"
  severity      TEXT,           -- "CRITICAL" | "HIGH" | "MEDIUM" | "LOW"
  cvss_score    REAL,           -- 10.0
  description   TEXT,           -- "Log4Shell RCE vulnerability..."
  fix_version   TEXT,           -- "2.17.1"
  is_reachable  INTEGER,        -- 1 (true) | 0 (false) | NULL (unknown)
  checked_at    TEXT            -- timestamp
);
```

---

### Layer B — Reachability Analysis (Our Key Differentiator)

This is where our project becomes **genuinely novel**. Every other SCA tool just tells you "you have a vulnerable dependency." Most developers ignore this because 90% of the time, they don't actually call the vulnerable function.

**Snyk charges extra for this feature. We get it for free because we already have the call graph.**

Here's how it works:

**Example**: Log4Shell (CVE-2021-44228) is exploited via `Logger.error(userInput)`. The vulnerable function is `org.apache.log4j.Logger.log()`.

**Without reachability**:
> "You use log4j 2.14.1 which has CVE-2021-44228. Fix it."

**With reachability** (our approach):
```
Checking reachability for CVE-2021-44228...

The vulnerable function: org.apache.log4j.Logger.log()

Tracing call graph backwards from this function...

REACHABLE — found in 3 execution paths:
  UserController.login()       → Logger.log(userInput)    ← CRITICAL (user input reaches logger)
  PaymentService.processOrder() → Logger.log(orderId)     ← LOW (orderId is internal, not user-controlled)
  HealthCheckEndpoint.ping()   → Logger.log("pong")       ← NONE (static string, not user input)

Risk Assessment:
  UserController.login() path is the ONLY exploitable path.
  Fix: sanitize user input before logging in UserController.java:45
  Or: upgrade log4j to 2.17.1 (eliminates vulnerability entirely)
```

**How we build this**:
1. CVE databases (NVD, OSV) publish "affected functions" for many CVEs — the specific methods that are exploitable
2. Our `edges` table already has the full call graph
3. We traverse backwards from the vulnerable function through the call graph to find all paths that originate from user input (HTTP endpoints, form handlers, socket listeners)

```sql
-- Find all paths that call the vulnerable function
-- and originate from an HTTP endpoint (user-controlled input)
WITH RECURSIVE call_chain AS (
  -- Start from the vulnerable function
  SELECT from_symbol, to_symbol, 1 as depth
  FROM edges
  WHERE to_symbol IN (SELECT id FROM symbols WHERE fqn LIKE 'org.apache.log4j.Logger%')
  
  UNION ALL
  
  -- Walk backwards through callers
  SELECT e.from_symbol, e.to_symbol, cc.depth + 1
  FROM edges e
  JOIN call_chain cc ON e.to_symbol = cc.from_symbol
  WHERE cc.depth < 20  -- limit recursion depth
)
SELECT DISTINCT s.fqn, s.file, s.line
FROM call_chain cc
JOIN symbols s ON cc.from_symbol = s.id
WHERE s.fqn LIKE '%Controller%'  -- HTTP endpoints are typically in controllers
   OR s.fqn LIKE '%Endpoint%'
   OR s.fqn LIKE '%Handler%';
```

This is exactly what Snyk's "reachability analysis" does — and they charge significantly more for it.

---

### Layer C — SAST Pattern Detection (Novel Vulnerability Discovery)

This is where we start detecting bugs **in your own code** — not just in dependencies.

**Example — SQL Injection**:

This is a vulnerability in YOUR code:
```java
// PaymentService.java line 45
public Order findOrder(String userInput) {
    String query = "SELECT * FROM orders WHERE id = " + userInput;  // ← DANGEROUS
    return database.execute(query);  // ← SQL injection sink
}
```

A user calling `findOrder("1 OR 1=1")` can dump your entire database.

**How we detect this using our graph**:

We define patterns called **taint flows**:
- **Source**: Places where user input enters the system (HTTP request parameters, form fields, socket reads)
- **Sink**: Places where data is used dangerously (SQL queries, shell commands, file paths, HTML output)
- **Rule**: If there's a path in the call graph from a Source to a Sink, and the data isn't sanitized along the way → flag it

```
SAST Rule: SQL_INJECTION

Sources (user input entry points):
  - HttpServletRequest.getParameter()
  - HttpServletRequest.getBody()
  - @RequestParam annotated methods

Sinks (dangerous operations):
  - Statement.execute(String)
  - PreparedStatement.executeQuery(String)
  - EntityManager.createNativeQuery(String)

Sanitizers (things that make it safe):
  - PreparedStatement with ? placeholders
  - Hibernate ORM methods
  - OWASP AntiSamy

Detection: trace from source → sink through edge graph
           if no sanitizer is on the path → VULNERABILITY
```

**Common SAST rules we can implement**:

| Rule | What it detects | CVSS Range |
|---|---|---|
| SQL Injection | User input → SQL query | 7.5–9.8 (HIGH–CRITICAL) |
| Command Injection | User input → Runtime.exec() | 8.0–10.0 (CRITICAL) |
| Path Traversal | User input → File.open() | 6.5–9.0 |
| XSS | User input → HTTP response body | 5.0–8.0 |
| Insecure Deserialization | Untrusted data → ObjectInputStream | 8.0–10.0 |
| Log Injection | User input → Logger | 4.0–6.0 |
| SSRF | User input → HTTP client URL | 7.0–9.0 |
| Hardcoded Credentials | String literals near auth keywords | 9.0–10.0 |

---

### Layer D — Honest Assessment of "Zero-Day Discovery"

I need to be completely honest with you here, as per the Engineering Constitution.

**What "zero-day" actually means**: A vulnerability that nobody has discovered yet — no CVE, no public knowledge.

**Can our tool find true zero-days?** Partially. Here's the reality:

✅ **We CAN find**:
- SQL injection, XSS, command injection — via taint analysis (Layer C above)
- Hardcoded secrets/API keys — via pattern matching on string literals
- Known-vulnerable patterns (use of weak crypto like MD5 for passwords)
- Insecure deserialization paths
- Dependency CVEs that haven't been patched (which are technically "zero-day" for your deployment)

⚠️ **We CANNOT reliably find** (without full data flow analysis):
- Complex multi-hop vulnerabilities (data transformed across 10 methods before reaching sink)
- Vulnerabilities that require understanding runtime behavior (thread races, timing attacks)
- Business logic flaws (approving a payment without authorization — context-dependent)
- Vulnerabilities in dynamically loaded classes or reflection-based code

**The honest framing**: Call our Layer C "SAST vulnerability detection" — not "zero-day discovery." It finds real vulnerabilities in your code using pattern-based taint analysis. This is the same thing CodeQL and Semgrep do. It's genuinely useful and valuable — just be accurate about what it is.

---

## Part 3 — How It Integrates Into Our Architecture

Our security layer sits between the Graph Store and the Query API:

```
┌────────────────────────────────────────────────────────┐
│              GRAPH STORE (graph.db)                    │
│  symbols | edges | files | quality_metrics             │
│                                                        │
│  + NEW: vulnerabilities | taint_flows | sast_findings  │
└───────────────────────┬────────────────────────────────┘
                        │
              ┌─────────▼──────────┐
              │  SECURITY ENGINE   │
              │                    │
              │  SCA Scanner:      │
              │  pom.xml deps →    │
              │  NVD/OSV API →     │
              │  vulnerabilities   │
              │  table             │
              │                    │
              │  Reachability:     │
              │  call graph →      │
              │  is CVE reachable? │
              │                    │
              │  SAST Engine:      │
              │  taint rules →     │
              │  source-to-sink    │
              │  path detection    │
              └─────────┬──────────┘
                        │
              ┌─────────▼──────────┐
              │  NEW CLI COMMANDS  │
              │                    │
              │  impact-index scan │
              │  → full security   │
              │    report          │
              │                    │
              │  impact-index cve  │
              │  → dependency CVEs │
              │  + reachability    │
              │                    │
              │  impact-index sast │
              │  → code patterns   │
              └────────────────────┘
```

**Example output of `impact-index scan`**:

```
Security Scan Report — my-java-project
Last indexed: commit abc1234 (2026-08-25)

═══════════════════════════════════════════════
CRITICAL (2)
═══════════════════════════════════════════════

[CVE-2021-44228] Log4Shell — log4j-core 2.14.1
  Severity: CRITICAL (CVSS 10.0)
  Fix: upgrade to log4j-core 2.17.1
  Reachability: REACHABLE via UserController.login() → Logger.log(userInput)
  → This is actively exploitable. Fix immediately.

[SAST-SQL-001] SQL Injection — PaymentService.java:45
  Severity: CRITICAL (CVSS 9.8)
  Pattern: userInput → database.execute(query) without sanitization
  Path: OrderController.createOrder() → PaymentService.findOrder() → DB.execute()
  Fix: Use PreparedStatement with ? placeholders

═══════════════════════════════════════════════
HIGH (1)
═══════════════════════════════════════════════

[CVE-2022-42003] Unsafe deserialization — jackson-databind 2.12.3
  Severity: HIGH (CVSS 7.5)
  Fix: upgrade to jackson-databind 2.14.0
  Reachability: NOT REACHABLE — jackson is imported but the vulnerable
                polymorphic deserialization method is never called.
  → Low priority. Fix in next scheduled dependency update.

═══════════════════════════════════════════════
SUMMARY
═══════════════════════════════════════════════
  Critical: 2  |  High: 1  |  Medium: 3  |  Low: 7
  Dependencies scanned: 47
  Reachable CVEs: 1 of 3 found CVEs (2 are unreachable = low priority)
  SAST findings: 4 patterns detected
```

---

## Part 4 — Commercial Services You Can Build on This

Here is the honest economic landscape. The application security market is **$13.6 billion in 2026** and growing. Here are every realistic product you could build:

---

### 🟢 Tier 1 — Realistic to Build as a Student / Small Team

#### Product 1 — Open Source Security Scanner (Free + Paid Pro)

**What it is**: `impact-index scan` — the security layer we just designed. Free for open-source projects, paid for private repos.

**Business model**: Freemium
- Free: dependency CVE scanning, 5 SAST rules
- Pro ($9/month/developer): full SAST rules, reachability analysis, CI/CD integration, historical reports

**Competitors**: OWASP Dependency-Check (free, no reachability), Grype (free, no SAST), Trivy (free, no SAST)

**Why you can compete**: Reachability analysis is a paid feature everywhere else. You get it free because you already have the call graph from Phase 1. This is a genuine moat.

**Market**: Developers who can't afford Snyk ($100+/month) but need more than basic dependency scanning.

---

#### Product 2 — CI/CD Security Gate (GitHub Action / GitLab CI)

**What it is**: A GitHub Action that runs `impact-index scan` on every pull request and blocks merge if CRITICAL vulnerabilities are found.

```yaml
# .github/workflows/security.yml
- uses: your-org/impact-index-action@v1
  with:
    fail-on: CRITICAL
    reachability-check: true
```

**Business model**: 
- Free for public repos
- $5/month per private repo
- $99/month unlimited (for teams)

**Competitors**: Snyk PR check ($$$), Dependabot (basic, no SAST), CodeQL (GitHub-native but complex to configure)

**Why you can compete**: Easier to set up than CodeQL, more accurate than Dependabot (reachability), cheaper than Snyk.

---

#### Product 3 — Legacy Java Audit Service (Consulting + Tool)

**What it is**: A paid service where you run `impact-index` + security scan on large old Java codebases and produce a professional security + architecture audit report.

**Target customers**: Banks, insurance companies, government agencies — they all have 10-year-old Java monoliths and need security audits to comply with regulations (PCI-DSS, SOC2, ISO27001).

**Business model**: 
- One-time audit: ₹2–10 lakh per project (or $5,000–$20,000)
- Ongoing monitoring subscription: ₹50,000/month (or $1,500/month)

**Why it's realistic**: You don't need to build a SaaS product. You just run your tool on their codebase and charge for the report + recommendations. This is pure consulting powered by your tool.

**This is something you can sell immediately after Phase 1 is working.**

---

### 🟡 Tier 2 — Needs 6–12 months, Real Engineering Team

#### Product 4 — SaaS Security Platform (Snyk Competitor)

**What it is**: A full web application where companies connect their GitHub/GitLab, you index their repos, and they get a dashboard showing security posture, CVEs, SAST findings, reachability, and fix recommendations.

**Business model**: Per-developer SaaS
- Starter: $9/dev/month (up to 10 devs)
- Team: $15/dev/month
- Enterprise: $30+/dev/month + support SLA

**Market size**: SAST market = $680M in 2026, growing to $1.89B by 2031

**What you need to build beyond the CLI**:
- Web dashboard (React/Next.js)
- Git integration (GitHub/GitLab OAuth)
- Background job system (re-scan on every commit)
- User auth + billing
- Report generation (PDF)

**Realistic timeline**: 6 months solo, 3 months with a 2-person team

**Valuation potential**: If you get 100 paying companies at $500/month average → $50,000 MRR → $600,000 ARR → $3–6M valuation at typical SaaS multiples

---

#### Product 5 — AI Code Review with Security Context

**What it is**: An MCP tool that AI coding agents (Claude Code, Cursor, Copilot) call before generating code to:
1. Check if the pattern being generated is vulnerable
2. Suggest the secure alternative automatically

**Example**:
```
Developer to AI: "Write a method to find a user by their email"

AI internally calls: impact-index.check_pattern("sql_query_with_string_concat")
→ returns: "Pattern matches SQL injection risk. Use PreparedStatement instead."

AI generates:
  ❌ NOT this: "SELECT * FROM users WHERE email = " + email
  ✅ But this:  "SELECT * FROM users WHERE email = ?" with PreparedStatement
```

**Business model**: Per-seat API access, integrated into AI coding assistant subscription
- Sell to AI coding assistant companies (Cursor, Copilot, etc.) as a B2B integration
- Or sell directly to enterprises as an "AI safety layer"

**Why this is the future**: As AI writes more code, security scanning of AI-generated code becomes critical. You're building the tool that sits between the AI and the codebase.

---

#### Product 6 — Compliance Reporting Tool (PCI-DSS, SOC2, HIPAA)

**What it is**: Regulated industries (banking, healthcare) must prove their code has no known vulnerabilities to pass audits. Your tool generates audit-ready PDF reports.

**The security team at a bank pays $300,000/year for Checkmarx**. You could offer the same capability for $30,000/year with a generated compliance report that maps findings to specific regulatory requirements.

**Business model**: Annual enterprise contracts, $15,000–$100,000/year per organization

**Why this works**: Compliance reports are required by law in many industries. Companies will pay for tooling that makes passing audits easier.

---

### 🔴 Tier 3 — Requires Significant Funding / Expertise

#### Product 7 — Bug Bounty Intelligence Platform

**What it is**: Security researchers use your tool to automatically scan open-source projects and find vulnerabilities, then submit to bug bounty programs.

**The math**: A single critical CVE in a popular library can pay $10,000–$50,000 in bug bounties. If your tool can find even 2–3 novel vulnerabilities per month in popular Java libraries, that's $20,000–$150,000/month.

**Why it's Tier 3**: 
- Requires near-zero false positives (you waste security researcher time if findings are wrong)
- Needs full data flow analysis for meaningful novel vulnerability discovery
- Legal/ethical complexity around vulnerability disclosure

**Realistic path**: Build this after Phase 4 (full taint analysis). Use as a research tool first, then commercialize.

---

#### Product 8 — Supply Chain Security (SBOM Generation + Monitoring)

**What it is**: Generate a **Software Bill of Materials (SBOM)** — a complete list of all components in your software — and continuously monitor it for new CVEs.

SBOM is now required by:
- US Executive Order 14028 (federal software)
- EU Cyber Resilience Act (all software sold in EU)
- Many government contracts

**Business model**: 
- SBOM generation: $500 one-time per project
- Continuous monitoring: $200/month per project
- Enterprise: $50,000/year for unlimited projects

**Market**: Every software company selling to US government or in EU needs this. It's legally mandated. Not optional.

**Why this is Tier 3**: Regulatory compliance tools require significant trust and certification. Takes time to establish credibility.

---

## Part 5 — Honest Recommendation (Which to Actually Build)

As a final year B.Tech student about to graduate, here's my frank advice on priority order:

### Right Now (While Still a Student)
1. **Ship Phase 1 of the core tool** (impact index + query + lint) — without this, nothing else is possible
2. **Add the SCA layer** (dependency CVE scanning) — this is the lowest effort addition and already makes the tool significantly more useful
3. **Add reachability** — this is your unique value proposition and nobody else gives it away free

### After Graduation (First Job)
4. Run the tool on a real old Java codebase and write a **detailed case study** — "I found 3 critical CVEs + 2 SQL injection paths in a 150k line legacy project"
5. This case study becomes your portfolio. Use it to:
   - Get a job at a security-focused company (Snyk, Checkmarx, etc.) — they will hire you
   - Get freelance consulting work from companies needing security audits

### Within 2 Years
6. Build **Product 1** (open source scanner) and get community traction on GitHub
7. Build **Product 2** (GitHub Action) — viral distribution, free marketing
8. If demand is proven, raise a small round and build **Product 4** (full SaaS)

---

## The Numbers at a Glance

| Product | Time to Build | Monthly Revenue Potential | Market |
|---|---|---|---|
| Open Source Scanner (free) | Already in our roadmap | Reputation + job offers | Global developers |
| Paid Pro scanner | +1 month | $500–$5,000/month (early) | SMB developers |
| GitHub Action | +2 weeks | $1,000–$20,000/month | DevOps teams |
| Legacy audit consulting | 0 extra code needed | ₹2–10L per client | Indian enterprise |
| SaaS platform | 6 months | $50,000+/month (at scale) | Global |
| Compliance reporting | 3 months | $15,000–$100,000/year/client | Regulated industries |

---

## What to Add to the Project Roadmap

Based on this analysis, here's what should be formally added to `requirements.md`:

### New Phase: Phase 2.5 — Security Intelligence Layer

**Between Phase 2 (Human Docs) and Phase 3 (Incremental Updates)**:

1. `impact-index cve` — dependency CVE scanning via OSV/NVD API
2. Reachability check — cross-reference vulnerable functions with our call graph
3. `impact-index sast` — top 5 SAST rules (SQL injection, command injection, XSS, path traversal, hardcoded secrets)
4. `impact-index scan` — combined report (SCA + SAST + reachability)
5. `vulnerabilities` and `sast_findings` tables in SQLite schema

**What this unlocks**:
- The tool goes from "useful for AI agents" to "useful for security teams"
- Every AI agent running in your project now knows which methods are vulnerable before editing them
- You have a compelling story for the job market and potential commercial opportunities

---

*Saved to: `docs/security-layer-and-commercial-opportunities.md`*
