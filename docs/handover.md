# 项目换届与会话交接指南 (Handover Guide)

> **当前维护者**: MCP Governance 统筹中枢  
> **更新时间**: 2026-11-03  
> **当前版本**: v1.0.0 (TASK-015 已合入 main)  
> **对应阶段**: Phase 3 独立控制面网关收官 / Phase 4 (控制面 UI 与多租户凭据体系) 首发交付完成

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
    - **模块与架构分层**: 新建 Maven 模块 `mcp-governance-gateway`，引入 WebFlux 与 Actuator；
    - **路由聚合管理**: 实现高并发安全的内存路由管理器 `RouteManager`，支持前缀冲突检测与最长前缀优先匹配算法；
    - **JSON-RPC 2.0 端点**: 实现 `McpRpcController`（`POST /mcp/v1/rpc`），支持 `initialize` 握手、`tools/list` 与 `tools/call` 分发。
12. **`TASK-010` 动态路由配置加载、tools/list 聚合视图与租户级 ACL 过滤深化 (Phase 3 核心深化收官)**:
    - 实现 `GatewayRoutesConfig` 与 `RouteConfigLoader` 外部动态热加载；
    - 抽象 `UpstreamToolProvider` SPI 与 `ToolAggregator` 动态聚合与命名空间前缀注入；
    - 租户级 ACL 动态过滤，未授权工具在 `tools/list` 视图中静默剔除。
13. **`TASK-011` 远端 HTTP / SSE 反向代理与出站客户端池落地 (Phase 3 核心出站收官)**:
    - 抽象 `UpstreamClient` SPI，实现响应式 `HttpUpstreamClient` 与 `SseUpstreamClient`；
    - 落地 `TokenRefResolver` 独立鉴权头安全注入（严禁透传上游凭据）；
    - 落地 `UpstreamClientPool` 长连接池与 `DefaultGatewayExecutionAdapter` 响应式转发。
14. **`TASK-012` 受控本地 STDIO 进程池托管与 Phase 3 独立网关收官 (Phase 3 终局圆满收官)**:
    - 实现 `StdioUpstreamClient`，采用 `ProcessBuilder(List<String>)` 数组启动防 Shell 注入；
    - 实现敏感凭证环境变量清洗过滤隔离沙箱；
    - 落地 `ManagedStdioProcessPool` 两阶段优雅安全停机（SIGTERM 3s -> SIGKILL）。
15. **`TASK-014` 开源基建补齐与 Docker 容器化交付 (开源与基础设施收官)**:
    - **容器化交付**: 交付 `mcp-governance-gateway/Dockerfile`（精简 Alpine JRE 17、非 root UID 10001:10001 运行、端口 18080、Actuator 健康检查探针）；交付 `mcp-governance-cli/Dockerfile`（Alpine JRE 17、非 root 用户、`/workspace` 工作目录直接挂载即扫）；交付根目录 `docker-compose.yml`（一键编排网关与配置挂载，端口 18080:18080）；
    - **开源合规文档**: 补齐 `CONTRIBUTING.md`、`SECURITY.md`（48h/7d 漏洞披露 SLA）、`CHANGELOG.md`（涵盖 v0.1.0-scan, v0.2.0-starter, v0.3.0-gateway 全历程）；
    - **构建包装器**: 补齐跨平台 `mvnw`、`mvnw.cmd` 与 Maven 3.9.9 包装器；
    - **拓扑与版本一致性**: 统一模块版本 `0.1.0-SNAPSHOT`，更新《AGENTS.md》第 4 节项目目录树；
    - **全量门禁**: 全工程 7 个模块、130 个单元与集成测试 100% 绿灯全部通过，已正式合入 `main`。
16. **`TASK-015` 网关内置轻量 Web 控制台 (Web Admin UI) 与开源多媒体门面升级 (控制面可视化收官)**:
    - **架构资产**: 产出 ADR 0011（`docs/adr/0011-gateway-web-console.md`）、蓝图（`docs/architecture/blueprint-0011-web-console.md`）与控制面契约（`docs/contracts/web-console-api.json`）；
    - **自包含零构建单页 Web 控制台**: 在 `mcp-governance-gateway/src/main/resources/static/index.html` 交付现代化科技质感 SPA（Vanilla HTML5/CSS3/ES6+），零 npm/node 依赖，浏览器直达 `http://127.0.0.1:18080/`；
    - **大盘概览与资产中心**: 提供全局服务数、工具数、调用量、成功率、拦截率指标大盘，以及可实时搜索、参数折叠展开的工具资产卡片；
    - **一键 ACL 策略动态生效**: 在 `RouteManager` 与控制器中实现线程安全的 `POST /api/v1/tools/acl`，支持界面一键阻断 (DENY) / 放行 (ALLOW)，秒级在 `tools/list` 视图与调用链中生效；
    - **实时脱敏审计流 (SSE)**: 实现 `InMemoryAuditRingBuffer` 环形缓冲与 `GET /api/v1/audit/stream` SSE 长连接，实时滚屏呈现 `***` 脱敏入参、执行结果与耗时瀑布流；
    - **全量门禁**: 全工程测试 100% PASS，Javadoc 零警告，审核员审查 `verdict: PASS`，已正式合并入 `main`。

---

## 3. 当前进展与下一步建议 (新会话核心任务规划)

至此，**TASK-000 至 TASK-015 已全部合入 main 分支**！
- 架构资产已沉淀至 ADR 0000 ~ ADR 0011；
- 网关已具备完备的内核能力（HTTP/SSE/STDIO 多协议接入、动态前缀、不可变责任链、Docker 容器化）与内嵌 Web 可视化控制台。

### 下一个新会话的核心任务规划：TASK-016 (多租户 API Key / OAuth 凭据体系与细粒度配额治理)
1. **多租户认证与凭据绑定 (Tenant & API Key Management)**:
   - 建立网关入站调用凭据校验（如 `X-MCP-API-Key` 或 Bearer Token），映射到特定租户/Agent；
   - 实现多租户维度的工具可见性隔离与 ACL 绑定；
2. **细粒度限流与调用配额 (Rate Limiting & Quotas)**:
   - 针对不同租户或高危工具配置 QPS / 并发数限制与日配额熔断；
3. **Web 控制台租户与配额面板扩展**:
   - 在 Web 控制台中增加租户与 Key 管理面板。

---

## 4. 给新会话统筹者的开场接力指令 (直接复制)

```markdown
我是项目负责人。本项目是 mcp-manager（Java 原生 MCP 治理、安全审计与控制面网关，v1.0.0）。
Phase 1 静态扫描、Phase 2 嵌入式 Starter、Phase 3 独立网关后端内核及 TASK-015 内置轻量 Web 控制台已全部完成并合入 main。
请你严格阅读仓库根目录的核心基线文档：
1. AGENTS.md（项目宪章、三权分立与会话轮转制度）
2. docs/handover.md（换届交接记录）
3. docs/environment-baseline.md（技术与版本基线）
4. README.md 与 README.zh-CN.md（项目门面与路线图）

【你的职责】：
作为本项目的统筹中枢，请直接【启用智能体团队 (Agent Teams)】，组建 architect（架构师）、engineer（研发工程师）、reviewer（代码审核员）三位常驻队友。

【当前启动任务】：
启动 TASK-016：多租户 API Key 凭据体系与网关细粒度速率配额治理。
1. 架构师先调研并产出 ADR 0012 与设计蓝图；
2. 待我签署 Approved: Yes 后由工程师在隔离分支实现，经审核员 PASS 后合并。所有文档、注释一律使用中文。
```
