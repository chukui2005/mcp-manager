# TASK-015: 网关内置轻量 Web 控制台 (Web Admin UI) 与开源多媒体门面升级

## 1. 任务元数据

- **任务 ID**: TASK-015
- **任务目标**: 为 `mcp-governance-gateway` 提供零外部依赖的内嵌轻量自包含 Web 控制台（Web Admin UI），并通过现代化卡片、指标图表、一键 ACL 开关、实时审计流大幅提升治理可视度与操作体验；同时规划开源多媒体门面（高保真大盘演示与 README 多媒体展示）。
- **当前状态**: `IN_REVIEW`
- **分配角色**:
  - 架构师 (Architect): 调研当前网关、设计 REST/SSE 扩展端点契约、产出 ADR 0011 与设计蓝图，规划 README 多媒体资产
  - 研发工程师 (Engineer): 在隔离分支构建静态资源（单页应用，自包含无额外外部构建打包限制）、实现网关控制面管理 API、编写测试并保证 CI 全绿
  - 代码审核员 (Reviewer): 只读审查安全、架构一致性与代码质量，给出 verdict
  - 人类决策者: 签署 `Approved: Yes` 批准进入实现，最终合并入 `main`
- **人类批准**: Approved: Yes

---

## 2. 状态机流转记录

| 阶段 | 状态 | 责任人 | 状态前置条件 / 结果说明 | 记录时间 |
| --- | --- | --- | --- | --- |
| 1 | DESIGN | Architect | 架构调研、ADR 0011 与 Blueprint 0011 撰写中 | 2026-11-03 |
| 2 | READY | Architect / Human | 契约与蓝图就绪，人类签署 `Approved: Yes` | 2026-11-03 |
| 3 | IN_REVIEW | Engineer | 隔离分支代码与测试实现完成，全量 mvn clean verify 绿灯通过，待审核员审查 | 2026-11-03 |
| 4 | READY_TO_LAND | Reviewer | 代码审查通过，`verdict: PASS` | 2026-11-03 |
| 5 | DONE | Human | 合并入 `main`，完成换届交接记录 | 2026-11-03 |

---

## 3. 验收标准 (Acceptance Criteria)

1. **内嵌自包含 Web 控制台**:
   - 访问 `http://127.0.0.1:18080/` 或 `http://127.0.0.1:18080/index.html` 即可直达控制台；
   - 静态资源自包含在 `mcp-governance-gateway/src/main/resources/static/`，无必须外部 npm/node 运行时服务；
   - 包含工具大盘总览 (Overview)、工具与 ACL 策略中心 (Tools & ACL Policy Explorer)、脱敏审计实时流检索 (Audit Viewer)。
2. **轻量管理与控制 API**:
   - 提供工具清单与动态 ACL 策略修改接口（支持一键禁用/放行特定工具）；
   - 提供实时或轮询/SSE 格式的脱敏审计事件流接口；
   - 与现有网关核心 `RouteManager`、`ToolAggregator`、`AuditMcpInterceptor` 等深度协同。
3. **安全与质量门禁**:
   - 控制面 API 遵循 OWASP 安全基线，严格参数校验；
   - 审计日志在返回前端时必须保证脱敏（`***` 打码）；
   - `mvn clean verify` 全项目通过，单元测试/集成测试零报错，Javadoc 零警告。
4. **开源门面多媒体规划**:
   - 规划高保真控制台架构图与 README 预览展示位，更新中英双语 README 门面。

---

## 4. 工程师实现摘要 (Implementation Summary)

- **后端模型与启停路由扩展**：
  - `RouteDefinition.java`：新增 `enabled` 字段（默认 `true`）及 getter/setter，`equals/hashCode` 包含 `enabled`。
  - `RouteManager.java`：新增 `activeSize()` 及线程安全方法 `setRouteEnabled(String routeId, boolean enabled)`。
  - `ToolAggregator.java`：在工具聚合与拉取阶段增加 `if (!route.isEnabled())` 判断，停用的 Server 下属工具直接静默剔除。
  - `DefaultGatewayExecutionAdapter.java`：在分发调用执行前校验 `route.isEnabled()`，若已停用则拒绝分发并抛出友好阻断 `McpAccessDeniedException`。
- **控制面 Controller 契约对齐**：
  - `WebConsoleController.java`：
    - 新增 `POST /api/v1/routes/status`，支持 `{ "routeId": "...", "enabled": true/false }` 热启停下游服务；
    - 扩展 `GET /api/v1/routes`，输出 `enabled` 与当前运行状态 `status` (`UP` / `STOPPED`)；
    - 扩展 `GET /api/v1/overview`，输出品牌 `brand: "mcp-governance"`、在线下游服务数 `activeUpstreamCount` 与总服务数；
    - 扩展 `GET /api/v1/tools`，输出各工具所属下游在线状态 `upstreamEnabled` 以及 Tool Picker 激活状态 `enabled`；
    - 扩展 `POST /api/v1/tools/acl`，兼容 `enabled: true/false`（`true` 放行，`false` 阻断），与前端 Tool Picker 勾选联动。
- **内嵌 Web 控制台单页 UI 升级**：
  - `index.html`：品牌全面对齐 `mcp-governance`；大盘统计卡片增加下游服务在线数与工具激活数展示；下游路由拓扑卡片增加【一键停用/启用 Server】交互按钮；工具列表升级为【Tool Picker】模式，首列 Checkbox 勾选即启用（放行），取消勾选即禁用（剔除），当对应 Server 停用时下属工具自动置灰且不可操作；审计流保持 SSE 实时瀑布流展示。
- **中英双语 README 门面规范对齐**：
  - 更新 `README.md` 与 `README.zh-CN.md` 首屏品牌为 `mcp-governance`，增加 Web 控制台大盘与 Tool Picker 勾选治理 ASCII 高保真多媒体预览与功能说明。
- **自动化测试与质量门禁验证**：
  - 新增/更新 `WebConsoleControllerTest`、`RouteManagerTest`、`ToolAggregatorTest` 等单测；
  - 运行 `mvn test` 130+ 测试全绿（0 failures, 0 errors），`mvn compile javadoc:javadoc` 零警告。

