# TASK-015 实现蓝图：网关轻量 Web 控制台与控制面 API

## 1. 任务背景与目标

本蓝图为 `TASK-015`（轻量 Web 控制台与开源多媒体展示）提供清晰、模块化、可验证的技术架构与落地指南。
目标是在不引入任何 Node.js / npm 编译依赖的前提下，在 `mcp-governance-gateway` 模块中构建自包含的现代化单页 Web 控制台（Web Admin UI），并提供轻量级响应式 REST/SSE 控制面后端 API。

严格遵循《AGENTS.md》与《.agents/architect.md》三权分立规范：
- 架构师产出设计蓝图、ADR 与 API 契约；
- 工程师在隔离分支中完成实现并保证单元测试通过；
- 审核员执行门禁审查，严禁绕过安全基线。

---

## 2. 交付清单与文件映射

| 序号 | 交付文件类别 | 文件路径 | 职责与内容要求 |
| :--- | :--- | :--- | :--- |
| 1 | **架构决策记录** | `docs/adr/0011-gateway-web-console.md` | 记录 Web 控制台架构选型与安全设计决策 |
| 2 | **API 契约** | `docs/contracts/web-console-api.json` | OpenAPI 3.0 控制面 REST/SSE 接口规范定义 |
| 3 | **静态控制台页面** | `mcp-governance-gateway/src/main/resources/static/index.html` | 现代化 Dark 主题自包含单页，含大盘概览、工具资产卡片、一键 ACL 开关、SSE 审计流 |
| 4 | **控制面控制器** | `io.github.chukui2005.mcpgovernance.gateway.controller.WebConsoleController` | 提供 `/api/v1/overview`、`/api/v1/tools`、`/api/v1/tools/acl`、`/api/v1/audit/events`、`/api/v1/audit/stream` |
| 5 | **审计内存环形队列** | `io.github.chukui2005.mcpgovernance.gateway.audit.InMemoryAuditRingBuffer` | 线程安全的固定容量环形队列与 SSE Flux 广播器 |
| 6 | **动态 ACL 管理扩展** | `RouteManager` / `RouteGovernanceConfig` 联动方法 | 线程安全的动态策略修改与生效逻辑 |
| 7 | **集成与单元测试** | `src/test/java/.../WebConsoleControllerTest.java` | 验证各控制面端点、ACL 修改实时生效与 SSE 事件发射 |

---

## 3. 详细架构设计与实现规约

### 3.1 前端单页架构设计 (`src/main/resources/static/index.html`)

- **设计规范**：
  - 风格：现代化深色（Cyberpunk / Slate Dark）科技质感，适配开发者与运维人员审美；
  - 布局：
    1. **顶部导航栏 (Navbar)**：展示网关名称 `MCP Governance Gateway`、版本标识 `v0.3.0`、服务状态指示灯（绿色脉冲 UP）、实时时钟；
    2. **总体大盘 (Overview Banner)**：
       - 下游服务数（Upstream Count）
       - 聚合工具数（Aggregated Tools）
       - 今日调用量（Total Invocations）
       - 治理阻断数（Blocked Calls）
       - 平均耗时与成功率仪表卡片
    3. **核心功能选项卡 (Tab Navigation)**：
       - `[工具资产与策略中心]`：卡片/表格化展示所有工具。包含：工具名、所属服务、描述、参数结构展开、ACL 状态（放行/禁用标签）。每个工具右侧提供 `[一键禁用]` / `[一键放行]` 按钮，点击即发起 API 调用并无刷新更新状态。
       - `[安全审计流水流 (Audit Stream)]`：瀑布流卡片展示。每条记录显示时间戳、工具名、调用者角色、脱敏参数预览（`***` 打码效果）、执行结果状态（SUCCESS / BLOCKED / FAILED）以及耗时。支持原生 SSE 实时滚屏追加，提供暂停/清屏按钮。
       - `[下游路由拓扑 (Routes Explorer)]`：列表展示已注册的下游服务及其运行协议（HTTP, SSE, STDIO）、前缀映射规则与基准配置。
- **技术实现**：
  - 原生 CSS3 Flexbox/Grid 布局，CSS 变量定义主题色系；
  - 原生 JavaScript (ES6+)，使用 `fetch` 处理 REST 请求，使用 `new EventSource('/api/v1/audit/stream')` 监听服务端事件；
  - 错误处理机制：当后端服务不可用时，界面提供友好的黄色/红色浮层提示，支持断线自动重连。

---

### 3.2 控制面响应式控制器设计 (`WebConsoleController`)

- **类名**：`io.github.chukui2005.mcpgovernance.gateway.controller.WebConsoleController`
- **注解**：`@RestController`，`@RequestMapping("/api/v1")`
- **依赖注入**：
  - `RouteManager`：读取路由与执行动态 ACL 修改
  - `ToolAggregator`：获取全量聚合工具清单
  - `McpMetricsCollector`：读取运行时吞吐、指标与耗时数据
  - `InMemoryAuditRingBuffer`：读取近期历史审计记录与订阅实时 SSE Flux

#### 核心端点实现规格：

1. **`GET /api/v1/overview`**
   - 返回结构包含：
     - `gateway`: `{ "status": "UP", "uptimeMs": 12345, "version": "0.1.0-SNAPSHOT" }`
     - `metrics`: 聚合调用总数、成功总数、拒绝总数、校验失败数、成功率与阻断率
     - `counts`: 下游服务数、工具总数

2. **`GET /api/v1/tools`**
   - 遍历 `ToolAggregator.aggregateTools()` 获取可用工具；
   - 结合各路由的 `RouteGovernanceConfig` 解析各工具当前的 ACL 策略状态（`ALLOWED` 或 `DENIED`）；
   - 返回工具元数据列表（聚合名称、下游 ID、描述、参数 Schema、ACL 状态）。

3. **`POST /api/v1/tools/acl`**
   - 入参 RequestBody：
     ```json
     {
       "toolName": "demo_read_file",
       "action": "DENY" // 枚举: ALLOW, DENY, RESET
     }
     ```
   - 逻辑：
     - 通过 `routeManager.matchByToolName(toolName)` 定位对应下游路由；
     - 获取原路由的 `RouteGovernanceConfig`，并更新 `allowedTools` / `deniedTools`；
     - 返回更新后的策略状态结果；
     - 校验：参数为空或路由未匹配返回 400/404。

4. **`GET /api/v1/audit/events`**
   - 返回 `List<McpAuditEvent>`（近期固定条数，如最近 50 条），保证敏感参数已由 `SensitiveDataMasker` 打码。

5. **`GET /api/v1/audit/stream`**
   - `produces = MediaType.TEXT_EVENT_STREAM_VALUE`
   - 返回 `Flux<ServerSentEvent<McpAuditEvent>>`，实时推送新产生的治理审计事件；
   - 带有心跳机制（例如每 15 秒发送空注释或保活事件，防止 HTTP 代理中间件切断连接）。

---

### 3.3 审计事件流中继器 (`InMemoryAuditRingBuffer`)

- **职责**：
  - 实现 Spring 统一事件监听器 `@EventListener` 或作为 `McpAuditPublisher` 的广播通道；
  - 维护一个固定容量（如 200 条）的并发安全环形缓冲区（`ConcurrentLinkedDeque` 或带有容量上限的列表）；
  - 基于 Reactor 的 `Sinks.Many<McpAuditEvent> sink = Sinks.many().multicast().directBestEffort()`；
  - 当收到新事件时，存入环形队列，并通过 `sink.tryEmitNext(event)` 分发给所有活跃的 SSE 客户端连接。

---

## 4. 工程师实施顺序与任务拆解

1. **第 1 步：创建环形队列组件与事件广播机制**
   - 在 `mcp-governance-gateway` 中增加 `InMemoryAuditRingBuffer`，接入 `McpAuditPublisher`，编写单测验证多并发下的队列截断与 Sink 广播。
2. **第 2 步：实现动态 ACL 修改逻辑**
   - 在 `RouteManager` 中增加线程安全的方法更新特定路由的 `RouteGovernanceConfig`，编写单元测试验证更新后 `matchByToolName` 与 `ToolAggregator` 的过滤行为。
3. **第 3 步：编写 `WebConsoleController` 控制面接口**
   - 编写 REST 控制器与 SSE 端点，注入各服务组件，编写 Controller WebFlux 集成测试（使用 `WebTestClient`）。
4. **第 4 步：编写自包含静态控制台页面 (`index.html`)**
   - 在 `mcp-governance-gateway/src/main/resources/static/index.html` 中编写现代化 Dark 风格页面与原生 JS 交互逻辑；
   - 确保直接打开或在浏览器访问 `http://127.0.0.1:18080/` 时完美呈现大盘、工具清单与实时审计流。
5. **第 5 步：端到端与 CI 构建验证**
   - 运行全量 `mvn clean verify`，验证所有模块与已有 130+ 测试不受任何破坏性影响。
