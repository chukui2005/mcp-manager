# 0000. 演练协作工作流与 Phase 1 架构基线

* **状态**: 已采纳 (Accepted)
* **日期**: 2026-10-29
* **决策者**: 架构师 AI、人类负责人
* **参考文档**: AGENTS.md, docs/environment-baseline.md

---

## 背景 (Context)

在 `mcp-manager` 项目启动之初，我们面临两大基础架构与协作层面的挑战：

1. **多 Agent 协作的安全与权限隔离验证**：
   本项目受严格的多 Agent 治理宪章（`AGENTS.md`）约束。在复杂的自主智能体协作流程中，如果缺乏明确的工具权限边界和严格的交接门禁，极易引发越权修改文件、范围蔓延和未经审查的误合并。在正式实现 Phase 1 的生产级 Java 模块之前，必须通过一次端到端的“空闭环演练”（TASK-000），实测并验证：
   - 工具权限的硬隔离（架构师只读、工程师在隔离分支工作树、审核员只读审查提意见、人类拥有最终合并决定权）。
   - 状态机的确定性流转（`DESIGN → READY → IN_REVIEW → READY_TO_LAND → DONE`）。
   - 机器可校验的人类批准机制（独立一行 `Approved: Yes`）。

2. **Phase 1 的技术定位与安全底线**：
   Phase 1 的核心目标是交付轻量级、可嵌入、Java 原生的静态扫描 CLI（`mcp-governance-cli`）与 GitHub Action（`mcp-governance-action`），用于对 MCP Server 配置文件与 Tool Schema 开展针对 OWASP MCP Top 10 安全风险的静态分析。
   - **防范未受信任的代码执行风险（MCP05）**：MCP 配置常常定义本地 STDIO 命令。扫描器在开发者电脑或 CI 中运行时，**绝不可默认直接拉起未受信任的本地子进程**，否则会产生严重的远程代码执行（RCE）隐患。
   - **杜绝外部依赖类型泄漏**：官方 MCP Java SDK 2.0 使用宽松的传输层数据结构（如 `inputSchema` 为 `Map<String, Object>`）。若直接将此类 SDK 内部类型泄漏至核心治理层模型，将破坏架构纯洁度，引发长期维护风险。
   - **业界标准报表规范**：扫描输出必须严格遵循 OASIS **SARIF 2.1.0** 规范，以便无缝集成 GitHub Code Scanning 与企业安全体系。

---

## 架构决策 (Decision)

我们确立以下架构政策、状态流转协议与领域隔离标准：

### 1. 多角色职责隔离与状态机

任务生命周期遵循严格的 5 阶段状态机，记录在 `.worklog/<task-id>.md` 中：

```
DESIGN ──► READY ──► IN_REVIEW ──► READY_TO_LAND ──► DONE
```

角色权限硬边界：
* **架构师**：只读权限（`Read`, `Grep`, `Glob`, `WebFetch`）。负责产出蓝图、ADR 与接口契约。严禁编写实现代码、严禁编译构建、严禁批准合并。文档一律使用**中文**撰写。
* **工程师**：在隔离分支/worktree 中操作。负责实现代码与测试，严禁直接推送 `main` 分支，严禁修改 `docs/adr/` 与 `docs/contracts/`，严禁自审。
* **审核员**：代码与 Git 差异只读审查。对照 ADR 与契约进行合规与安全审查，输出判定（`verdict: PASS | ISSUES_FOUND`）与具体 findings。严禁写代码，严禁合并。
* **人类负责人**：拥有唯一合并权、Phase 阶段推进权和 ADR 批准权。

### 2. 机器可校验的人类批准门禁

* 状态从 `DESIGN` 进入 `READY` **必须**获得人类明确批准。
* 口头同意不构成批准。必须在任务卡中写入独立且精确的一行：
  ```yaml
  Approved: Yes
  ```
* 缺少该标记时，工程师不得启动编码。

### 3. Wire 传输模型与控制面模型分离

为了保证 `mcp-governance-core` 模块的架构纯洁性，隔离外部 SDK 升级波动：
* **Wire 传输模型**：SDK 内部数据结构（如 `McpSchema.Tool`、原生 `Map<String, Object>`）严格限制在解析与适配层（`mcp-governance-scanner`）。严禁暴露给核心治理层公开 API。
* **控制面模型**：核心层使用强类型不可变实体（如 `GovernedTool`、`GovernedResource`、`ScanRule`）。
* **完整性与 Schema 保证**：
  - `inputSchema` 必须符合 JSON Schema 2020-12 标准，构造时做非空校验。
  - 每个受治理的 Tool 必须实时计算并持有 canonical 规范格式的 SHA-256 签名，用于防篡改检测。

### 4. 零信任静态扫描与安全执行策略

* **默认静态解析**：扫描器默认纯静态解析配置文件（`mcp.json`、Claude 配置等），不拉起本地进程。
* **STDIO 进程隔离**：默认禁止派生子进程执行 STDIO 配置中的命令。
* **显式执行授权**：只有显式传入 `--allow-execute` 参数时，才允许拉起进程执行动态列举，并必须打印高危审计告警。

### 5. 标准化 SARIF 2.1.0 报表契约

* 扫描报告必须输出符合 OASIS SARIF 2.1.0 规范的 JSON 格式。
* 每一项漏洞与缺陷均须映射到 OWASP MCP Top 10（MCP01 ~ MCP10）。
* 报告中**绝对严禁输出任何明文 Secret、密钥或 Token 实际值**。

---

## 结果与影响 (Consequences)

### 正向收益 (Positive)
* **供应链与合并安全保障**：杜绝 AI 自行合并恶意代码或绕过人类把关的风险。
* **高内聚与极低依赖**：核心模块采用纯 Java 17，无 Spring 依赖，CLI 启动极快。
* **杜绝 CI 注入隐患**：默认不执行 STDIO 命令，防止恶意 MCP 仓库在扫描时注入执行危险脚本。
* **开箱即用的 GitHub 联动**：原生支持 GitHub Code Scanning 代码行内高亮安全告警。

### 成本与权衡 (Trade-offs)
* **流程门禁成本**：每个任务从设计到编码必须等待人类签署 `Approved: Yes`，保证了质量与安全但增加了少许等待。
* **静态分析局限**：在不开启 `--allow-execute` 时，无法直接捕获运行时动态计算生成的 Tool，需依靠静态配置或导出的 Manifest。

---

## 合规与审查检查点 (Checkpoints)

### 审核员审查项
1. 检查工程师提交的 PR 是否修改了 `docs/adr/` 或 `docs/contracts/`（若是则直接阻断）。
2. 确认 `mcp-governance-core` 零 Spring 依赖。
3. 检查控制台与日志中绝不包含明文密钥。

### CI 门禁项
1. 状态卡包含 `Approved: Yes`。
2. `mvn -B verify` 测试通过率 100%。
3. `mvn javadoc:javadoc` 零警告。
4. 生成的 SARIF 报表通过 SARIF 2.1.0 JSON Schema 校验。
