# 0011. 内置轻量 Web 控制台、Server 热启停与 Tool Picker 勾选治理架构

* **状态**: 提议中 (Proposed)
* **日期**: 2026-11-03
* **决策者**: 架构师 AI、项目负责人
* **参考文档**: AGENTS.md, docs/adr/0008-phase3-gateway-architecture-preview.md, docs/adr/0009-gateway-governance-pipeline-and-resilience.md, docs/contracts/web-console-api.json, .worklog/TASK-015.md

---

## 背景 (Context)

在经历 Phase 1（静态扫描器）、Phase 2（Starter 运行时切面）以及 Phase 3（独立控制面网关 `mcp-governance-gateway`）的建设后，网关已具备了多协议接入、路由聚合、动态前缀重写、不可变责任链治理（Metrics/Audit/ACL/Validation）以及 Docker 一键启动能力。

然而，在真实生产运维与 Agent 协作场景中，存在三大痛点需求：
1. **Server 粒度缺乏一键热启停控制 (Server Hot Toggle)**：
   - 当某个下游 MCP Server 出现故障、维护升级或存在安全风险时，运维人员需要能一键将其暂时下线，而无需修改网关配置文件并重启整个网关服务；
   - 下线后，该 Server 下的所有 Tool 必须立即从 Agent 视角的 `tools/list` 聚合视图中静默剔除，并在执行调用 `tools/call` 时直接拒绝分发。
2. **租户/Agent 可见性过滤缺乏直观操作界面 (Tool Picker 勾选联动)**：
   - 现阶段调整 Tool ACL 策略需手动编写参数或执行底层命令，缺乏直观的可视化勾选机制（最小权限原则）；
   - 用户与运维希望在 Web 控制台上通过直接【勾选/去勾选】某个 Tool，实时动态联动 `RouteManager` 与 `ToolAclPolicy`。未勾选的工具在 Agent 发起 `tools/list` 阶段被静默过滤剔除，Agent 甚至感知不到该工具的存在（防止上下文投毒与无意调用）。
3. **开源品牌与视觉门面正式统一 (Brand Alignment)**：
   - 项目品牌正式统一规范对齐为 **`mcp-governance`**（涵盖文档、Web 控制台品牌标识、OpenAPI 契约、README 等，修正过往残留的临时命名）；
   - 在网关根目录与控制台中呈现高保真科技感暗黑仪表盘，并在开源 README 中规划高质量大盘预览与多媒体展示位。
4. **技术栈约束 (Zero-Build Vanilla SPA)**：
   - 必须保持 Java 原生、轻量可移植的极简原则。控制台作为静态资源内置在网关 JAR 包中（`mcp-governance-gateway/src/main/resources/static/`），零额外 Node.js / npm 依赖，浏览器直达 `http://127.0.0.1:18080/`。

---

## 架构决策 (Decision)

### 1. 品牌与视觉门面正式统一为 `mcp-governance`

- **标识统一**：
  - 控制台标题与 Header 标识统一对齐为：`mcp-governance 控制台` (mcp-governance Gateway Web Console)；
  - 核心版本对齐当前阶段发布规划（1.0.0 / 0.1.0-SNAPSHOT）；
  - 控制面 API 契约与 JSON 规范统一标识为 `mcp-governance`；
- **README 多媒体门面**：
  - 预留并规范 README 首屏控制台实时大盘 ASCII/高保真预览结构与暗黑流光卡片展示；
  - 中英双语 README 首屏直达 30 秒 Docker 启动与 Web 控制台访问方式。

---

### 2. 下游 Server 目录、健康大盘与一键热启停架构 (Server Hot Toggle)

- **数据模型扩展 (`RouteDefinition`)**：
  - 在 `RouteDefinition` 模型中引入 `enabled` 状态字段（布尔类型，默认为 `true`）；
  - 引入运行时状态枚举/标记：`status`（`UP` / `DOWN` / `STOPPED`）；
- **动态启停控制机制 (`RouteManager`)**：
  - `RouteManager` 提供线程安全的 `setRouteEnabled(String routeId, boolean enabled)` 方法；
  - 当 `enabled = false` 时：
    1. **`ToolAggregator.aggregateTools()` 联动**：遍历各路由聚合工具时，直接跳过所有 `!route.isEnabled()` 的停用路由，使得下属 Tool 在 Agent 发起 `tools/list` 阶段立即完全隐形下线；
    2. **`DefaultGatewayExecutionAdapter` 联动**：在数据面执行 `tools/call` 请求匹配到该路由时，若路由处于停用状态，直接抛出 `McpAccessDeniedException` 或返回服务停用错误，拒绝调用分发；
- **控制面端点**：
  - `POST /api/v1/routes/status`：
    - 入参：`{ "routeId": "k8s-service", "enabled": false }`
    - 出参：`{ "success": true, "routeId": "k8s-service", "enabled": false, "status": "STOPPED", "message": "Server 路由已热停用" }`
  - `GET /api/v1/routes`：
    - 返回每个 Server 路由的 `enabled`、`status`、协议与工具统计。

---

### 3. 可视化租户/Agent 可见性过滤 (Tool Picker 勾选联动)

- **最小权限暴露与静默剔除原则**：
  - 遵循 OWASP MCP02（权限过度蔓延）防御基线。对于组织内挂载的众多 MCP 工具，默认或按需实行最小权限暴露；
  - 未勾选激活的工具，在 Agent 发起 `tools/list` 协议握手时**直接静默剔除**，减少 Agent 上下文 Token 消耗并消除幻觉调用风险；
- **Tool Picker 勾选控制 API (`POST /api/v1/tools/acl`)**：
  - 扩展原有 API 契约，支持直观的 `enabled` 勾选参数：
    ```json
    {
      "toolName": "k8s_delete_pod",
      "enabled": false
    }
    ```
  - 后端映射逻辑：
    - `enabled: true` 等价于 ACL 动作 `ALLOW`（从 `deniedTools` 移出并纳入 `allowedTools`）；
    - `enabled: false` 等价于 ACL 动作 `DENY`（从 `allowedTools` 移出并纳入 `deniedTools`）；
    - 兼容原有的 `action`（`ALLOW` / `DENY` / `RESET`）字段，保持向后兼容；
- **`WebConsoleController` 与 `GET /api/v1/tools` 扩展**：
  - 返回项中包含：
    - `enabled`: `boolean`（当上游 Server 启用且自身 ACL 为 `ALLOWED` 时为 `true`，否则为 `false`，供前端 Checkbox 绑定）；
    - `upstreamEnabled`: `boolean`（标识父级 Server 是否启用）；
    - `aclStatus`: `ALLOWED` / `DENIED`；
- **前端交互实现**：
  - 在 Web 控制台表格/卡片中提供直观的 Switch 开关或 Checkbox 复选框；
  - 点击即可秒级向后端提交变更并局部重显状态，支持搜索过滤与批量观察。

---

### 4. 实时脱敏审计流水线瀑布流 (SSE + RingBuffer)

- **高并发内存环形缓冲 (`InMemoryAuditRingBuffer`)**：
  - 预分配固定容量（默认 200 条）并发安全双端队列，自动淘汰最旧事件，内存占用恒定有界；
  - 集成 `McpAuditPublisher` SPI，在拦截器完成审计打码后自动写入；
- **Server-Sent Events (SSE) 长连接**：
  - 端点：`GET /api/v1/audit/stream`，媒体类型 `text/event-stream`；
  - 采用 Spring WebFlux `Flux<ServerSentEvent<Map<String, Object>>>` 响应式单向广播；
  - 默认 15 秒保活 Ping 注释包，防止浏览器或反向代理超时断连；
- **前端瀑布流呈现**：
  - 原生 `EventSource` 监听，收到新事件时以优雅动效插入顶部，支持暂停/继续与清屏；
  - 严格展示已脱敏入参（`SensitiveDataMasker` 打码值），防止凭据泄漏。

---

## 架构后果 (Consequences)

### 正向影响 (Positive)
1. **运维可控性大幅飞跃**：具备了 Server 级（粗粒度故障隔离）与 Tool Picker 勾选级（细粒度权限收敛）的双层动态控制面；
2. **零打包与零侵入交付**：控制台纯静态嵌入网关，零 Node.js / npm 依赖，开箱即用；
3. **品牌标准化**：统一使用 `mcp-governance`，扫清过往命名残留，提升开源影响力与专业度。

### 限制与权衡 (Trade-offs)
1. **纯内存策略持久化扩展点**：当前控制面热修改默认生效于运行期内存；生产环境长期持久化需依赖外置 `mcp-routes.json` 回写扩展。
2. **单机 SSE 连接池容量**：SSE 适合管理端点调试，大并发集群管理建议配合专用日志平台（如 ELK / Loki）。

---

## 替代方案评估 (Alternatives Considered)

1. **方案 A：引入微前端或 React 构建独立管理后台**：
   - 严重破坏《AGENTS.md》中规定的轻量、Java 原生与无额外编译流水线原则，放弃。
2. **方案 B：仅通过配置文件修改并热重载实现启停**：
   - 用户需要登录服务器修改 JSON/YAML 文件，无法满足管理员浏览器一键操作的直观敏捷诉求，放弃。
