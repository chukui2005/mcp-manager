# 项目换届与会话交接指南 (Handover Guide)

> **当前维护者**: MCP Governance 统筹中枢  
> **更新时间**: 2026-11-02  
> **当前版本**: v0.3.0-alpha (Phase 3 网关核心出站代理与长连接池收官)  
> **对应阶段**: Phase 3 (独立 MCP 网关建设中)

---

## 1. 会话轮转与换届纪律 (Session Rotation Rule)

根据《AGENTS.md》宪章第 3.1 节，为了防止会话上下文过长导致模型“降智”（注意力衰减、规则遗忘）以及响应延迟翻倍，本项目执行严格的**会话轮转制度**：

### 换届标准触发点（何时精准新开会话）：
- **精确触发条件**：**当且仅当一个完整的 TASK 闭环合并到 `main` 分支后（状态机达到 `DONE`）**。
- **换届操作**：
  1. 当前统筹者在合入 `main` 后，更新本文档（`docs/handover.md`）中的当前进展与下一步目标；
  2. 人类在 DeepSeek Harness Web 界面点击 **“新建会话”**；
  3. 人类将本文档第 4 节的【开场接力指令】一键复制发送给新会话 AI；
  4. 新会话 AI 拥有 100% 崭新、纯净的 Token 窗口，通过读取物理文件瞬间接力，继续调用智能体团队推进下一个 TASK。

---

## 2. 当前工程与资产完成现状 (Status)

### 2.1 已完成并合并入 `main` 的里程碑
1. **基础设施与协作宪章**:
   - `AGENTS.md`：确立三权分立（架构师只读、工程师隔离分支、审核员只读把关、人类最终批准）与状态机规范；
   - 语言规范：强制全中文文档与注释规范；
   - 远程绑定：`https://github.com/chukui2005/mcp-manager.git`，GroupId `io.github.chukui2005`。
2. **`TASK-000-dry-run` 闭环演练**:
   - 验证了 ADR 0000 产出、`Approved: Yes` 人类门禁、隔离分支提交、Reviewer 判定与人类合并全流程。
3. **`TASK-001` 多模块骨架与 SARIF 契约**:
   - 确立 ADR 0001 与 SARIF 2.1.0 契约；
   - 实现纯 Java 领域模型 `GovernedTool`、静态引擎与 `MCP01-001` 密钥检测；
4. **`TASK-002` 真实 MCP 配置文件解析引擎与 OWASP MCP02/MCP03 规则集**:
   - 实现 `McpConfigParser` 与不可变 `McpConfig` 模型；
   - 实现 `MCP02-001` (权限蔓延/通配符)、`MCP03-001` (隐藏提示注入)、`MCP03-002` (同名工具影子劫持)；
   - 实现 ANSI 彩色 Markdown 报告渲染器 `ConsoleReportRenderer` 与 Exit Code 2 门禁。
5. **`TASK-003` GitHub Action 自举静态审计与 Phase 1 双语 README 交付 (Phase 1 结项收官)**:
   - 产出 ADR 0003 与 Blueprint 0003，实现 Shade Fat-JAR、Composite Action 封装与 CI 自举扫描；
   - 交付中英双语 `README.md` 与 `README.zh-CN.md`。
6. **`TASK-004` Spring Boot Starter 脚手架与运行时拦截契约设计 (Phase 2 启航收官)**:
   - 产出 ADR 0004 与 Blueprint 0004，建立纯 Java 不可变上下文模型与责任链拦截器契约；
   - 实现 Spring Boot 3.3.4 条件装配与敏感入参脱敏审计拦截器 `LoggingMcpInterceptor`。
7. **`TASK-005` 运行时策略拦截器集实现与 Spring Boot 深度集成 (Phase 2 核心收官)**:
   - 实现 `AclMode`、`ToolAclPolicy`、`ParameterValidationRule`、`ToolAclMcpInterceptor` (Order: 100) 与 `ParameterValidationMcpInterceptor` (Order: 200)；
   - Starter 自动装配与责任链条件注入，全量测试通过并合入 `main`。
8. **`TASK-006` 运行时指标遥测与 Micrometer / Actuator 监控观测集成 (Phase 2 可观测性收官)**:
   - 实现 `InvocationStatus`、`ToolMetricsSnapshot`、`McpMetricsCollector` SPI、`InMemoryMcpMetricsCollector` 与 `MetricsMcpInterceptor` (Order: -100)；
   - Starter 自动装配与 Actuator 端点 `/actuator/mcpGovernance`，全量测试通过并合入 `main`。
9. **`TASK-007` 运行时审计记录结构化导出与事件监听器体系 (Phase 2 全面圆满收官)**:
   - 实现不可变记录 `McpAuditEvent`、递归打码 `SensitiveDataMasker`、`McpAuditPublisher` SPI 与 `AuditMcpInterceptor` (Order: -50)；
   - Starter 支持 `@EventListener` 与单行紧凑 JSON 导出（SIEM 零解析采集），全量测试通过并合入 `main`。
10. **`TASK-008` Phase 2 结项验收复盘与 Phase 3 独立网关架构预研 (架构收官)**:
    - 产出 ADR 0008、Blueprint 0008 与网关路由契约 `docs/contracts/gateway-routes.schema.json`。
11. **`TASK-009` mcp-governance-gateway 模块骨架搭建与路由聚合引擎实现 (Phase 3 启航收官)**:
    - **模块与架构分层**: 新建 Maven 模块 `mcp-governance-gateway`，单向依赖纯 Java 模块 `mcp-governance-core`，引入 Spring Boot 3.3.4 Reactive WebFlux (`spring-boot-starter-webflux`) 与 Actuator；
    - **配置与契约模型**: 严格映射 `gateway-routes.schema.json` 契约，实现 `GatewayServerProperties`（默认强制安全基线 `127.0.0.1:8080`）、`RouteDefinition`、`UpstreamProtocol`（sse/http/stdio）、`StdioCommandConfig`、`UpstreamAuthConfig`、`RouteGovernanceConfig`；
    - **路由聚合管理**: 实现高并发安全的内存路由管理器 `RouteManager`，支持前缀冲突检测与**最长前缀优先匹配算法**（Longest Prefix Match）；
    - **JSON-RPC 2.0 端点**: 实现 `JsonRpcRequest`、`JsonRpcResponse`、`JsonRpcError` 领域模型与 Streamable HTTP 控制器 `McpRpcController`（`POST /mcp/v1/rpc`），支持 `initialize` 握手、`tools/list` 命名空间聚合与 `tools/call` 前缀解析分发；
    - **Core 治理适配**: 实现 `GatewayExecutionAdapter`，将入站请求打通转换为 Core 的不可变 `McpInvocationContext`；
    - **全量门禁**: 全工程 7 个模块、95 个单元与集成测试 100% PASS，Javadoc 零警告，已正式合并入 `main`。
12. **`TASK-010` 动态路由配置加载、tools/list 聚合视图与租户级 ACL 过滤深化 (Phase 3 核心深化收官)**:
    - **契约对齐与动态加载**: 实现 `GatewayRoutesConfig` 与 `RouteConfigLoader`，支持从外部 JSON、流或 Classpath/File 资源加载路由表，支持动态热更新至 `RouteManager` 并重置；
    - **工具发现与前缀命名空间防冲突**: 抽象 `UpstreamToolProvider` SPI，实现 `ToolAggregator` 并发拉取下游工具，注入唯一路由命名空间前缀 `prefix`，规避跨下游同名工具冲突与影子劫持风险（防 OWASP MCP03、MCP09）；
    - **租户级 ACL 动态过滤**: 联动下游 `RouteGovernanceConfig` 并转换为 Core 模块不可变 `ToolAclPolicy`，支持白名单/黑名单加通配符模式，未授权工具在 `tools/list` 发现视图中静默剔除，实现最小权限暴露（防 OWASP MCP02 权限蔓延）；
    - **全流程端点打通与全量门禁**: `McpRpcController` 实现响应式 `tools/list` 聚合发现与过滤，105 个单元与集成测试全部 PASS，Javadoc 零警告，两轮审核通过并合入 `main`。

---

## 3. 当前进展与下一个任务目标：TASK-012 (受控本地 STDIO 进程池托管与 Phase 3 独立网关收官)

### 3.1 刚完成任务：TASK-011 (远端 HTTP / SSE 反向代理与出站客户端池落地)
- **状态**: 人类授权合并，分支 `task/TASK-011-upstream-client-pool` 已成功合并入 `main`，状态机达成 `DONE`。
- **核心成果**:
  1. 定义纯 Java `UpstreamClient` SPI 统一出站接口，屏蔽协议底层差异；
  2. 实现了基于 Spring WebFlux 的 `HttpUpstreamClient` 与支持长连接会话、动态 endpoint 协商的 `SseUpstreamClient`；
  3. 实现了 `TokenRefResolver`，严格遵循安全基线解析环境变量，独立注入下游鉴权头（Bearer/ApiKey/Basic），严禁透传上游客户端原始凭证，日志绝对脱敏；
  4. 实现了 `UpstreamClientPool` 与 `UpstreamClientFactory`，支持长连接复用与优雅停机销毁；
  5. 改造 `DefaultGatewayExecutionAdapter`（非阻塞 `executeReactive`）与 `DefaultUpstreamToolProvider`，打通真实调用与 `tools/list` 探测；
  6. 全模块构建成功，100+ 测试全部绿色 PASS，Javadoc 零警告。

### 3.2 下一个任务目标：TASK-012 (受控本地 STDIO 进程池托管与 Phase 3 独立网关综合验收)
- **所属阶段**: Phase 3 终局收官
- **核心模块**: `mcp-governance-gateway`
- **核心目标**:
  1. 依据 ADR 0008 与 Blueprint 0008，实现受控的本地进程出站客户端 `StdioUpstreamClient` 与 `ManagedStdioProcessPool`；
  2. 严格遵循安全基线：STDIO 命令执行必须被视为不受信任代码执行，仅允许通过配置文件白名单预定义可执行文件与固定入参，严禁动态命令拼接，继承最小环境变量子集；
  3. 实现本地子进程的标准输入输出（stdin/stdout）基于 JSON-RPC 2.0 异步消息交换与优雅销毁；
  4. 完善多协议混合代理场景端到端综合测试与性能基线压测；
  5. 完成 Phase 3 结项复盘与发布准备。

---

## 4. 给新会话统筹者的开场接力指令 (直接复制)

```markdown
我是项目负责人。本项目是 mcp-manager（Java 原生 MCP 治理与静态审计层）。
Phase 1、Phase 2 全部圆满收官，Phase 3 的前三个核心任务 TASK-009、TASK-010 与 TASK-011（网关骨架、动态路由加载、工具聚合视图、租户级 ACL 过滤、远端 HTTP/SSE 出站客户端池与凭证注入）已正式验收并合并入 main 分支。
请严格阅读仓库根目录的核心基线文档：
1. AGENTS.md（项目宪章、三权分立与会话轮转制度）
2. docs/handover.md（换届交接记录）
3. docs/environment-baseline.md（技术与版本基线）
4. docs/adr/0008-phase3-gateway-architecture-preview.md（网关架构 ADR）
5. docs/architecture/0008-blueprint.md（网关技术实施蓝图）
6. docs/contracts/gateway-routes.schema.json（网关路由契约）

【你的职责】：
作为本项目的统筹中枢，请直接【启用智能体团队 (Agent Teams)】，组建 architect（架构师）、engineer（研发工程师）、reviewer（代码审核员）三位常驻队友。

【当前启动任务】：
启动 TASK-012：受控本地 STDIO 进程池托管与 Phase 3 独立网关收官。
根据 ADR 0008 与 Blueprint 0008 分派研发工程师队友开展隔离分支开发与测试，代码审核员执行两遍审查，待通过后向我汇报。所有代码注释、文档一律使用中文。
```
