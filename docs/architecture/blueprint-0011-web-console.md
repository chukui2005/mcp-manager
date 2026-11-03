# TASK-015 设计蓝图：内置轻量 Web 控制台、Server 热启停与 Tool Picker 勾选治理升级

## 1. 任务背景与目标

本蓝图为 `TASK-015`（Web 控制台增强、Server 热启停、Tool Picker 勾选联动及品牌升级）提供全流程设计与工程实施指南。
本任务在既有嵌入式控制台基础上，深化控制面交互维度：
1. **Server 目录与健康大盘**：可视化展示已注册的 MCP Server 及其在线状态，提供一键热启停接口与前端开关；停止后该 Server 下的 Tool 立即下线并不再参与聚合和调用。
2. **租户/Agent 可见性过滤 (Tool Picker)**：工具表格/卡片提供【勾选框】自由开启/禁用 Tool，未勾选的工具在 Agent 发起 `tools/list` 阶段直接静默剔除（最小权限暴露）；支持基于 `RouteManager` 与动态 `ToolAclPolicy` 实时生效。
3. **实时审计流水线瀑布流**：保持并优化基于 `InMemoryAuditRingBuffer` 与 SSE 的实时滚屏脱敏流水线展示。
4. **开源视觉与品牌规范升级**：统一项目品牌为 `mcp-governance`，更新文档与 UI 品牌标识，规划 README 门面多媒体展示。

严格遵守《AGENTS.md》与《.agents/architect.md》三权分立规范：
- 架构师负责产出蓝图、ADR 与 API 契约，严禁编写业务实现代码；
- 工程师在隔离分支中完成实现并确保全量测试通过；
- 审核员执行门禁审查，签署 PASS。

---

## 2. 核心架构设计与联动机制

```
                                      +-----------------------------------------+
                                      |   Browser (Web Admin Console)           |
                                      |   http://127.0.0.1:18080/               |
                                      +--------------------+--------------------+
                                                           |
                          REST: POST /api/v1/routes/status | REST: POST /api/v1/tools/acl
                          (Server 热启停)                   | (Tool Picker 勾选/去勾选)
                                                           v
+-----------------------------------------------------------------------------------------------------+
| mcp-governance-gateway 控制面                                                                        |
|                                                                                                     |
|  [WebConsoleController]                                                                             |
|      |                                                                                              |
|      +---> [RouteManager]                                                                           |
|                |                                                                                    |
|                +---> route.setEnabled(false) ---> 下游 Server 热停用                                 |
|                |                                                                                    |
|                +---> route.updateToolAcl(tool, DENY) ---> 动态更新 RouteGovernanceConfig            |
|                                                                                                     |
|  [ToolAggregator]                                                                                   |
|      |                                                                                              |
|      +---> 检查 route.isEnabled()                                                                   |
|      |         |                                                                                    |
|      |         +--> false: 直接跳过整个 Server，下属所有 Tool 立即隐形下线                           |
|      |                                                                                              |
|      +---> 检查 ToolAclPolicy.isAllowed(toolName)                                                   |
|                |                                                                                    |
|                +--> false (未勾选): tools/list 阶段静默剔除 (最小权限暴露)                          |
|                                                                                                     |
|  [DefaultGatewayExecutionAdapter]                                                                   |
|      |                                                                                              |
|      +---> tools/call 分发阶段二次校验:                                                             |
|                若 route.isEnabled() == false 或 isAllowed == false                                  |
|                直接抛出 McpAccessDeniedException 阻断执行                                           |
+-----------------------------------------------------------------------------------------------------+
```

---

## 3. 控制面 API 与数据结构设计

### 3.1 契约端点规格

| 端点 | HTTP 方法 | 功能说明 | 核心入参 / 返回模型 |
| :--- | :--- | :--- | :--- |
| `/api/v1/overview` | `GET` | 网关运行大盘概览 | 返回网关状态、版本、品牌、在线 Server 数、总 Server 数、激活 Tool 数、总调用量与拦截率 |
| `/api/v1/routes` | `GET` | 下游 Server 目录与运行拓扑 | 返回已注册 Server 清单，含 `id`、`protocol`、`prefix`、`uri`、`enabled`、`status`、ACL 模式 |
| `/api/v1/routes/status` | `POST` | **一键热启停指定 Server** | RequestBody: `{"routeId": "k8s-svc", "enabled": false}`；返回最新状态与提示信息 |
| `/api/v1/tools` | `GET` | 全量工具资产与勾选状态 | 返回所有工具列表，含 `name`、`rawName`、`upstreamId`、`upstreamEnabled`、`aclStatus`、`enabled` |
| `/api/v1/tools/acl` | `POST` | **Tool Picker 勾选动态控制** | RequestBody: `{"toolName": "k8s_delete", "enabled": false}` 或 `{"toolName":"...", "action":"DENY"}` |
| `/api/v1/audit/events` | `GET` | 近期脱敏审计事件快照 | Query: `limit=50`；返回格式化的脱敏审计记录数组 |
| `/api/v1/audit/stream` | `GET` | SSE 实时脱敏审计流瀑布流 | `text/event-stream` 长连接，广播包含脱敏入参、耗时、状态的审计流水 |

---

## 4. 工程师实施清单与文件映射

为指导研发工程师精准实现，明确划分主要修改文件与次要修改文件：

### 4.1 主要修改文件 (Primary Implementation Scope)
1. **`mcp-governance-gateway/src/main/java/io/github/chukui2005/mcpgovernance/gateway/config/RouteDefinition.java`**
   - 增加字段：`private volatile boolean enabled = true;`
   - 提供 Getter / Setter 以及对应的构造方法扩展。
2. **`mcp-governance-gateway/src/main/java/io/github/chukui2005/mcpgovernance/gateway/route/RouteManager.java`**
   - 增加线程安全的 `public synchronized boolean setRouteEnabled(String routeId, boolean enabled)` 方法；
   - 更新 `size()` 或增加 `activeSize()`，方便大盘区分全部与在线 Server 数量。
3. **`mcp-governance-gateway/src/main/java/io/github/chukui2005/mcpgovernance/gateway/aggregation/ToolAggregator.java`**
   - 在 `aggregateTools()` 与 `fetchAndProcessRouteTools()` 中，前置判断：
     ```java
     if (!route.isEnabled()) {
         log.debug("下游路由 [{}] 当前处于停用状态，跳过工具聚合", route.getId());
         return Mono.just(Collections.emptyList());
     }
     ```
   - 确保停用的 Server 下的所有 Tool 立即从 `tools/list` 视图中剔除。
4. **`mcp-governance-gateway/src/main/java/io/github/chukui2005/mcpgovernance/gateway/adapter/DefaultGatewayExecutionAdapter.java`**
   - 在 `executeReactive()` 分发路由时，前置校验：
     ```java
     if (!route.isEnabled()) {
         return Mono.error(new McpAccessDeniedException("下游 Server [" + route.getId() + "] 当前处于停用状态，拒绝调用"));
     }
     ```
5. **`mcp-governance-gateway/src/main/java/io/github/chukui2005/mcpgovernance/gateway/controller/WebConsoleController.java`**
   - 品牌统一：在大盘返回中输出 `brand: "mcp-governance"`，统计在线与停用路由数；
   - 新增端点：`POST /api/v1/routes/status`，处理 Server 热启停；
   - 扩展端点：`POST /api/v1/tools/acl`，兼容 `enabled: true/false` 字段；
   - 扩展端点：`GET /api/v1/tools`，输出 `enabled`（当且仅当上游启停状态为 `true` 且 ACL 为 `ALLOWED` 时为 `true`）；
   - 扩展端点：`GET /api/v1/routes`，输出 `enabled` 与当前运行状态。
6. **`mcp-governance-gateway/src/main/resources/static/index.html`**
   - 品牌标识更新：统一对齐为 `mcp-governance`；
   - 下游 Server 拓扑卡片中增加【一键热启停开关（Switch / Toggle Button）】；
   - 工具管理列表改为 **Tool Picker** 模式：首列显示醒目的 Checkbox 复选框，勾选即启用（放行），取消勾选即禁用（剔除）；未启用的上游 Server 下属工具置灰并提示上游已停用；
   - 优化实时审计流瀑布流滚屏动画与样式。

### 4.2 次要修改与测试文件 (Secondary Scope & Tests)
1. **`mcp-governance-gateway/src/test/java/io/github/chukui2005/mcpgovernance/gateway/controller/WebConsoleControllerTest.java`**
   - 增加 Server 热启停端点 `POST /api/v1/routes/status` 测试；
   - 增加 Tool Picker 勾选 `POST /api/v1/tools/acl` 带有 `enabled: true/false` 的测试；
   - 验证 Server 停用后工具列表状态与路由列表状态返回。
2. **`mcp-governance-gateway/src/test/java/io/github/chukui2005/mcpgovernance/gateway/route/RouteManagerTest.java`**
   - 增加 `setRouteEnabled` 启停状态流转单测。
3. **`mcp-governance-gateway/src/test/java/io/github/chukui2005/mcpgovernance/gateway/aggregation/ToolAggregatorTest.java`**
   - 验证停用路由的工具不会出现在聚合结果中。
4. **`README.md` & `README.zh-CN.md`**
   - 统一品牌标识为 `mcp-governance`；
   - 预留/更新首屏控制台大盘 ASCII 预览及高保真控制台介绍。

---

## 5. 工程师编码与协作顺序规范

```
[步骤 1: 模型与核心引擎扩展]
  -> 修改 RouteDefinition 增加 enabled 字段
  -> 在 RouteManager 增加 setRouteEnabled 接口
  -> 在 ToolAggregator 与 ExecutionAdapter 增加 enabled 校验拦截
  -> 运行 RouteManagerTest & ToolAggregatorTest 验证单测通过

[步骤 2: 控制面 Controller 契约对齐]
  -> 在 WebConsoleController 新增 POST /api/v1/routes/status
  -> 扩展 POST /api/v1/tools/acl 兼容 enabled 参数
  -> 扩展 GET /api/v1/tools 与 GET /api/v1/routes 输出完整状态
  -> 编写并更新 WebConsoleControllerTest 验证所有端点契约

[步骤 3: 嵌入式静态页面 index.html 交互升级]
  -> 统一品牌对齐为 mcp-governance
  -> 实现 Server 拓扑卡片的热启停开关
  -> 实现 Tool Picker 勾选框联动交互与状态呈现
  -> 验证浏览器直接访问 http://127.0.0.1:18080/ 的可用性

[步骤 4: 门禁与交付验证]
  -> 运行全工程 mvn clean verify，确保全部测试绿灯，Javadoc 零警告
  -> 提交 PR 等待 Reviewer 审查与人类 Approved: Yes
```
