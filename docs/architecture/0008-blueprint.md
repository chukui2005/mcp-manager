# 0008. Phase 3 独立 MCP 网关 (Gateway) 技术实施蓝图

## 1. 概述与设计目标

本蓝图承接 ADR 0008，为 Phase 3 独立 MCP 网关（`mcp-governance-gateway`）提供详尽的技术落地架构、数据结构定义、协议流转时序、组件职责划分以及分阶段演进路线。

### 1.1 核心设计目标
1. **聚合网关 (Aggregating Reverse Proxy)**：向上游异构 Agent 呈现单一点对点聚合虚拟 MCP Server，向下游代理多个分布式或本地 MCP Server；
2. **多协议适配与桥接**：提供 Streamable HTTP（JSON-RPC POST）、SSE 长连接推送，以及严格受限的本地 STDIO 隔离进程托管；
3. **安全与治理下沉**：深度复用 `mcp-governance-core` 纯 Java 治理内核（Tool ACL、参数正则校验、指标采集、脱敏审计日志），确保非 Java 生态客户端享有同等的企业级安全合规防护；
4. **命名空间与工具路由**：自动前缀重写与解包，杜绝工具重名冲突与影子工具投毒劫持。

---

## 2. 整体架构与组件拆解

```
+-----------------------------------------------------------------------------------------+
|                                mcp-governance-gateway                                   |
|                                                                                         |
|  [ Inbound Handlers (入站处理器) ]                                                       |
|  - StreamableHttpEndpointHandler: POST /mcp/v1/rpc (支持标准 JSON-RPC 2.0 批量与单调用)     |
|  - SseIngressEndpointHandler: GET /mcp/v1/sse (建立服务端推送事件会话流)                  |
|                                                                                         |
|  [ Gateway Context & Resolution (上下文与路由解析) ]                                    |
|  - RouteResolver: 基于租户标识/URL 路径/工具前缀匹配目标 UpstreamRoute                    |
|  - ToolNamespaceManager: 工具注册聚合、前缀重写 (e.g. "fs:read_file" -> "read_file")     |
|                                                                                         |
|  [ Core Governance Pipeline (治理拦截责任链 - 100% 复用 Core) ]                         |
|  - MetricsMcpInterceptor (Order -100) -> 收集网关代理吞吐与延迟                         |
|  - AuditMcpInterceptor (Order -50)   -> 结构化不可变审计日志与敏感参数脱敏              |
|  - ToolAclMcpInterceptor (Order 100) -> 租户级工具黑白名单与通配符阻断                  |
|  - ParameterValidationInterceptor (Order 200) -> 入参注入与路径遍历防范                 |
|                                                                                         |
|  [ Downstream Egress Adapters (出站适配器) ]                                            |
|  - SseUpstreamClient: 基于 WebClient 的远端 SSE 流式客户端池                            |
|  - HttpUpstreamClient: 标准 HTTP POST JSON-RPC 转发客户端                               |
|  - ManagedStdioProcessPool: 受控本地 STDIO 进程生命周期管理器 (受限环境/显式白名单)     |
+-----------------------------------------------------------------------------------------+
                                 |                      |
                                 v                      v
                +--------------------------------+ +--------------------------------+
                | 远端 MCP Server (SSE / HTTP)    | | 受控本地进程 (STDIO Server)    |
                +--------------------------------+ +--------------------------------+
```

### 2.1 核心组件职责表

| 组件名称 | 所在包路径（建议） | 职责定位 |
|---|---|---|
| `GatewayServerProperties` | `...gateway.config` | 网关核心配置绑定（绑定端口、绑定主机 127.0.0.1、路由定义列表） |
| `RouteDefinition` | `...gateway.route` | 单个下游路由元数据（`id`, `prefix`, `upstreamType`, `uri`, `timeoutMs`, `acl`） |
| `RouteManager` | `...gateway.route` | 路由表内存索引、动态刷新与按工具名称/前缀快速检索定位 |
| `ToolAggregator` | `...gateway.aggregation` | 聚合各下游 `tools/list`，注入命名空间并结合租户 ACL 生成过滤视图 |
| `UpstreamClient` (SPI) | `...gateway.upstream` | 出站客户端统一接口，屏蔽底层 SSE、HTTP、STDIO 通信差异 |
| `UpstreamClientFactory` | `...gateway.upstream` | 根据 upstreamType 构造并缓存长连接 Client 实例 |
| `GatewayMcpExecutionAdapter` | `...gateway.adapter` | 将入站 JSON-RPC 包装为 Core 的 `McpInvocationContext` 并交由拦截链治理 |

---

## 3. 详细时序流转：以工具调用 (`tools/call`) 为例

```
Agent                  Gateway (Ingress)      Governance Chain       RouteManager       UpstreamClient       Target Server
  |                           |                      |                    |                    |                   |
  | 1. POST /mcp/v1/rpc       |                      |                    |                    |                   |
  |    (tools/call: "k8s_pod")|                      |                    |                    |                   |
  |-------------------------->|                      |                    |                    |                   |
  |                           | 2. 解析命名空间与路由 |                    |                    |                   |
  |                           |------------------------------------------>|                    |                   |
  |                           |    返回 RouteDefinition(k8s-server, "pod")|                    |                   |
  |                           |<------------------------------------------|                    |                   |
  |                           |                                           |                    |                   |
  |                           | 3. 构建 McpInvocationContext              |                    |                   |
  |                           | 4. 触发拦截链 (proceed)                   |                    |                   |
  |                           |--------------------->|                    |                    |                   |
  |                           |                      |-- Metrics (-100)   |                    |                   |
  |                           |                      |-- Audit (-50)      |                    |                   |
  |                           |                      |-- ACL (100)        |                    |                   |
  |                           |                      |-- Validation (200) |                    |                   |
  |                           |                      |                    |                    |                   |
  |                           |                      | 5. 校验通过，派发执行                   |                   |
  |                           |                      |---------------------------------------->|                   |
  |                           |                      |                    |                    | 6. 转发 JSON-RPC  |
  |                           |                      |                    |                    |    ("pod", args)  |
  |                           |                      |                    |                    |------------------>|
  |                           |                      |                    |                    | 7. 返回原始响应   |
  |                           |                      |                    |                    |<------------------|
  |                           |                      | 8. 封装 McpInvocationResult             |                   |
  |                           |                      |<----------------------------------------|                   |
  |                           | 9. 链式出栈，记录耗时、脱敏审计并归档      |                    |                   |
  |                           |<---------------------|                    |                    |                   |
  | 10. 组装响应 JSON 报文     |                      |                    |                    |                   |
  |<--------------------------|                      |                    |                    |                   |
```

### 异常阻断流转：
若在步骤 4 中，ACL 或参数校验未通过：
- 责任链立即抛出 `McpAccessDeniedException` 或 `McpGovernanceException`；
- `AuditMcpInterceptor` 处于外层，在 `catch` 块中立即捕获，记录 `status = ACCESS_DENIED` 或 `VALIDATION_FAILED`，脱敏入参并发布审计事件；
- `MetricsMcpInterceptor` 统计 `failureCount` 增加；
- 请求被阻断，**根本不会触达下游 UpstreamClient**，防止越权命令渗漏至受限系统；
- Ingress Handler 返回标准 JSON-RPC 2.0 错误响应（如 Error Code `-32003: Access Denied`）。

---

## 4. 安全防护与配置合规基线

严格落实《AGENTS.md》第 5 节硬性条款：
1. **默认安全绑定**：网关监听地址强制默认为 `127.0.0.1`，仅在显式配置安全凭证与代理策略时允许对外绑定；
2. **凭证隔离与不转发**：下游 Server 的鉴权 Token、API Key 由网关注册中心统一配置并在发起代理请求时挂载，**严禁上游 Agent 透传下游凭证**，杜绝凭证泄露风险（OWASP MCP01）；
3. **STDIO 受信执行沙箱**：
   - 只有在配置文件中显式列出的固定可执行路径允许拉起；
   - 环境变量仅继承最小受信任子集，不透传宿主机敏感变量（如 `AWS_SECRET_ACCESS_KEY`、`DOCKER_AUTH_CONFIG` 等）。

---

## 5. Phase 3 实施规划与里程碑路线 (Milestones)

Phase 3 建议拆分为 4 个演进任务推进：

| 任务标识 | 里程碑主题 | 核心交付成果 |
|---|---|---|
| **TASK-009** | 网关骨架与响应式 Ingress 基础 | 创建 `mcp-governance-gateway` 模块，实现 Streamable HTTP JSON-RPC 解析与基础代理抽象 |
| **TASK-010** | 动态路由、工具聚合与命名空间 | 实现 `gateway-routes.json` 配置解析、`tools/list` 命名空间前缀重写与动态聚合 |
| **TASK-011** | 治理责任链下沉与集中式审计 | 将 Core 的 ACL、参数校验、脱敏审计与指标收集器完整接入网关代理生命周期 |
| **TASK-012** | 多协议出站代理 (SSE / HTTP / STDIO) 与综合验收 | 实现下游 SSE 长连接池与受控 STDIO 托管，完成多 Agent 并发场景端到端测试 |
