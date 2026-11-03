# 0011. 嵌入式轻量 Web 控制台架构与响应式控制面 API 设计

* **状态**: 提议中 (Proposed)
* **日期**: 2026-11-03
* **决策者**: 架构师 AI、项目负责人
* **参考文档**: AGENTS.md, docs/adr/0008-phase3-gateway-architecture-preview.md, docs/adr/0009-gateway-governance-pipeline-and-resilience.md, docs/contracts/web-console-api.json, .worklog/TASK-015.md

---

## 背景 (Context)

在经历 Phase 1（静态扫描器）、Phase 2（Starter 运行时切面）以及 Phase 3（独立控制面网关 `mcp-governance-gateway`）的建设后，网关已具备了多协议接入、路由聚合、动态前缀重写、不可变责任链治理（Metrics/Audit/ACL/Validation）以及 Docker 一键启动能力。

然而，当前网关存在以下核心体验与运维短板：
1. **控制面黑盒化，缺乏直观的可视化界面**：
   - 当前运维人员与开发者查看工具资产、指标聚合和审计日志时，只能依赖 curl 访问 Actuator 端点（`/actuator/mcpGovernance`）或查阅控制台日志，调试门槛高，缺乏一览式监控体验；
2. **缺乏动态交互式策略管控手段**：
   - 现阶段调整 Tool ACL 策略需手动修改配置文件并重载，无法在 Web UI 上实现“一键熔断/放行”某个高危工具；
3. **技术栈选型约束与避免外部打包依赖**：
   - 根据《AGENTS.md》使命与不可协商约束，网关必须保持 Java 原生、轻量可移植的极简原则。引入复杂的 Node.js / Webpack / Vite / React 前端编译流水线将严重拖慢构建速度，破坏纯 Maven / Docker 的单一步骤交付体验；
4. **开源社区展示度不足**：
   - GitHub 项目门面急需现代化控制台大盘展示图与架构拓扑，直观传递“企业级 MCP 治理中心”的视觉冲击力。

---

## 架构决策 (Decision)

### 1. 前端技术栈：自包含轻量纯原生单页架构 (Zero-Build Vanilla SPA)

- **物理位置**：
  - 静态资产统一置于 `mcp-governance-gateway/src/main/resources/static/` 目录下；
  - 核心入口文件为 `index.html`（内联或同级引用极简样式与无打包 ES 模块 JS，单文件或零依赖多文件静态分发）；
- **零外部编译依赖 (Zero-Build)**：
  - 严禁引入 Node.js、pnpm、Vite、npm 等外部打包工具；
  - 页面采用现代化 HTML5、CSS 原生变量（Dark/Modern 科技主题）以及原生 Vanilla JavaScript (Fetch API, EventSource, DOM API)；
  - 使用内嵌轻量 SVG 图标与原生 Canvas/SVG 绘制简易趋势图（折线图/柱状图），实现 100% 自包含与离线可用，不引入任何重型外部三方库；
- **访问入口**：
  - 当浏览器直接访问网关根路径 `http://127.0.0.1:18080/` 时，Spring WebFlux 默认将静态资源映射至 `index.html`，实现开箱即用的直达体验。

---

### 2. 控制面后端 API 设计与分层隔离

控制面 REST API 与 MCP 数据面（JSON-RPC `POST /mcp/v1/rpc`）保持严格分层：
- **API 统一路由前缀**：`/api/v1`；
- **核心交互端点契约**：
  1. `GET /api/v1/overview`：大盘总体摘要。聚合下游 Upstream Server 数量、在线状态、聚合 Tool 数量、累计调用数、成功率、拦截率及平均耗时；
  2. `GET /api/v1/tools`：全量工具资产发现视图。返回所有聚合后的工具名（含前缀）、所属下游 Server、原始名称、描述、JSON Schema 参数定义、当前 ACL 状态（ALLOWED / DENIED / INHERITED）以及近实时调用量；
  3. `POST /api/v1/tools/acl`：一键 ACL 策略动态下发。入参包含 `upstreamId`、`toolName`、`action`（`ALLOW` / `DENY` / `RESET`），动态操作路由治理配置中的 `allowedTools` 与 `deniedTools`，实时影响随后的 `tools/list` 发现与 `tools/call` 执行；
  4. `GET /api/v1/audit/events`：近期脱敏审计记录查询（返回最新 50~100 条脱敏事件快照）；
  5. `GET /api/v1/audit/stream`：基于 Server-Sent Events (SSE) 的实时流式审计瀑布流，媒体类型为 `text/event-stream`，前端通过原生 `EventSource` 建立长连接，实时渲染调用动态；
  6. `GET /api/v1/routes`：下游路由详情列表，包含协议（HTTP/SSE/STDIO）、URI、状态与治理模式。

---

### 3. 动态 ACL 状态管理与并发安全保证

- **内存动态策略下发**：
  - 由 `RouteManager` 扩展或委托路由配置动态更新机制，对指定下游路由的 `RouteGovernanceConfig` 进行线程安全修改（采用 `synchronized` 或写时复制结构）；
  - 当下发 `DENY` 指令时，将目标工具追加至对应下游的 `deniedTools`，同时在 `ToolAggregator.aggregateTools()` 视图中实时剔除，并在 `DefaultGatewayExecutionAdapter` 中实时拦截执行；
  - 动态策略在服务运行期生效，并保留持久化到外置 JSON/YAML 文件的扩展点。

---

### 4. 控制面安全性基线 (OWASP Compliance)

- **严格只读控制与同源防护**：
  - API 与控制台遵循 OWASP 安全基线，默认仅绑定 `127.0.0.1` 本地回环接口；
  - CORS 策略继承已有的严格配置，禁止任意第三方 Origin 跨域调用控制面 API；
- **审计记录脱敏保证**：
  - 控制台端点仅对外暴露已经过 `SensitiveDataMasker` 打码脱敏的 `McpAuditEvent`，严禁在控制台 API 中返回未经打码的 rawArguments / rawCredentials。

---

## 架构后果 (Consequences)

### 正向影响 (Positive)
1. **零学习成本与极速运维**：通过现代化 Web 大盘一眼看清全网 MCP 工具资产、健康度与实时调用轨迹；
2. **极简构建与容器一致性**：无任何 Node/npm 构建环节，Maven `mvn package` 与 Docker 构建流程 100% 保持纯粹与秒级打包；
3. **安全闭环与动态阻断**：发现恶意调用或高危工具时，管理员可在 Web 界面秒级一键阻断（ACL Deny），无需重启网关进程。

### 负向考量与限制 (Trade-offs)
1. **纯原生前端无组件库支持**：需编写原生 CSS 与 DOM 操作代码，UI 交互需保持紧凑专注，不做过度复杂的企业级中后台逻辑；
2. **内存事件容量约束**：实时审计 SSE 与近期列表基于内存 RingBuffer 暂存（默认保留最近 200 条），防止高并发时内存无节制膨胀。

---

## 替代方案评估 (Alternatives Considered)

1. **方案 A：采用 React / Vue 独立前端工程，并通过 Maven 前端插件打包**：
   - *缺陷*：引入 Node/npm 庞大环境依赖，CI 耗时显著增加，脱离项目“Java 原生、轻量可嵌入”的初衷；放弃该方案。
2. **方案 B：仅通过 Actuator 端点暴露，不提供内嵌 Web UI**：
   - *缺陷*：用户体验差，缺乏交互式一键 ACL 阻断能力，不符合开源项目直观展示门面的需求；放弃该方案。
