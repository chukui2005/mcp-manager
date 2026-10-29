# MCP Governance (mcp-manager)

> **English** | [简体中文](README.zh-CN.md)

**The Governance and Audit Layer for Model Context Protocol (MCP), Java Native, Scan-First.**

[![Build](https://img.shields.io/badge/build-passing-brightgreen)](#)
[![Java 17](https://img.shields.io/badge/Java-17-orange.svg)](#)
[![SARIF 2.1.0](https://img.shields.io/badge/SARIF-2.1.0-blue.svg)](https://docs.oasis-open.org/sarif/sarif/v2.1.0/sarif-v2.1.0.html)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

---

## 1. What is MCP Governance?

As enterprise AI agents evolve, developers and organizations connect dozens of MCP Servers, hundreds of external tools, and heterogeneous models. However, managing MCP security lacks visibility, security audits, and automated policy enforcement.

**MCP Governance (`mcp-manager`)** is a lightweight, pure Java-based static audit and governance layer. It detects token leakage, broad scope permissions, hidden prompt injections, and tool shadowing attacks in MCP configuration files before deployment, and seamlessly reports findings via standard **OASIS SARIF 2.1.0** into GitHub Code Scanning.

---

## 2. Terminal High-Fidelity Preview

When running `mcp-governance-cli` against an MCP configuration, findings are rendered in high-contrast ANSI Markdown cards with strict exit code enforcement:

```text
================================================================================
                    MCP GOVERNANCE STATIC SCAN REPORT                           
================================================================================
Target: samples/vulnerable-mcp.json
Total Findings: 4 (CRITICAL: 1, HIGH: 2, MEDIUM: 1, LOW: 0)

[CRITICAL] MCP01-001 (Category: MCP01 Token Management)
  Rule: Plaintext Token Exposure Scan
  Target: github-server -> env.GITHUB_PERSONAL_ACCESS_TOKEN
  Message: Detected plaintext credentials/token in environment variable: ghp_****
  Remediation: Store secrets in system environment variables or secure vaults.

[HIGH] MCP03-001 (Category: MCP03 Tool Poisoning)
  Rule: Prompt Injection in Tool Description
  Target: github-server -> read_secure_file
  Message: Tool description contains prompt injection keywords ('ignore all previous instructions').
  Remediation: Sanitize tool description and strip instructions attempting to override system behavior.

[HIGH] MCP03-002 (Category: MCP03 Tool Poisoning)
  Rule: Tool Shadowing Collision Scan
  Target: Tool 'read_secure_file' defined in multiple servers: [github-server, local-server]
  Message: Conflicting tool names allow shadowing or hijacking during model invocation.
  Remediation: Assign unique namespaces or rename conflicting tool declarations.

[MEDIUM] MCP02-001 (Category: MCP02 Overprivileged Scope)
  Rule: Broad Scope Pattern Scan
  Target: github-server -> read_secure_file -> wildcard_action
  Message: Argument schema defines overly broad wildcard regex: '.*'
  Remediation: Constrain arguments to strict enumerations or narrowed formats.
================================================================================
Status: BLOCKED (Found 1 CRITICAL, 2 HIGH violations) -> Exit Code: 2
```

---

## 3. Comparison with Alternatives

| Feature / Dimension | Wanaku | AI MCP Bridge | mocapi | **MCP Governance (`mcp-manager`)** |
|---|---|---|---|---|
| **Static Configuration Scan** | ❌ None | ❌ None | ⚠️ Basic Schema Only | ✅ **Full Engine (`mcp.json` / Claude Desktop)** |
| **OWASP MCP Top 10 Mapping** | ❌ None | ❌ None | ❌ None | ✅ **MCP01, MCP02, MCP03 Rules Built-in** |
| **SARIF 2.1.0 Export** | ❌ None | ❌ None | ❌ None | ✅ **Native OASIS SARIF Export** |
| **CI / GitHub Action Gate** | ❌ None | ❌ None | ❌ None | ✅ **Composite Action with CodeQL Upload** |
| **Zero Spring Dependency Core** | ❌ N/A | ❌ N/A | ❌ N/A | ✅ **Pure Java 17 Domain Core** |
| **Runtime Audit & Starter** | ❌ Router | ❌ Bridge | ❌ Mocking | 🔄 **Phase 2 (Spring Boot Starter)** |
| **Standalone Gateway** | ✅ Active | ⚠️ Proxy | ❌ None | 🔄 **Phase 3 (Enterprise Gateway)** |

---

## 4. Quick Start (Under 30 Seconds)

### Option 1: Standalone CLI (Fat-JAR)

Build the fat-jar and run a static scan against any MCP configuration:

```bash
# Package all modules into an executable fat-jar
mvn clean package -DskipTests

# Scan an MCP configuration file and export SARIF
java -jar mcp-governance-cli/target/mcp-governance-cli-0.1.0-SNAPSHOT-all.jar \
  mcp.json \
  --output scan-result.sarif
```

### Option 2: GitHub Action in CI (5 Lines of YAML)

Add MCP static security scanning directly to `.github/workflows/ci.yml`:

```yaml
- name: Audit MCP Configuration
  uses: chukui2005/mcp-manager@v0.1.0
  with:
    target: 'mcp.json'
    upload-sarif: 'true'
    fail-on-findings: 'true'
```

---

## 5. System Architecture

```text
               +-------------------------------------------+
               |   MCP Config (mcp.json / Claude Desktop)  |
               +-------------------------------------------+
                                     |
                                     v
               +-------------------------------------------+
               |         mcp-governance-scanner            |
               |        (McpConfigParser - Static)         |
               +-------------------------------------------+
                                     |
                        GovernedTool & ScanContext
                                     |
                                     v
               +-------------------------------------------+
               |        mcp-governance-rules-owasp         |
               |   SPI: ScanRule (MCP01 / MCP02 / MCP03)   |
               +-------------------------------------------+
                                     |
                               List<Finding>
                                     |
                   +-----------------+-----------------+
                   |                                   |
                   v                                   v
+------------------------------------+   +------------------------------------+
|        mcp-governance-cli          |   |          SarifSerializer           |
| (ANSI Terminal & Exit Code 2 Gate) |   |  (OASIS SARIF 2.1.0 for GitHub UI) |
+------------------------------------+   +------------------------------------+
```

---

## 6. Built-in Security Rules (Phase 1)

| Rule ID | OWASP Category | Severity | Description | Remediation |
|---|---|---|---|---|
| **`MCP01-001`** | MCP01 Token Management | `CRITICAL` | Detects hardcoded API keys, JWTs, and tokens in environment variables (`ghp_`, `sk-`, `Bearer `). | Reference secrets from system environment variables or secure vault stores. |
| **`MCP02-001`** | MCP02 Overprivileged Scope | `MEDIUM` | Detects overly permissive wildcard regular expressions (`.*`, `.+`) in tool parameters. | Narrow input parameter patterns and restrict with explicit enums. |
| **`MCP03-001`** | MCP03 Tool Poisoning | `HIGH` | Detects prompt injection keywords in tool descriptions designed to hijack model system instructions. | Strip system override directives and sanitize tool descriptions. |
| **`MCP03-002`** | MCP03 Tool Poisoning | `HIGH` | Detects conflicting identical tool names across multiple servers (tool shadowing). | Rename conflicting tools or isolate them into dedicated server namespaces. |

---

## 7. Roadmap

- **Phase 1: Static Scanning & Governance CLI (Completed)**
  - [x] Pure Java 17 core domain model (`GovernedTool`, `McpConfig`, zero Spring dependencies)
  - [x] Static AST parser for standard `mcp.json` and Claude Desktop configuration formats
  - [x] OWASP MCP Top 10 rules SPI (`MCP01-001`, `MCP02-001`, `MCP03-001`, `MCP03-002`)
  - [x] OASIS SARIF 2.1.0 serializer and JSON schema verification
  - [x] CLI executable fat-jar with ANSI terminal reporting and exit code gates
  - [x] GitHub Composite Action and self-hosted bootstrap scan pipeline
- **Phase 2: Runtime Governance & Spring Boot Starter (Upcoming)**
  - [ ] `mcp-governance-spring-boot-starter` non-invasive client/server interception
  - [ ] Runtime context desensitization and prompt filtering
  - [ ] Comprehensive audit logging and OpenTelemetry metrics
- **Phase 3: Standalone Gateway & Policy Replay (Future)**
  - [ ] Independent MCP proxy/gateway with OAuth 2.1 authentication
  - [ ] Dynamic policy replay and shadow execution sandbox

---

## 8. Contributing & Security

- Contributions are welcome! Please refer to [CONTRIBUTING.md](CONTRIBUTING.md) for details on code style, testing, and pull requests.
- To report security vulnerabilities, please consult [SECURITY.md](SECURITY.md).
