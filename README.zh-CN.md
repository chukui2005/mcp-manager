# MCP 治理与安全网关基础设施 (mcp-manager)

> [English](README.md) | **简体中文**

**面向 Model Context Protocol (MCP) 的 Java 原生治理、安全审计与控制面网关基础设施。**

[![构建状态](https://img.shields.io/badge/build-passing-brightgreen)](#)
[![Java 17](https://img.shields.io/badge/Java-17-orange.svg)](#)
[![Spring Boot 3.3](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](#)
[![SARIF 2.1.0](https://img.shields.io/badge/SARIF-2.1.0-blue.svg)](https://docs.oasis-open.org/sarif/sarif/v2.1.0/sarif-v2.1.0.html)
[![正式发布](https://img.shields.io/badge/release-v0.3.0--gateway-blue.svg)](#)
[![开源协议](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

---

## 1. 什么是 MCP Governance？

随着企业级 AI Agent 的快速演进，开发者和企业需要连接数十个 MCP Server、数百个外部工具及异构大模型。然而，MCP 的调用与权限暴露普遍缺乏统一的可见性、安全审计与自动化访问控制。

**MCP Governance (`mcp-manager`)** 提供渐进式、企业级的 Java 原生治理基础设施：
1. **离线静态安全扫描器 (Phase 1)**：纯 Java 静态分析 CLI 与 GitHub Action，在配置部署前检测 Token/凭证泄露、权限过度宽泛通配符、隐藏提示词注入与影子工具劫持风险，输出标准 **OASIS SARIF 2.1.0** 报表无缝集成 GitHub Code Scanning。
2. **嵌入式运行时治理 (Phase 2)**：零侵入的 Spring Boot Starter，提供纯 Java 拦截器责任链（`McpInterceptorChain`）、Tool ACL 动态访问控制（黑白名单与通配符）、参数正则安全拦截、Micrometer 监控与 Actuator 端点（`/actuator/mcpGovernance`），以及开箱即用的 SIEM 单行紧凑 JSON 审计日志（`McpAuditEvent`）。
3. **独立控制面网关 (Phase 3)**：基于 Spring Boot 3.3+ 响应式 WebFlux 与 Netty 的独立网关进程，聚合多协议下游（HTTP、SSE 长连接与受控本地 STDIO 进程池托管沙箱），提供前缀命名空间防冲突、租户级工具过滤及全链路安全责任链治理。

---

## 2. 终端高保真效果预览

使用 `mcp-governance-cli` 对 MCP 配置文件执行静态扫描时，终端以高对比度 ANSI Markdown 卡片呈现结果并严格执行阻断退出码：

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

## 3. 差异化定位矩阵 (Why MCP Governance)

| 功能与特性维度 | Wanaku | AI MCP Bridge | mocapi | **MCP Governance (`mcp-manager`)** |
|---|---|---|---|---|
| **静态配置安全扫描** | ❌ 无 | ❌ 无 | ⚠️ 基础 Schema 校验 | ✅ **完整引擎 (`mcp.json` / Claude Desktop)** |
| **OWASP MCP Top 10 映射** | ❌ 无 | ❌ 无 | ❌ 无 | ✅ **内置 MCP01/02/03 核心规则集** |
| **SARIF 2.1.0 契约导出** | ❌ 无 | ❌ 无 | ❌ 无 | ✅ **原生 OASIS SARIF 标准报表** |
| **CI / GitHub Action 门禁** | ❌ 无 | ❌ 无 | ❌ 无 | ✅ **轻量 Composite Action + CodeQL 上传** |
| **Core 模块零 Spring 依赖** | ❌ 不适用 | ❌ 不适用 | ❌ 不适用 | ✅ **纯 Java 17 领域模型与扩展 SPI** |
| **运行时审计与 Starter** | ❌ 仅路由 | ❌ 仅桥接 | ❌ 仅 Mock | ✅ **完整的 Spring Boot Starter (Phase 2)** |
| **Tool ACL 与正则安全校验** | ⚠️ 部分支持 | ❌ 无 | ❌ 无 | ✅ **严格白名单/黑名单模式与参数防御** |
| **指标遥测与 Actuator 端点** | ❌ 无 | ❌ 无 | ❌ 无 | ✅ **Micrometer 监控与 `/actuator/mcpGovernance`** |
| **独立控制面网关** | ✅ 活跃 | ⚠️ 代理 | ❌ 无 | ✅ **响应式多协议独立网关 (Phase 3)** |
| **受控本地 STDIO 进程池沙箱** | ⚠️ 无沙箱 | ❌ 无 | ❌ 无 | ✅ **敏感环境变量过滤与两阶段优雅安全停机** |

---

## 4. 快速上手 (Quick Start，30 秒跑起来)

### 方式一：独立 CLI 扫描工具 (Fat-JAR)

编译生成单 JAR 可执行包，直接对任何 MCP 配置文件发起静态安全扫描：

```bash
# 构建全部模块并生成 fat-jar
mvn clean package -DskipTests

# 扫描 MCP 配置文件并导出 SARIF 报告
java -jar mcp-governance-cli/target/mcp-governance-cli-0.1.0-SNAPSHOT-all.jar \
  samples/vulnerable-mcp.json \
  --output scan-result.sarif
```

### 方式二：嵌入式 Spring Boot Starter（零侵入）

为现有的 Java 应用直接引入运行时治理能力：

```xml
<dependency>
    <groupId>io.github.chukui2005</groupId>
    <artifactId>mcp-governance-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

在 `application.yml` 中声明安全治理策略：

```yaml
mcp:
  governance:
    enabled: true
    logging:
      enabled: true
      mask-sensitive-args: true       # 敏感参数自动打码脱敏 (***)
    acl:
      enabled: true
      default-mode: WHITELIST         # 严格默认拒绝，白名单允许模式
      allowed-tools:
        - "calculator"
        - "file_read_*"               # 支持通配符匹配
    validation:
      enabled: true
      rules:
        - tool-name-pattern: "file_*"
          parameter-name: "path"
          regex-pattern: "^/safe/.*"  # 防路径穿越正则拦截
```

### 方式三：独立控制面网关 (多上游聚合与代理)

将异构分布式上游（HTTP、SSE、本地 STDIO）聚合为统一的网关入口：

1. 编写路由配置 `gateway-routes.json`：
```json
{
  "gateway": { "host": "127.0.0.1", "port": 8080 },
  "upstreams": [
    { "id": "k8s-svc", "protocol": "http", "prefix": "k8s_", "uri": "http://10.0.0.1:9091/mcp" },
    { "id": "fs-local", "protocol": "stdio", "prefix": "fs_", "stdio": { "command": "node", "args": ["server.js"] } }
  ]
}
```

2. 启动独立网关：
```bash
java -jar mcp-governance-gateway/target/mcp-governance-gateway-0.1.0-SNAPSHOT.jar
```

3. 将各种 AI Agent（Cursor, Claude Desktop, Dify 等）直接连接到 `http://127.0.0.1:8080/mcp/v1/rpc`。

### 方式四：GitHub Action CI 流水线 (5 行 YAML)

直接在项目的 `.github/workflows/ci.yml` 中集成 MCP 静态审计能力：

```yaml
- name: Audit MCP Configuration
  uses: chukui2005/mcp-manager@v0.1.0
  with:
    target: 'mcp.json'
    upload-sarif: 'true'
    fail-on-findings: 'true'
```

---

## 5. 系统架构图 (Architecture)

```text
               +-------------------------------------------------------------+
               |          AI Agents (Claude Desktop, Cursor, LangChain)      |
               +-------------------------------------------------------------+
                                      |
                                      | Streamable HTTP / JSON-RPC 2.0
                                      v
+===========================================================================================+
|                     mcp-governance-gateway (独立控制面网关)                                |
|                                                                                           |
|  [ 入站适配层 Ingress ]                                                                    |
|    - POST /mcp/v1/rpc (Streamable HTTP，支持 ping 保活与 notifications/initialized 通知)    |
|    - GET  /mcp/v1/sse (Server-Sent Events 会话长连接)                                     |
|                                                                                           |
|  [ 命名空间与路由聚合 Router ]                                                              |
|    - 最长前缀匹配与命名空间隔离 (如 k8s_get_pods -> 剥离前缀 get_pods)                         |
|    - tools/list 租户级 Tool ACL 动态最小权限过滤                                           |
|                                                                                           |
|  [ Core 核心治理流水线 (与 Spring Boot Starter 100% 共享纯 Java 内核) ]                    |
|    Order -100: MetricsMcpInterceptor        (记录调用频次、耗时统计与成功率指标)           |
|    Order  -50: AuditMcpInterceptor          (敏感参数深度打码脱敏，发布 SIEM 结构化事件)    |
|    Order  100: ToolAclMcpInterceptor        (白名单/黑名单及通配符严格阻断)                |
|    Order  200: ParameterValidationInterceptor (正则防御路径遍历与注入攻击)                 |
|                                                                                           |
|  [ 出站客户端连接池 Egress Pools ]                                                         |
|    - HttpUpstreamClient  (响应式 WebClient，具备安全 Token 注入与超时控制)                  |
|    - SseUpstreamClient   (SSE 长连接订阅流，具备指数退避断线自动重连)                      |
|    - StdioUpstreamClient (受控本地子进程池 ManagedStdioProcessPool 与环境变量沙箱隔离)     |
+===========================================================================================+
            |                             |                             |
            v                             v                             v
+-----------------------+     +-----------------------+     +-----------------------+
| 远端 HTTP MCP Server  |     | 远端 SSE MCP Server   |     | 受控本地 STDIO 进程   |
+-----------------------+     +-----------------------+     +-----------------------+
```

---

## 6. 内置安全规则参考 (Security Rules Reference)

| 规则编号 | OWASP 类别 | 严重级别 | 规则说明 | 修复建议 |
|---|---|---|---|---|
| **`MCP01-001`** | MCP01 Token 管理不当与密钥暴露 | `CRITICAL` | 静态扫描环境变量中的硬编码敏感 Token、API Key (`ghp_`, `sk-`, `Bearer `)。 | 严禁硬编码敏感凭据，使用系统环境变量或密钥托管组件注入。 |
| **`MCP02-001`** | MCP02 权限范围蔓延 | `HIGH` | 检查工具参数 schema 正则中是否包含过度宽松的通配模式 (`.*`, `.+`)。 | 收紧参数模式约束，优先使用限定长度或枚举值。 |
| **`MCP03-001`** | MCP03 工具投毒 | `CRITICAL` | 检查工具描述（Description）中是否包含试图篡改系统提示词的注入关键字。 | 清洗工具描述文本，去除越权提示注入内容。 |
| **`MCP03-002`** | MCP03 工具投毒 | `HIGH` | 静态扫描同一配置中跨 Server 声明的同名工具（影子工具劫持风险）。 | 重命名冲突工具或引入 Server 专属命名空间前缀。 |

---

## 7. 路线图与里程碑 (Roadmap)

- **Phase 1: 静态扫描与审计 CLI (已结项交付 - v0.1.0-scan)**
  - [x] 纯 Java 17 领域模型 (`GovernedTool`, `McpConfig`, 零 Spring 依赖)
  - [x] 标准 `mcp.json` 与 Claude Desktop 格式纯静态 AST 解析器
  - [x] 基于 SPI 的 OWASP MCP Top 10 规则集 (`MCP01-001`, `MCP02-001`, `MCP03-001`, `MCP03-002`)
  - [x] OASIS SARIF 2.1.0 序列化与 JSON Schema 契约校验
  - [x] CLI 可执行 Fat-JAR、ANSI 彩色终端报表与 Exit Code 门禁
  - [x] GitHub Composite Action 封装与仓库自举静态审计工作流
- **Phase 2: 运行时治理与 Spring Boot Starter (已全面收官 - v0.2.0-starter)**
  - [x] `mcp-governance-spring-boot-starter` 条件化自动装配与零侵入适配
  - [x] 纯 Java 不可变上下文模型与拦截器责任链体系 (`McpInterceptorChain`)
  - [x] Tool ACL 访问控制（黑白名单与通配符规则，治理 OWASP MCP02）
  - [x] 参数安全校验拦截器（防命令注入与路径遍历，治理 OWASP MCP05）
  - [x] 运行时指标遥测与 Spring Boot Actuator 端点集成 (`/actuator/mcpGovernance`, 治理 OWASP MCP08)
  - [x] 不可变结构化审计事件 (`McpAuditEvent`)、递归敏感参数脱敏 (`SensitiveDataMasker`, 治理 OWASP MCP01)
  - [x] Spring 原生 `@EventListener` 异步事件与单行紧凑 JSON 格式导出 (开箱即用适配 SIEM/ELK)
- **Phase 3: 独立 MCP 治理网关 (已全面收官交付 - v0.3.0-gateway)**
  - [x] 独立 MCP 网关架构决策 (ADR 0008)、技术蓝图与路由契约 (`gateway-routes.schema.json`)
  - [x] `mcp-governance-gateway` 独立模块搭建，采用 Spring Boot 3.3+ 响应式 WebFlux 与 Netty 架构 (TASK-009)
  - [x] 动态路由加载、`tools/list` 命名空间前缀重写聚合与租户级 Tool ACL 最小权限过滤 (TASK-010)
  - [x] 远端 HTTP 与 SSE 长连接出站反向代理客户端池 (`UpstreamClientPool`) 与环境变量凭据安全注入 (TASK-011)
  - [x] 受控本地 STDIO 进程池托管 (`ManagedStdioProcessPool`)，具备敏感环境变量沙箱与两阶段优雅安全停机 (TASK-012)
  - [x] 网关治理责任链全面贯通（Tools 调用严格阻断与审计下沉）、MCP 协议生命周期对齐与 CORS 跨域防护装配 (TASK-013)

---

## 8. 贡献与安全 (Contributing & Security)

- 欢迎参与共建！请参阅 [CONTRIBUTING.md](CONTRIBUTING.md) 了解代码风格、测试规范与 Pull Request 流程。
- 如需上报安全漏洞，请查阅 [SECURITY.md](SECURITY.md)。
