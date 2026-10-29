# 0000. Dry-Run Workflow and Phase 1 Architecture Baseline

* **Status**: Proposed
* **Date**: 2026-10-29
* **Deciders**: Architect AI, Human Lead
* **Consulted**: AGENTS.md, Environment Baseline

---

## Context

As the `mcp-manager` project kicks off, we face two foundational architectural challenges:

1. **Multi-Agent Coordination & Safety Verification**:
   The project operates under a strictly partitioned multi-agent governance charter (`AGENTS.md`). In complex agentic workflows, lack of explicit tool boundaries and informal handoffs frequently cause scope creep, unauthorized file edits, and unvetted merges. Before implementing production Java modules for Phase 1, an end-to-end "Dry-Run" (TASK-000) is required to test and validate:
   - Tool permissions and role sandboxing (Architect read-only, Engineer isolated worktree, Reviewer read-only findings, Human final decision).
   - Strict state machine transitions and handover gates.
   - Machine-verifiable human sign-off mechanics.

2. **Phase 1 Technical Scope & Security Posture**:
   The objective of Phase 1 is delivering a lightweight, embeddable, Java-native static scanning CLI (`mcp-governance-cli`) and GitHub Action (`mcp-governance-action`) capable of evaluating Model Context Protocol (MCP) server configurations and schemas against the OWASP MCP Top 10.
   - **Untrusted Execution Risk (MCP05)**: MCP configurations frequently define STDIO-based servers invoking local commands and shell binaries. Spawning arbitrary sub-processes during developer builds or CI pipeline scans presents severe remote code execution (RCE) and local exploitation vectors.
   - **External Type Leakage**: Upstream client libraries (e.g., the official MCP Java SDK) use raw transport representations (such as `McpSchema.Tool` where `inputSchema` is a raw `Map<String, Object>`). Leaking SDK-specific wire structures across the governance domain threatens long-term modularity and breaks clean architecture boundaries.
   - **Standard Reporting Format**: Scanner results must integrate seamlessly with enterprise devsecops pipelines and GitHub Code Scanning without proprietary lock-in.

---

## Decision

We establish the following architectural policies, state transition protocols, and domain boundary standards:

### 1. Multi-Agent Role Separation and State Machine

The task lifecycle follows a mandatory 5-stage deterministic state machine tracked in `.worklog/<task-id>.md`:

```
DESIGN ──► READY ──► IN_REVIEW ──► READY_TO_LAND ──► DONE
```

Role-based access boundaries are strictly enforced:
* **Architect**: Read-only (`Read`, `Grep`, `Glob`, `WebFetch`). Responsible for blueprints (`docs/architecture/`), ADRs (`docs/adr/`), and contract definitions (`docs/contracts/`). Forbidden from modifying implementation code, executing builds, or approving merges.
* **Engineer**: Scoped to isolated branch/worktree with code modification and test execution permissions. Forbidden from pushing directly to `main`, forbidden from modifying `docs/adr/` and `docs/contracts/`, and forbidden from self-reviewing.
* **Reviewer**: Read-only code and git inspection. Audits against ADRs, contracts, code style, and security invariants. Emits structured findings and a verdict (`PASS` / `FAIL`). Forbidden from modifying code, applying patches, or merging PRs.
* **Human Decider**: Sole authority for phase progressions, ADR approvals, and merging PRs into `main`.

### 2. Machine-Verifiable Human Approval Gate

* The state transition `DESIGN → READY` **strictly requires** explicit human approval.
* Verbal agreement in chat or PR comments does **not** constitute approval.
* Approval must be recorded in the task's worklog document (`.worklog/<task-id>.md`) as an exact, standalone line:
  ```yaml
  Approved: Yes
  ```
* Automated gates and scripts must parse this exact token before allowing the task to transition to `READY`.

### 3. Wire Model vs. Control Plane Model Separation

To maintain architectural purity and shield `mcp-governance-core` from upstream SDK churn:
* **Wire Models**: SDK data types (e.g., `McpSchema.Tool`, raw `Map<String, Object>` payloads) are strictly confined to transport adapters and parsing layers (`mcp-governance-scanner`). They must **never** be exposed in the public API of `mcp-governance-core`.
* **Control Plane Models**: The core domain operates exclusively on strongly-typed governance entities (e.g., `GovernedTool`, `GovernedResource`, `ScanRule`).
* **Integrity & Schema Guarantees**:
  - `inputSchema` in the governance domain must be validated against JSON Schema 2020-12 specifications.
  - Every governed entity must compute and retain an immutable SHA-256 digest of its canonicalized definition for tamper detection and revision tracking.
  - Validation runs against instantiated, immutable domain records, bypassing permissive serialization defaults.

### 4. Zero-Trust Static Scan & Safe Execution Policy

* **Static Scan by Default**: The scanner evaluates configuration files (`mcp.json`, Claude desktop configs, environment definitions) and tool schemas strictly through static parsing and pattern analysis.
* **STDIO Execution Quarantine**: The scanner **must not** spawn or execute local STDIO commands by default.
* **Explicit Execution Opt-In**: Dynamic capability discovery (spawning a server process to query `tools/list`) is permitted only when explicitly invoked with the `--allow-execute` CLI flag and must generate an immediate audit warning log.

### 5. Standardized SARIF 2.1.0 Output Contract

* All scan reports must emit compliant OASIS Static Analysis Results Interchange Format (SARIF) version 2.1.0 JSON.
* Every finding must map to an OWASP MCP Top 10 category (e.g., `MCP01` for secret exposure, `MCP02` for scope creep, `MCP03` for tool poisoning, `MCP05` for command injection).
* Findings must report severity, rule metadata, file locations, and actionable remediation text without ever leaking secret values or plaintext credentials.

---

## Consequences

### Positive

* **Guaranteed Supply Chain & Merge Safety**: Eliminates the risk of rogue AI commits landing on `main` or agents bypassing human review gates.
* **High Modularity & Framework Independence**: Keeping `mcp-governance-core` pure Java 17 LTS (free of Spring and external framework dependencies) ensures minimal footprint and rapid CLI startup via `picocli`.
* **Zero Command Injection Risk in CI**: Refusing to execute STDIO processes by default guarantees that running `mcp-governance-cli` against untrusted third-party repositories cannot execute malicious payloads.
* **Turnkey DevSecOps Integration**: Emitting standard SARIF 2.1.0 enables native integration with GitHub Code Scanning (`github/codeql-action/upload-sarif@v3`) and enterprise SIEM/SARIF consumers.

### Negative / Trade-offs

* **Process Overhead**: Tasks cannot proceed autonomously from design to implementation without waiting for human sign-off (`Approved: Yes`).
* **Inspection Limitations**: Pure static analysis cannot inspect dynamic tools exposed by servers whose tool lists are calculated at runtime, unless `--allow-execute` is deliberately granted or static schema manifests are provided.
* **Model Mapping Maintenance**: Maintaining dedicated `Governed*` domain models separate from the MCP Java SDK requires explicit mapping and translation layers.

---

## Compliance & Verification Checkpoints

### For Reviewer

1. **Role Boundary Enforcement**:
   - Verify that Engineer PRs contain **no modifications** to `docs/adr/` or `docs/contracts/`.
   - Verify that no PR merges directly to `main`.
2. **Dependency & Layering Invariants**:
   - Verify `mcp-governance-core` contains zero dependencies on Spring Framework.
   - Verify that no MCP SDK wire types (e.g., `io.modelcontextprotocol.spec.McpSchema`) leak into `mcp-governance-core` public interfaces.
3. **Execution Safety**:
   - Inspect CLI argument parsing to verify that sub-process execution paths for STDIO servers are unreachable unless `--allow-execute` is true.
4. **Secret Sanitization**:
   - Verify that log outputs, exception messages, and SARIF property bags redact all tokens, keys, and credentials.

### For CI Pipeline

1. **State Machine Verification**:
   - Verify that `.worklog/<task-id>.md` contains `Approved: Yes` before build verification completes.
2. **Build and Quality**:
   - `mvn -B verify` must pass with 100% test success.
   - `mvn javadoc:javadoc` must complete with zero warnings.
3. **Self-Scanning (SARIF Bootstrap)**:
   - The scanner must run against test configurations and dogfood against the repository; any `CRITICAL` finding blocks PR merge.
   - Generated SARIF files must validate against the official SARIF 2.1.0 JSON Schema.
