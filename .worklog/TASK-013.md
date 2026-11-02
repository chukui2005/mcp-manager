# Task: TASK-013 - 独立网关治理链贯通、长连接自愈与协议规范对齐加固

## 状态元数据
- **Status**: IN_REVIEW
- **Owner**: Reviewer
- **Approved**: Yes
- **Created**: 2026-11-02
- **Updated**: 2026-11-02

---

## 1. 任务背景与核心目标
在经历全工程代码地毯式审查后，发现 `mcp-governance-gateway` 存在若干核心缺陷，需紧急开展加固攻坚：
1. **【CRITICAL】治理拦截链贯通 (MCP02/MCP08)**：在 `DefaultGatewayExecutionAdapter` 中完整串联并执行 Core 模块的 `McpInterceptorChain`（ToolAclMcpInterceptor、ParameterValidationMcpInterceptor、MetricsMcpInterceptor、AuditMcpInterceptor），彻底杜绝 tools/call 绕过 ACL 鉴权与审计的重大安全漏洞；
2. **【MAJOR】清除危险的伪造成功 Mock 回退**：彻底移除远程调用失败时直接返回 `status=SUCCESS` 的 `fallbackMockResult`，连接异常必须如实向客户端暴露失败；
3. **【MAJOR】修复 SseUpstreamClient 内存泄漏**：在 `sendRpcAndAwait` 中通过 `doFinally` 移除 `pendingRequests` 中的 `Sinks.One`，杜绝请求超时/网络中断导致的内存悬挂泄漏；
4. **【MAJOR】对齐 MCP 生命周期与协议规范**：
   - 增加对 `ping` 方法与通知 `notifications/initialized` 的规范处理（通知请求 `id==null` 时返回空，不输出响应体）；
   - 出站客户端实现出站握手 `initialize` 与 `notifications/initialized`；
5. **【MAJOR】长连接自愈与轻量重试**：
   - 为 `SseUpstreamClient` 引入指数退避自动重连机制；
   - 为只读/幂等方法（`tools/list`、`ping`）增加瞬时网络异常的轻量重试；
6. **【MAJOR】网关跨域安全过滤器**：在网关 WebFlux 中根据 `corsAllowedOrigins` 配置装配 `CorsWebFilter`；
7. **【MINOR】属性命名与正则优化**：统一 `callerId` 上下文属性，将 `TokenRefResolver` 中的正则提炼为预编译常量。

---

## 2. 验收标准 (Acceptance Criteria)
- [ ] 架构师产出 ADR 0009 (`docs/adr/0009-gateway-governance-pipeline-and-resilience.md`) 与技术蓝图
- [ ] 人类在本文档签署 `Approved: Yes`
- [ ] 工程师检出隔离分支 `task/TASK-013-gateway-resilience-and-pipeline` 完成编码
- [ ] 单元与集成测试覆盖：
  - 测试直接发起非授权 `tools/call` 被 `ToolAclMcpInterceptor` 阻断并记录审计事件；
  - 测试入参违规被 `ParameterValidationMcpInterceptor` 阻断；
  - 测试远端连接失败时如实报错，不再伪造成功；
  - 测试 SseUpstreamClient 超时后 Sink 正常被移除；
  - 测试 `ping` 与 `notifications/initialized` 协议对齐；
- [ ] `mvn clean verify` 100% 通过，Javadoc 零警告
- [ ] 审核员完成双遍审查并给出 `verdict: PASS`
- [ ] 人类最终批准合并入 `main`

---

## 3. 架构设计与契约约束 (Architect)
- **相关 ADR**: `docs/adr/0009-gateway-governance-pipeline-and-resilience.md`
- **设计摘要**: 待架构师填写

---

## 4. 人类批准记录 (Human Approval Gate)
Approved: Yes

---

## 5. 实现摘要 (Engineer)
- **PR 链接 / Commit Hash**: `task/TASK-013-gateway-resilience-and-pipeline`
- **主要变更文件**:
  1. `mcp-governance-gateway/src/main/java/io/github/chukui2005/mcpgovernance/gateway/adapter/DefaultGatewayExecutionAdapter.java`:
     - 注入并串联 Core 模块 `McpInterceptorChain`（Metrics -> Audit -> Tool ACL -> Parameter Validation）；
     - 动态从路由配置解析构建 `ToolAclMcpInterceptor`，杜绝 tools/call 绕过 ACL 权限与审计漏洞；
     - 彻底废除 `fallbackMockResult` 伪造成功的反模式，下游故障与连接中断如实暴露；
     - 统一上下文属性使用 `AuditMcpInterceptor.ATTR_CALLER_ID`。
  2. `mcp-governance-gateway/src/main/java/io/github/chukui2005/mcpgovernance/gateway/controller/McpRpcController.java`:
     - 支持 MCP `ping` 保活探针方法（返回 `{"status": "ok"}`）；
     - 支持 MCP 客户端通知机制（`id == null` 或 `notifications/initialized` 时返回 204 No Content，不返回多余 body）；
     - 统一捕获 `McpAccessDeniedException` (-32003) 与 `McpGovernanceException` (-32000)。
  3. `mcp-governance-gateway/src/main/java/io/github/chukui2005/mcpgovernance/gateway/upstream/client/SseUpstreamClient.java`:
     - 在 `sendRpcAndAwait` 返回的 Mono 上挂载 `.doFinally(signalType -> pendingRequests.remove(reqId))`，彻底修复请求超时与连接断开导致的 Sink 内存泄漏；
     - `connectSse()` 在 error 与 complete 回调中增加指数退避自动重连机制；
     - 幂等方法 `listTools` 与 `ping` 增加网络瞬时异常轻量级重试策略。
  4. `mcp-governance-gateway/src/main/java/io/github/chukui2005/mcpgovernance/gateway/config/GatewayWebFluxConfig.java`:
     - 新增响应式 WebFlux 配置类，根据 `corsAllowedOrigins` 装配并注册 `CorsWebFilter`，防御未授权跨域请求。
  5. `mcp-governance-gateway/src/main/java/io/github/chukui2005/mcpgovernance/gateway/upstream/auth/TokenRefResolver.java`:
     - 增加静态预编译常量 `PLAIN_ENV_NAME_PATTERN`，优化环境变量名正则匹配性能。
  6. 测试集补齐与增强:
     - `GatewayGovernancePipelineIntegrationTest`: 完整验证未授权 tools/call 被 ACL 阻断、敏感字段脱敏审计以及下游故障真实暴露；
     - `McpRpcControllerTest`: 验证 `ping` 与 `notifications/initialized` 协议行为。
- **测试覆盖说明**:
  - `mvn clean verify`: 全工程全部模块 100% 编译并通过所有单元与集成测试（共 114+ 测试，0 failures, 0 errors）；
  - `mvn javadoc:javadoc`: 全工程 7 个模块零警告通过 Javadoc 校验。

---

## 6. 审查与判定 (Reviewer)
- **Reviewer Verdict**: PENDING
- **Findings**:
  - [None]
