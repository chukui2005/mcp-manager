# MCP Governance (mcp-manager)

> **English** | [简体中文](README.zh-CN.md)

**The Governance, Security Audit, and Gateway Infrastructure for Model Context Protocol (MCP), Java Native, Scan-First.**

[![Build](https://img.shields.io/badge/build-passing-brightgreen)](#)
[![Java 17](https://img.shields.io/badge/Java-17-orange.svg)](#)
[![Spring Boot 3.3](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](#)
[![SARIF 2.1.0](https://img.shields.io/badge/SARIF-2.1.0-blue.svg)](https://docs.oasis-open.org/sarif/sarif/v2.1.0/sarif-v2.1.0.html)
[![Release](https://img.shields.io/badge/release-v0.3.0--gateway-blue.svg)](#)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

---

## 1. What is MCP Governance?

As enterprise AI agents evolve, developers and organizations connect dozens of MCP Servers, hundreds of external tools, and heterogeneous models. However, managing MCP security lacks visibility, security audits, and automated policy enforcement.

**MCP Governance (`mcp-manager`)** provides progressive, enterprise-grade governance infrastructure:
1. **Offline Static Scanner (Phase 1)**: Pure Java static analysis CLI and GitHub Action that detects token leakage, broad scope permissions, hidden prompt injections, and tool shadowing attacks in MCP configuration files, reporting findings via standard **OASIS SARIF 2.1.0** into GitHub Code Scanning.
2. **Embedded Runtime Governance (Phase 2)**: Non-intrusive Spring Boot Starter providing pure Java interceptor pipelines, Tool ACL (whitelisting/blacklisting), regex parameter validation, Prometheus/Micrometer metrics (`/actuator/mcpGovernance`), and SIEM-ready structured JSON audit logs (`McpAuditEvent`).
3. **Standalone Control Plane Gateway (Phase 3)**: Independent Spring Boot Reactive WebFlux gateway aggregating multi-protocol upstreams (HTTP, SSE, and managed local STDIO sandboxes) with prefix-based namespace collision avoidance, tenant-level tool ACL filtering, connection pooling, and full governance pipeline enforcement.

---

## 2. Terminal High-Fidelity Preview

When running `mcp-governance-cli` against an MCP configuration, findings are rendered in high-contrast ANSI Markdown cards with strict exit code enforcement:

```text
================================================================================
                    MCP GOVERNANCE STATIC SCAN REPORT                           
================================================================================
Target: samples/vulnerable-mcp.json
Total Findings: 4 (CRITICAL: 2, HIGH: 2, MEDIUM: 0, LOW: 0)

[CRITICAL] MCP01-001 (Category: MCP01 Token Management)
  Rule: Plaintext Token Exposure Scan
  Target: github-server -> env.auth_token
  Message: Detected plaintext credentials/token in environment variable: ghp_****
  Remediation: Store secrets in system environment variables or secure vaults.

[HIGH] MCP02-001 (Category: MCP02 Overprivileged Scope)
  Rule: Broad Scope Pattern Scan
  Target: github-server -> read_secure_file -> wildcard_action
  Message: Argument schema defines overly broad wildcard regex: '.*'
  Remediation: Constrain arguments to strict enumerations or narrowed formats.

[CRITICAL] MCP03-001 (Category: MCP03 Tool Poisoning)
  Rule: Prompt Injection in Tool Description
  Target: github-server -> read_secure_file
  Message: Tool description contains prompt injection keywords ('Ignore all previous instructions').
  Remediation: Sanitize tool description and strip instructions attempting to override system behavior.

[HIGH] MCP03-002 (Category: MCP03 Tool Poisoning)
  Rule: Tool Shadowing Collision Scan
  Target: Tool 'read_secure_file' defined in multiple servers: [github-server, local-server]
  Message: Conflicting tool names allow shadowing or hijacking during model invocation.
  Remediation: Assign unique namespaces or rename conflicting tool declarations.
================================================================================
Status: BLOCKED (Found 2 CRITICAL, 2 HIGH violations) -> Exit Code: 2
```

---

## 3. Comparison with Alternatives

| Feature / Dimension | Wanaku | AI MCP Bridge | mocapi | **MCP Governance (`mcp-manager`)** |
|---|---|---|---|---|
| **Static Configuration Scan** | ❌ None | ❌ None | ⚠️ Basic Schema Only | ✅ **Full Engine (`mcp.json` / Claude Desktop)** |
| **OWASP MCP Top 10 Mapping** | ❌ None | ❌ None | ❌ None | ✅ **Built-in MCP01, MCP02, MCP03 Rules** |
| **SARIF 2.1.0 Export** | ❌ None | ❌ None | ❌ None | ✅ **Native OASIS SARIF 2.1.0 Export** |
| **CI / GitHub Action Gate** | ❌ None | ❌ None | ❌ None | ✅ **Composite Action with CodeQL Upload** |
| **Zero Spring Dependency Core** | ❌ N/A | ❌ N/A | ❌ N/A | ✅ **Pure Java 17 Domain Core** |
| **Runtime Audit & Starter** | ❌ Router | ❌ Bridge | ❌ Mocking | ✅ **Full Spring Boot Starter (Phase 2)** |
| **Tool ACL & Regex Validation** | ⚠️ Partial | ❌ None | ❌ None | ✅ **Whitelist/Blacklist & Param Guard** |
| **Metrics & Actuator Telemetry** | ❌ None | ❌ None | ❌ None | ✅ **Micrometer & `/actuator/mcpGovernance`** |
| **Standalone Gateway** | ✅ Active | ⚠️ Proxy | ❌ None | ✅ **Multi-protocol Reactive Gateway (Phase 3)** |
| **Managed STDIO Sandbox** | ⚠️ Unsandboxed | ❌ None | ❌ None | ✅ **Env Filter & 2-Stage Termination** |

---

## 4. Quick Start (Under 30 Seconds)

### Option 1: Standalone Scanner CLI (Fat-JAR)

Build the fat-jar and run an instant static security scan against any MCP configuration:

```bash
# Package all modules into an executable fat-jar
mvn clean package -DskipTests

# Scan an MCP configuration file and export SARIF
java -jar mcp-governance-cli/target/mcp-governance-cli-0.1.0-SNAPSHOT-all.jar \
  samples/vulnerable-mcp.json \
  --output scan-result.sarif
```

### Option 2: Embedded Spring Boot Starter (Zero-Intrusion)

Add runtime governance to your existing Java application:

```xml
<dependency>
    <groupId>io.github.chukui2005</groupId>
    <artifactId>mcp-governance-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Configure declarative security policies in `application.yml`:

```yaml
mcp:
  governance:
    enabled: true
    logging:
      enabled: true
      mask-sensitive-args: true       # Automatic secret masking (***)
    acl:
      enabled: true
      default-mode: WHITELIST         # Strict default-deny
      allowed-tools:
        - "calculator"
        - "file_read_*"               # Wildcard pattern support
    validation:
      enabled: true
      rules:
        - tool-name-pattern: "file_*"
          parameter-name: "path"
          regex-pattern: "^/safe/.*"  # Anti-path traversal regex
```

### Option 3: Standalone Control Plane Gateway

Aggregate distributed upstreams (HTTP, SSE, STDIO) into a unified secure gateway endpoint:

1. Define `gateway-routes.json`:
```json
{
  "gateway": { "host": "127.0.0.1", "port": 8080 },
  "upstreams": [
    { "id": "k8s-svc", "protocol": "http", "prefix": "k8s_", "uri": "http://10.0.0.1:9091/mcp" },
    { "id": "fs-local", "protocol": "stdio", "prefix": "fs_", "stdio": { "command": "node", "args": ["server.js"] } }
  ]
}
```

2. Start the gateway:
```bash
java -jar mcp-governance-gateway/target/mcp-governance-gateway-0.1.0-SNAPSHOT.jar
```

3. Connect your AI Agents (Cursor, Claude Desktop, LangChain) directly to `http://127.0.0.1:8080/mcp/v1/rpc`.

### Option 4: GitHub Action in CI (5 Lines of YAML)

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
               +-------------------------------------------------------------+
               |          AI Agents (Claude Desktop, Cursor, LangChain)      |
               +-------------------------------------------------------------+
                                      |
                                      | Streamable HTTP / JSON-RPC 2.0
                                      v
+===========================================================================================+
|                     mcp-governance-gateway (Standalone Control Plane)                     |
|                                                                                           |
|  [ Ingress Handlers ]                                                                     |
|    - POST /mcp/v1/rpc (Streamable HTTP & Ping / Initialized Lifecycle Support)            |
|    - GET  /mcp/v1/sse (Server-Sent Events Session Stream)                                 |
|                                                                                           |
|  [ Namespace & Route Aggregation ]                                                        |
|    - Longest Prefix Routing & Namespace Isolation (e.g. k8s_get_pods -> get_pods)         |
|    - Tenant-level Tool ACL Dynamic Filtering in tools/list                                |
|                                                                                           |
|  [ Core Governance Pipeline (100% Shared with Spring Boot Starter) ]                      |
|    Order -100: MetricsMcpInterceptor        (Invocation count, latency & success rate)    |
|    Order  -50: AuditMcpInterceptor          (Sensitive argument masking & SIEM JSON event)|
|    Order  100: ToolAclMcpInterceptor        (Whitelist/Blacklist enforcement)             |
|    Order  200: ParameterValidationInterceptor (Regex guard against injection/traversal)   |
|                                                                                           |
|  [ Egress Client Connection Pools ]                                                       |
|    - HttpUpstreamClient  (Reactive WebClient with retry & token injection)                |
|    - SseUpstreamClient   (SSE streaming & auto-reconnect backoff)                         |
|    - StdioUpstreamClient (Managed local process pool & sensitive env-sanitization)        |
+===========================================================================================+
            |                             |                             |
            v                             v                             v
+-----------------------+     +-----------------------+     +-----------------------+
| Upstream HTTP Server  |     | Upstream SSE Server   |     | Managed STDIO Process |
+-----------------------+     +-----------------------+     +-----------------------+
```

---

## 6. Built-in Security Rules Reference

| Rule ID | OWASP Category | Severity | Description | Remediation |
|---|---|---|---|---|
| **`MCP01-001`** | MCP01 Token Management | `CRITICAL` | Detects hardcoded API keys, JWTs, and tokens in environment variables (`ghp_`, `sk-`, `Bearer `). | Reference secrets from system environment variables or secure vault stores. |
| **`MCP02-001`** | MCP02 Overprivileged Scope | `HIGH` | Detects overly permissive wildcard regular expressions (`.*`, `.+`) in tool parameters. | Narrow input parameter patterns and restrict with explicit enums. |
| **`MCP03-001`** | MCP03 Tool Poisoning | `CRITICAL` | Detects prompt injection keywords in tool descriptions designed to hijack model system instructions. | Strip system override directives and sanitize tool descriptions. |
| **`MCP03-002`** | MCP03 Tool Poisoning | `HIGH` | Detects conflicting identical tool names across multiple servers (tool shadowing). | Rename conflicting tools or isolate them into dedicated server namespaces. |

---

## 7. Roadmap & Milestones

- **Phase 1: Static Scanning & Governance CLI (Completed - v0.1.0-scan)**
  - [x] Pure Java 17 core domain model (`GovernedTool`, `McpConfig`, zero Spring dependencies)
  - [x] Static AST parser for standard `mcp.json` and Claude Desktop configuration formats
  - [x] OWASP MCP Top 10 rules SPI (`MCP01-001`, `MCP02-001`, `MCP03-001`, `MCP03-002`)
  - [x] OASIS SARIF 2.1.0 serializer and JSON schema verification
  - [x] CLI executable fat-jar with ANSI terminal reporting and exit code gates
  - [x] GitHub Composite Action and self-hosted bootstrap scan pipeline
- **Phase 2: Runtime Governance & Spring Boot Starter (Completed - v0.2.0-starter)**
  - [x] `mcp-governance-spring-boot-starter` conditional auto-configuration & zero-intrusion adapter
  - [x] Pure Java immutable context & interceptor chain architecture (`McpInterceptorChain`)
  - [x] Tool ACL access control (whitelist/blacklist & wildcard patterns, addressing OWASP MCP02)
  - [x] Parameter safety validation interceptor (anti-injection & path traversal, addressing OWASP MCP05)
  - [x] Runtime metrics telemetry & Spring Boot Actuator endpoint (`/actuator/mcpGovernance`, addressing OWASP MCP08)
  - [x] Immutable structured audit events (`McpAuditEvent`) & recursive parameter masking (`SensitiveDataMasker`, addressing OWASP MCP01)
  - [x] Spring native `@EventListener` & single-line compact JSON export (out-of-the-box SIEM/ELK integration)
- **Phase 3: Standalone MCP Governance Gateway (Completed - v0.3.0-gateway)**
  - [x] Standalone Gateway Architecture Decision (ADR 0008), Blueprint & Route Contract (`gateway-routes.schema.json`)
  - [x] `mcp-governance-gateway` standalone module with Spring Boot 3.3+ Reactive WebFlux & Netty (TASK-009)
  - [x] Dynamic route loading, `tools/list` namespace aggregation & tenant-level Tool ACL filtering (TASK-010)
  - [x] Outbound HTTP & SSE reverse proxy client pool (`UpstreamClientPool`) with secure token resolution (TASK-011)
  - [x] Managed local STDIO process pool (`ManagedStdioProcessPool`) with environment sandboxing & 2-stage shutdown (TASK-012)
  - [x] Comprehensive gateway governance pipeline integration, ping/notification lifecycle alignment & CORS security (TASK-013)

---

## 8. Contributing & Security

- Contributions are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) for code style, testing guidelines, and PR workflow.
- To report security vulnerabilities, please refer to [SECURITY.md](SECURITY.md).
