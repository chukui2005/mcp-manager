# 0008. Phase 3 独立 MCP 网关 (Gateway) 架构设计与协议代理模型

* **状态**: 提议中 (Proposed)
* **日期**: 2026-10-31
* **决策者**: 架构师 AI、项目负责人
* **参考文档**: AGENTS.md, docs/environment-baseline.md, docs/adr/0001-project-scope-and-modules.md, docs/adr/0004-spring-boot-starter-and-runtime-interception.md, docs/adr/0005-runtime-policy-and-tool-acl.md, docs/adr/0006-runtime-metrics-and-actuator.md, docs/adr/0007-runtime-audit-event-and-listener.md, .worklog/TASK-008.md

---

## 背景 (Context)

在 Phase 1（静态配置扫描与 SARIF 安全审计）与 Phase 2（Spring Boot Starter 嵌入式运行时治理、Tool ACL、参数校验、指标遥测、结构化审计事件）圆满收官并合入 `main` 后，本项目已完全具备了针对单个 Java 应用程序内部的 MCP 客户端调用拦截能力。

然而，当企业与组织内部的 MCP 生态从单机、单应用走向大规模微服务与多 Agent 协作时，暴露出不可忽视的体系化治理瓶颈：
1. **拓扑爆炸与多对多耦合（N × M 难题）**：
   企业内部存在多个异构 Agent（如 Claude Desktop、Cursor、Devin、LangChain/Dify 智能体）与多个下游 MCP Server（如 Git、Database、Filesystem、Kubernetes、AWS CLI）。每个 Agent 各自配置直连所有 Server，导致凭证散落各处、连接重复创建、端口暴露剧增。
2. **异构传输协议代理缺失**：
   MCP 协议支持 STDIO（标准输入输出）、SSE（Server-Sent Events）以及最新的 Streamable HTTP。很多 Agent 仅支持 STDIO 或 HTTP，而企业级 MCP Server 可能部署在容器化云原生集群上（HTTP/SSE）。亟需统一的网关进行协议桥接（例如：Agent 经由 HTTP 访问，网关代理到后端远端 SSE 或本机隔离托管的 STDIO 进程）。
3. **工具命名空间冲突与动态聚合（Tool Routing & Namespace）**：
   当下游存在多个 Server 提供同名或功能重叠工具时（如 `db-server` 与 `cache-server` 都提供 `query` 或 `list`），缺少命名空间隔离机制（例如前缀重写 `db_query` vs `cache_query`），导致 Agent 识别混淆与影子劫持风险（OWASP MCP03、MCP09）。
4. **集中式企业级治理与鉴权下沉**：
   在单应用 Starter 模式下，策略配置绑定在各个应用本地。当面对外部接入的非 Java Agent 时，Starter 无法覆盖。必须提供一个**独立部署的 MCP 治理网关（Standalone Gateway）**，将 Phase 2 沉淀的 Tool ACL 访问控制、参数校验、请求耗时与错误指标（Metrics）、结构化审计日志（Audit Event）下沉为企业统一接入流量入口。
5. **架构边界与依赖纯洁性约束（不可协商）**：
   - 依据《AGENTS.md》第 4 节：`mcp-governance-core` 必须保持纯 Java 17，严禁引入 Spring 依赖；网关模块应作为独立模块（`mcp-governance-gateway`）继承 `core`，可采用轻量高效的响应式技术栈（Spring Boot 3.3.4 + Spring WebFlux / Netty）；
   - 依据《AGENTS.md》第 6 节：控制面模型与 Wire 传输模型强隔离，不得将 MCP SDK 内部类型泄漏到治理层与路由契约中。

---

## 架构决策 (Decision)

### 1. 设立独立模块 `mcp-governance-gateway`

在根 POM 中规划新增 Maven 模块：
```xml
<module>mcp-governance-gateway</module>
```
- **技术选型**：采用 Java 17 + Spring Boot 3.3.4 (Reactive WebFlux / Project Reactor / Netty)。选择响应式非阻塞 IO，满足网关处理长连接、SSE 流式推送与高并发反向代理的性能要求。
- **模块依赖边界**：
  - 依赖 `mcp-governance-core`（引入责任链 `McpInterceptorChain`、`ToolAclPolicy`、`SensitiveDataMasker`、`McpAuditEvent`、`McpMetricsCollector` 等纯 Java 治理内核）；
  - 依赖 `spring-boot-starter-webflux`、`spring-boot-starter-actuator`；
  - 严禁向 `core` 模块反向引入任何网关或 Web 依赖，严格维护单向依赖流。

---

### 2. 网关系统分层与逻辑架构

网关划分为清晰的五大层次：

```
+--------------------------------------------------------------------------+
|                       1. Inbound Ingress (入站适配层)                     |
|    - Streamable HTTP / JSON-RPC Endpoint (POST /mcp/{agentId})           |
|    - Server-Sent Events (SSE) Ingress (/mcp/sse)                         |
|    - Stdio Bridge Ingress (可选命令行管道适配)                           |
+--------------------------------------------------------------------------+
                                    | (Inbound MCP Request)
                                    v
+--------------------------------------------------------------------------+
|                      2. Router & Namespace (路由与命名空间层)             |
|    - Route Table Lookup (匹配客户端租户与路径)                            |
|    - Tool Aggregation (合并下游多个 Server 的 tools/list)                 |
|    - Namespace Prefixing / Stripping (前缀防冲突: git_* -> git-server)    |
+--------------------------------------------------------------------------+
                                    | (Standard McpInvocationContext)
                                    v
+--------------------------------------------------------------------------+
|                    3. Governance Pipeline (复用 Core 治理流水线)          |
|    [MetricsMcpInterceptor]   (Order -100: 延迟、TPS、失败率监控)          |
|    [AuditMcpInterceptor]     (Order  -50: 不可变结构化审计与敏感数据脱敏) |
|    [ToolAclMcpInterceptor]   (Order  100: 租户级工具黑白名单与通配符拦截)  |
|    [ParameterValidationInterceptor] (Order 200: 正则防注入与路径穿越防范) |
+--------------------------------------------------------------------------+
                                    | (Governed & Dispatched)
                                    v
+--------------------------------------------------------------------------+
|                      4. Downstream Egress (出站代理层)                    |
|    - Remote SSE Client Pool (远端 MCP Server 反向代理)                    |
|    - Streamable HTTP Client (远端 HTTP MCP Server 反向代理)              |
|    - Managed Local STDIO Process (本地隔离子进程托管，严格遵循安全基线)    |
+--------------------------------------------------------------------------+
                                    |
+--------------------------------------------------------------------------+
|                      5. Control Plane (控制与观测平面)                    |
|    - 动态路由与策略配置热加载 (gateway-routes.json / YAML)                 |
|    - Actuator 监控端点 & Prometheus 指标导出                             |
|    - SIEM 结构化审计流实时导出 (JSON 日志 / 审计 SPI)                     |
+--------------------------------------------------------------------------+
```

---

### 3. 多协议支持与 STDIO 安全托管基线

1. **Streamable HTTP & SSE 优先（推荐模式）**：
   - 监听本地端口（默认 `127.0.0.1:8080`），严格遵循《AGENTS.md》5.2 节：未开启 OAuth 时默认**强制绑定 127.0.0.1**，并强校验 HTTP `Origin` 与 `Host` 头，防止 DNS Rebinding 与 CSRF 劫持。
2. **受控的本地 STDIO 进程托管（严格限制）**：
   - 根据《AGENTS.md》5.2 节：“STDIO 命令执行必须被视为不受信任代码执行”；
   - 网关默认**关闭**任意 STDIO 命令动态执行；仅允许通过配置文件白名单预定义可执行文件路径与固定参数；
   - 严禁通过环境变量或客户端请求动态拼接 shell 命令；执行子进程时进行严格的环境变量脱敏与权限沙箱隔离。

---

### 4. 动态工具路由与命名空间聚合 (Namespace & Aggregation)

网关对上游 Agent 呈现为一个虚拟的单一聚合 MCP Server：
1. **`tools/list` 聚合发现**：
   - 当 Agent 发起 `tools/list` 请求时，网关并发向上游多个目标 Server 查询工具清单；
   - 按照路由规则注入命名空间前缀（例如将 `github-server` 的 `create_issue` 映射为 `github_create_issue`）；
   - 应用租户 ACL 过滤，被 ACL 显式禁止的工具直接在 `tools/list` 中剔除，实现**按权限最小化暴露（OWASP MCP02）**。
2. **`tools/call` 精确分发与解包**：
   - 当 Agent 调用带有前缀的工具名（如 `github_create_issue`）时，网关识别路由前缀，将其还原为下游真实工具名 `create_issue`；
   - 请求流经治理流水线（参数校验、敏感词脱敏、审计事件生成）；
   - 路由给后端目标 Server 对应的 Client 实例执行并转发响应结果。

---

### 5. 声明式路由契约与规范

为网关定义强类型、自解释的 JSON Schema 契约：`docs/contracts/gateway-routes.schema.json`。
包含：
- `gateway`: 监听地址、端口、优雅停机参数；
- `routes`: 路由列表，指定 `id`、`path`、`upstream`（协议类型 `sse` / `http` / `stdio`、端点地址或进程配置）；
- `governance`: 关联的 ACL 模式、允许/拒绝名单、参数校验规则，实现“路由即策略”。

---

## 影响与后果 (Consequences)

### 正向收益 (Positive)
1. **集中治理收敛**：解决了多 Agent 异构接入时的安全黑洞，一次部署网关，全部 Agent（无论 Python、Node.js、Go、Rust 还是闭源客户端）立刻享有统一的 Tool ACL、敏感词脱敏、参数校验与 SIEM 审计。
2. **零改动复用 Core 资产**：Core 模块中经由 Phase 1 与 Phase 2 严格沉淀的 `McpInterceptorChain`、`McpAuditEvent`、`SensitiveDataMasker`、`ToolAclPolicy` 100% 直接复用，无需重写任何安全逻辑。
3. **架构清晰解耦**：客户端只需感知统一网关入口；下游 Server 拓扑调整、版本升级、凭证轮转仅在网关侧配置变更，客户端无感知。

### 潜在代价与挑战 (Trade-offs & Mitigations)
1. **网络跳数增加（Hops）**：增加了网关代理层引入的微量延迟。
   - *缓解措施*：网关采用 Netty 异步非阻塞 IO，治理拦截链采用无锁内存计算，基准链路延迟控制在毫秒级以内。
2. **高可用性（HA）要求**：网关成为所有 MCP 流量的单点（SPOF）。
   - *缓解措施*：网关保持无状态设计（Stateless），路由与策略通过配置中心或文件动态加载，支持水平扩展至 Kubernetes 集群并挂载负载均衡器。

---

## 备选方案 (Alternatives Considered)

1. **备选方案 A：继续沿用 Spring Boot Starter，不开发独立网关**
   - *评估*：不采用。Starter 仅能赋能基于 Java / Spring Boot 的 Agent 应用；无法治理 Cursor、Claude Desktop 等原生客户端，无法满足混合多语言生态与企业级平台管控诉求。
2. **备选方案 B：基于现有的通用 API 网关（如 Spring Cloud Gateway 或 Kong）编写插件**
   - *评估*：不采用。MCP 是基于 JSON-RPC 2.0、具有长连接会话、动态 `tools/list` 协商与流式 STDIO/SSE 特性的专业 AI 协议，通用 HTTP 网关缺乏对 MCP 协议握手、工具聚合与命名空间重写的原生理解，插件维护成本高且难以与本项目 Core 治理深度联动。
3. **备选方案 C：在 Core 模块中实现网关网络层**
   - *评估*：严重违反宪章，坚决否决。《AGENTS.md》严格规定 `core` 模块必须保持纯 Java 17、零框架依赖。网关网络传输与 WebFlux 编排必须置于独立的 `mcp-governance-gateway` 模块。
