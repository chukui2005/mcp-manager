# 0001. Maven Multi-Module Architecture and Domain Boundary Baseline

* **Status**: Proposed
* **Date**: 2026-10-29
* **Deciders**: Architect AI, Human Lead
* **Consulted**: AGENTS.md, .agents/architect.md, docs/adr/0000-dry-run-workflow.md

---

## Context

Phase 1 of `mcp-manager` focuses on delivering an embeddable, lightweight, Java-native static scanner CLI (`mcp-governance-cli`) and GitHub Action for inspecting Model Context Protocol (MCP) server manifests (`mcp.json`, Claude desktop configs, tool definitions) against OWASP MCP Top 10 risks.

To achieve long-term modularity, security, and extensibility across subsequent phases (Phase 2: Spring Boot Starter, Phase 3: Governance Gateway), we must establish a rock-solid multi-module Maven structure with strict dependency and domain boundaries:

1. **Spring-Free Core Domain**:
   If the core domain model and rule interfaces depend on Spring Framework, the CLI and any lightweight embedding agent would carry 30MB+ of transitive runtime dependencies, degrade cold-start times, and prevent embedding in non-Spring environments.
2. **Wire Model vs. Control Plane Isolation**:
   MCP Java SDK 2.0 defines wire-level data transfer records (e.g., `McpSchema.Tool` with `Map<String, Object> inputSchema`). Wire protocols deserialize loosely and evolve independently. Leaking raw wire maps or external SDK classes directly into core governance abstractions compromises validation invariants and binds governance rules to wire serialization formats.
3. **Safe Execution Boundary (MCP05)**:
   Many MCP server configurations define local STDIO execution commands. A static scanner running in CI/CD or local developer workstations must never execute untrusted local shell processes without explicit opt-in (`--allow-execute`).
4. **Interoperable Reporting**:
   Security findings must be structured and exported strictly under the OASIS SARIF 2.1.0 specification for GitHub Code Scanning and enterprise SIEM ingest.

---

## Decision

We establish a 4-submodule Maven multi-module architecture rooted at `io.github.chukui2005:mcp-manager`:

### 1. Module Hierarchy and Directed Acyclic Graph (DAG)

```
mcp-manager (Root Aggregator & Parent POM)
 │
 ├── mcp-governance-core
 │    └── (Pure Java 17 Domain, Zero Framework Dependencies, SPIs)
 │
 ├── mcp-governance-scanner
 │    ├── Depends on: mcp-governance-core
 │    └── (Static Parser, AST/Config Evaluator, SARIF 2.1.0 Exporter)
 │
 ├── mcp-governance-rules-owasp
 │    ├── Depends on: mcp-governance-core
 │    └── (ScanRule SPI implementations: MCP01, MCP02, MCP03)
 │
 └── mcp-governance-cli
      ├── Depends on: mcp-governance-scanner, mcp-governance-rules-owasp
      └── (Picocli Executable, Zero STDIO execution by default, Exit codes)
```

**Dependency Rules (Strict Invariants)**:
- `mcp-governance-core` MUST NOT depend on Spring, Picocli, or any module in this repository.
- `mcp-governance-scanner` MUST NOT depend on `mcp-governance-cli`. CLI is purely an outer entry shell.
- `mcp-governance-rules-owasp` depends ONLY on `mcp-governance-core` (plus test dependencies). Rules remain decoupled from scanning engines and CLI drivers.
- `mcp-governance-cli` aggregates scanner and rule sets into a deployable distribution.

### 2. Technology & Version Baseline

- **Java Version**: Java 17 LTS (configured in `maven.compiler.release`).
- **Build System**: Apache Maven 3.9+.
- **JSON & Schema Parsing**: Jackson 2.17.2 (`jackson-databind`, `jackson-core`, `jackson-annotations`).
- **CLI Framework**: Picocli 4.7.6 (`info.picocli:picocli`, annotation processor `picocli-codegen`).
- **Logging Facade**: SLF4J 2.0.13 (`slf4j-api`). Logback 1.5.6 is scoped strictly to `mcp-governance-cli` and test scopes.
- **Testing**: JUnit Jupiter 5.10.3 and AssertJ Core 3.26.3 across all test scopes.
- **JavaDoc**: `maven-javadoc-plugin` with `<failOnWarnings>true</failOnWarnings>`.

### 3. Model Separation (`GovernedTool` & Control Plane)

- Control plane models reside exclusively in `io.github.chukui2005.mcpgov.core.model`.
- `GovernedTool` encapsulates:
  - Canonical `name` and `description`.
  - Canonical JSON Schema 2020-12 representation of `inputSchema` (validated upon construction; never null).
  - Computed SHA-256 integrity hash across the canonical representation of the tool.
  - Risk classification metadata (`Set<RiskTag>`, `RiskLevel`).
- Wire models (raw JSON from `mcp.json` or transport envelopes) are parsed inside `mcp-governance-scanner` and converted into `GovernedTool` and `GovernedServerConfig` before passing into rule evaluation.

### 4. Zero-Trust Execution Policy

- By default, `mcp-governance-scanner` inspects server configurations solely through static JSON/YAML deserialization and text analysis.
- The scanner MUST NOT spawn child processes or execute `command` fields found in STDIO server definitions.
- Dynamic tool discovery via STDIO child process execution is gated strictly behind an explicit CLI switch (`--allow-execute`) and must emit a high-visibility warning log.

---

## Consequences

### Positive
- **Ultra-lightweight & Fast Startup**: `mcp-governance-core` and `mcp-governance-scanner` remain pure Java with minimal runtime footprint.
- **Future-Proof Reusability**: When Phase 2 introduces `mcp-governance-spring-boot-starter`, it will consume `mcp-governance-core` and `mcp-governance-scanner` seamlessly without dependency conflicts.
- **Enforced Security by Default**: Zero accidental RCE vulnerability during automated CI scans of untrusted repositories.
- **Standardized CI Integration**: SARIF 2.1.0 output allows immediate upload via `github/codeql-action/upload-sarif@v3`.

### Negative / Trade-offs
- **Mapping Overhead**: Requires explicit mapper classes in `scanner` to translate raw configuration structures into immutable domain records.
- **Limited Runtime Discovery**: Without `--allow-execute`, servers whose tools are generated dynamically at runtime cannot be discovered from static config files alone unless an export schema file is provided.

---

## Compliance & Verification Checkpoints

1. **Dependency Analysis**: `mvn dependency:analyze` and `mvn dependency:tree` must verify zero Spring artifacts in `core`, `scanner`, `rules-owasp`, and `cli`.
2. **Javadoc Quality**: `mvn javadoc:javadoc` must pass with zero warnings across all modules.
3. **SARIF Validation**: The output of `mcp-governance-cli` must validate against `docs/contracts/scan-result.schema.json`.
