# 0002. MCP 配置文件静态解析器与 OWASP MCP02/MCP03 规则架构设计

* **状态**: 提议中 (Proposed)
* **日期**: 2026-10-29
* **决策者**: 架构师 AI、项目负责人
* **参考文档**: AGENTS.md, docs/adr/0001-project-scope-and-modules.md, docs/contracts/scan-result.schema.json

---

## 背景 (Context)

在 TASK-001 中，项目确立了 4 模块结构（`core`, `scanner`, `rules-owasp`, `cli`）并实现了初步的 `MCP01-001`（明文秘钥暴露）检测规则。然而现有实现存在以下显著局限：
1. **缺乏真实配置文件模型与解析器**：现阶段 `ScanContext` 仅接收原始字符串，缺乏对标准 `mcp.json` 与 Claude Desktop 格式 (`claude_desktop_config.json`) 的解析能力，无法对各个 `mcpServers` 的 `command`、`args`、`env` 和内嵌/声明的 Tool 进行语义分析。
2. **缺乏权限范围蔓延与过度授权检查 (MCP02)**：未对工具的 `inputSchema` 约束范围（如通配符 `*`、无界字段）、高危系统级破坏操作进行静态识别。
3. **缺乏工具投毒与提示词注入防御 (MCP03)**：恶意 MCP Server 可能在工具 `description` 中埋入隐藏提示注入指令（如 `Ignore previous instructions`、`<system>`），或通过跨 Server 重名工具劫持大模型调用（Tool Shadowing）。
4. **控制台输出单一**：默认输出仅是简单的单行文本打印，缺乏便于研发与审计人员直观研判的 ANSI 彩色 Markdown 格式风险摘要。

因此，亟需确立静态配置解析器设计标准、扩展统一治理模型、定义 MCP02/MCP03 规则判定算法，并设计控制台展示规范。

---

## 架构决策 (Decision)

### 1. 领域模型演进 (`mcp-governance-core`)

为了使所有扫描规则具备类型安全的数据访问能力，同时遵循“控制面模型与 Wire 传输模型强隔离”以及“零 Spring 依赖”不变量：

1. **新增不可变配置领域实体**：
   - `McpServerConfig` (Record):
     - `id`: 服务唯一标识（如 `"filesystem"`, `"github"`）；
     - `command`: 启动命令（如 `"npx"`, `"docker"`，静态分析，禁止运行）；
     - `args`: 命令参数列表（不可变 List）；
     - `env`: 环境变量键值映射（不可变 Map，支持脱敏与引用识别）；
     - `declaredTools`: 该服务内嵌或静态声明的 `GovernedTool` 列表；
     - `rawProperties`: 其它附加扩展属性。
   - `McpConfig` (Record):
     - `servers`: 服务映射表 `Map<String, McpServerConfig>`；
     - 辅助方法 `getAllTools()`: 汇聚返回所有 Server 声明的 `GovernedTool`；
     - 辅助方法 `findServerByToolName(String toolName)`: 定位工具所属的服务。

2. **升级 `ScanContext`**：
   - 增加对强类型 `McpConfig mcpConfig` 的第一类公民支持；
   - 保留原有物理路径与原始文本字段，确保向下兼容。

### 2. 静态配置解析引擎 (`mcp-governance-scanner`)

在 `mcp-governance-scanner` 模块中实现 `McpConfigParser`：
- **格式归一化**：标准 `mcp.json` 与 `claude_desktop_config.json` 核心均挂载于根节点 `mcpServers`（或兼容平铺与嵌套两种常见变体）。解析器通过 Jackson `ObjectMapper` 静态解析 JSON AST，构建 `McpConfig`；
- **内嵌工具提取**：支持解析在配置中直接声明的 `tools` 节点（包含 `name`, `description`, `inputSchema`），并安全转换为不可变的 `GovernedTool` 实体；
- **零进程拉起（严格静态审计）**：默认绝不调用 `ProcessBuilder` 或执行 STDIO 命令，防止本地代码执行（MCP05）。

### 3. OWASP MCP02 与 MCP03 规则集规范 (`mcp-governance-rules-owasp`)

通过 SPI 扩展新增 3 条核心安全规则：

#### 规则 1：`MCP02-001` - 过度宽泛权限与参数范围检测
- **所属分类**: `OwaspCategory.MCP02` (权限范围蔓延与过度授权)
- **默认严重度**: `Severity.HIGH`
- **判定逻辑**:
  1. **通配符泛化检查**: 检查工具 `inputSchema` 中的默认值、示例或模式约束，若包含全通配符 `"*"`、`"**"`，或文件路径参数未定义限定目录；
  2. **高危破坏性系统操作模式**: 工具名称或参数名命中高危模式（如包含 `execute_command`, `shell_exec`, `rmdir`, `format_disk`, `drop_database`）但未标注审批要求或只读白名单约束；
  3. **空弱 Schema 约束**: 工具参数类型为 Object/Array 却完全缺失 `properties`、`items` 或通篇允许 `additionalProperties: true` 且无限制。

#### 规则 2：`MCP03-001` - 工具描述隐藏提示注入检测 (Prompt Injection)
- **所属分类**: `OwaspCategory.MCP03` (工具投毒与提示注入)
- **默认严重度**: `Severity.CRITICAL`
- **判定逻辑**:
  - 正则扫描工具的 `description` 以及各参数的 `description`；
  - 命中注入诱导模式（大小写不敏感）：
    - 指令忽略覆写：`ignore (all )?previous instructions?`, `disregard previous`, `forget all previous`；
    - 系统标记冒充：`<system>`, `</system>`, `[system]`, `System prompt:`, `<instruction>`；
    - 隐蔽外发诱导：`send (credentials|tokens?|passwords?|keys?) to`, `exfiltrate`；
    - 角色重定义劫持：`you are now a`, `new instructions:`, `operating in developer mode`。

#### 规则 3：`MCP03-002` - 跨服务工具影子混淆检测 (Tool Shadowing)
- **所属分类**: `OwaspCategory.MCP03` (工具投毒与提示注入)
- **默认严重度**: `Severity.HIGH`
- **判定逻辑**:
  - 汇总当前 `McpConfig` 中所有 Server 所提供的 Tool 名称；
  - 检测是否存在重复名称（例如 `serverA` 与 `serverB` 同时暴露名为 `"read_file"` 的工具）；
  - 若存在同名，报告 `Tool Shadowing` 告警，标明冲突的 Server ID 和潜在调用劫持风险。

### 4. 控制台 ANSI 彩色 Markdown 报告渲染 (`mcp-governance-cli`)

在 `cli` 模块新增 `ConsoleReportRenderer`：
- 支持 ANSI 颜色（CRITICAL 标红加粗、HIGH 标黄、MEDIUM 标蓝、LOW 标灰）；
- 格式化输出结构：
  1. 扫描概览卡片（扫描目标、耗时、发现项统计）；
  2. 风险分布统计表格（按 OWASP 分类及严重级别分组）；
  3. 详细发现项清单（规则 ID、严重级、定位位置、问题说明、修复指导）。

---

## 备选方案分析 (Alternatives)

1. **直接调用 MCP Server 进程探测动态暴露的 Tools (STDIO Handshake)**:
   - *否决理由*：违反 Phase 1 的静态扫描与安全基线（5.2 条款），第三方未经审计的配置文件可能通过 `command` 和 `args` 植入本地攻击载荷（MCP05）。因此坚持默认纯静态 AST 解析；动态探测必须且仅在显式传入 `--allow-execute` 时受限执行。
2. **在规则层内直接解析原始 JSON Map 而不抽象 `McpConfig`**:
   - *否决理由*：会导致每个规则重复编写脆弱的 JSON 路径解析代码，破坏控制面强类型约束与代码可维护性。

---

## 影响与后果 (Consequences)

### 正向影响
- 确立了真实 MCP 配置文件的标准抽象模型，支持多 Server、多 Tool 场景的一体化静态审计。
- 完善了 OWASP MCP Top 10 中前三项最核心风险（MCP01 凭证泄露、MCP02 权限蔓延、MCP03 工具投毒与劫持）的静态防御闭环。
- 极大改善命令行交互与 CI 控制台可读性。

### 潜在代价与妥协
- 纯静态分析无法捕获部分完全依赖运行时动态生成的 Tool Schema（后续 Phase 结合沙箱动态探测演进）。
