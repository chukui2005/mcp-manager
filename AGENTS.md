# AGENTS.md — MCP Governance 项目协作宪章

> 本文件是所有 AI 会话在操作本仓库前**必须首先阅读并遵守**的最高层级规则。
> 它定义项目使命、权限边界、协作协议和不可协商的约束。
> 当本文件与任何角色文件或任务指令冲突时，**以本文件为准**。

## 1. 项目使命与定位

**一句话定位**：MCP 的治理与审计层，Java 原生，扫描优先。

**解决什么问题**：当组织内存在多个 MCP Server、上百个 Tool、多个 Agent 同时运行时，缺乏统一的可见性、审计和策略执行层。本项目提供从**静态扫描**到**运行时治理**再到**独立网关**的渐进式基础设施。

**不做什么**（防止范围蔓延）：

- 不做又一个 MCP Server 或 MCP Router（Wanaku 等已覆盖）。
- 不做通用聊天 UI 或 Agent 框架。
- 不执行 Tool 的实际业务逻辑；只治理调用行为。

**当前 Phase**：以 `.worklog/` 中的活跃任务为准。Phase 边界由人类维护，AI 不得自行推进。

## 2. 角色与权限边界（硬性，不可协商）

本项目使用三个固定 AI 角色 + 人类最终决策者。**权限边界由工具配置强制执行，不依赖提示词自觉。**

| 角色      | 工具权限                                                                                      | 核心产出                      | 禁止事项                                                                                                                      |
| ------- | ----------------------------------------------------------------------------------------- | ------------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| **架构师** | 只读（Read, Grep, Glob, WebFetch）                                                            | ADR、契约、学习笔记、蓝图            | **禁止 Edit/Write**；禁止写实现代码；禁止批准合并                                                                                          |
| **工程师** | 隔离 worktree 内 Read/Write/Bash                                                             | 代码、测试、PR                  | **禁止直接 push 到 main**；禁止修改 `docs/adr/` 和 `docs/contracts/`；禁止自审                                                            |
| **审核员** | 只读 + 只读 Bash（`git diff`, `git log`, `git show`, `git grep`, `git ls-files`, `gh pr view`） | Review findings、`verdict` | **禁止 Edit/Write**；禁止任何写文件 shell（`sed -i`, `tee`, `>`, `git apply`, `git checkout`, `patch` 等）；禁止 `git commit/push`；禁止批准合并 |
| **人类**  | 全部                                                                                        | 合并决定、Phase 边界、ADR 批准      | —                                                                                                                         |

**第一原则**：没有任何 AI 拥有直接推送到 `main` 的权限。所有变更通过 PR，由人类最终合并。

**角色文件**：各角色的专属规则位于 `.agents/architect.md`、`.agents/engineer.md`、`.agents/reviewer.md`。本文件是通用基线，角色文件只补充该角色的具体职责和额外约束，不重复本文件内容。

## 3. 协作协议：状态机与交接门禁

每个任务在工作日志 `.worklog/<task-id>.md` 中拥有一个**结构化的状态字段**。状态转换只能由拥有对应权限的角色触发，且必须满足前置条件。

```
DESIGN → READY → IN_REVIEW → READY_TO_LAND → DONE
```

| 状态转换                        | 触发角色 | 前置条件                                         |
| --------------------------- | ---- | -------------------------------------------- |
| `DESIGN → READY`            | 架构师  | ADR 已提交；契约已更新；人类写下 **`Approved: Yes`**（精确格式） |
| `READY → IN_REVIEW`         | 工程师  | PR 已提交；CI 全部通过                               |
| `IN_REVIEW → READY_TO_LAND` | 审核员  | `verdict: PASS`；无 CRITICAL/MAJOR findings    |
| `READY_TO_LAND → DONE`      | 人类   | 人类执行合并操作                                     |

**人类批准必须是机器可验证的**：在 `.worklog/<task>.md` 中写入独立一行 `Approved: Yes`。CI 通过检查该行是否存在来决定是否允许合并。聊天中的口头同意不构成批准。

**审核员的 `verdict` 是输入，不是决定**。审核员写 `PASS` 不代表合并；只有人类的合并操作才完成 `READY_TO_LAND → DONE`。

## 4. 项目结构与文档边界

```
/
├── AGENTS.md                      # 本文件
├── .agents/                       # 角色专属规则
├── .worklog/                      # 任务状态与交接记录
├── docs/
│   ├── architecture/              # 架构师蓝图
│   ├── adr/                       # 架构决策记录（Nygard 格式）
│   ├── contracts/                 # OpenAPI / JSON Schema 契约
│   └── learning-notes.md          # 学习 Sprint 产出
├── mcp-governance-core/           # 纯 Java，无 Spring 依赖
├── mcp-governance-scanner/        # 扫描引擎
├── mcp-governance-cli/            # picocli 入口
├── mcp-governance-action/         # GitHub Action wrapper
├── mcp-governance-rules-owasp/    # OWASP MCP Top 10 规则实现
└── mcp-governance-spring-boot-starter/   # Phase 2
```

**硬性约束**：

- `core` **不得依赖 Spring**。这是 CLI 轻量和可嵌入的前提。
- `scanner` 不得直接依赖 `cli`；CLI 只是 scanner 的薄壳。
- 契约文件（`docs/contracts/`）和 ADR（`docs/adr/`）**只能由架构师写入**。工程师和审核员只读。
- 新增跨切面依赖、分层变更、技术替换，**必须有对应的 ADR**。没有 ADR 的架构偏离，审核员必须标记为 CRITICAL finding。

## 5. 编码与安全约束（不可协商）

### 5.1 通用编码约束

- 所有公开 API（Starter、core 扩展点）必须有 Javadoc，`mvn javadoc:javadoc` **零警告**。
- 规则实现必须实现统一的 `ScanRule` 接口，包含 `id`（如 `MCP01-001`）、`owaspCategory`、`severity`、`remediation`。
- **不引入硬编码的外部 URL、token 或私有端点**。环境访问只允许在配置加载、bootstrap 或测试中出现。
- 若为 Spring Boot Starter：必须使用 `@ConditionalOnClass` / `@ConditionalOnMissingBean`，不得强制引入不必要依赖；所有配置项通过 `@ConfigurationProperties` 绑定，有 Javadoc 和默认值。

### 5.2 MCP 安全基线（MUST 条款）

以下条款直接来自 MCP 规范与 OWASP MCP Top 10，任何违反都是 **CRITICAL**：

- MCP Server / 网关**不得接受任何并非明确为其颁发的 token**。
- 实现授权的组件**必须验证所有入站请求**。
- **不得使用会话进行认证**。
- Streamable HTTP 部署必须**绑定 127.0.0.1**（本地模式）或强制 **OAuth 2.1**（共享模式），并验证 `Origin`。
- **不得转发下游凭据**；下游凭据必须独立获取。
- STDIO 命令执行必须被视为**不受信任代码执行**。扫描器默认不执行 STDIO 命令；如需执行以发现 Tool，必须在显式 `--allow-execute` 下运行并记录警告。

### 5.3 OWASP MCP Top 10 映射

扫描规则和运行时策略必须可映射到以下类别（Phase 1 至少覆盖 MCP01、MCP02、MCP03）：

MCP01 Token 管理不当与密钥暴露；MCP02 权限范围蔓延；MCP03 工具投毒；MCP04 供应链攻击；MCP05 命令注入与执行；MCP06 上下文载荷中的提示注入；MCP07 认证与授权不足；MCP08 审计与遥测缺失；MCP09 影子 MCP Server；MCP10 上下文过度共享。

### 5.4 环境变量与 Secret 控制

- 所有 secret 必须通过**引用**注入，不得出现在命令行参数、配置文件明文或代码常量中。
- `.env` 文件、`mcp.json` 中的明文凭据**不得提交**。
- 扫描器输出中**永远不打印 secret 的实际值**，只报告位置、类型和置信度。
- 网关/Starter 自身的敏感配置**不得有默认值**；缺失时启动失败并给出明确错误。

## 6. 外部依赖集成协议（防止类型泄漏）

**核心原则**：控制面模型与 wire 模型分离。不将 SDK 内部类型泄漏到治理层边界。

### 6.1 MCP Java SDK 2.0 的特定约束

- `Tool.inputSchema` 是 `Map<String, Object>`，**不是** `JsonSchema`。不得直接复用 SDK 的 `McpSchema.Tool` record 作为治理层模型。
- 定义 `GovernedTool` 包装类，持有原始 schema、SHA-256、风险标签和可见性规则。SDK 的 `Tool` 仅用于 wire 解析和序列化。
- `inputSchema` **不得为 null**；JSON Schema 2020-12 为默认 dialect。
- SDK 2.0 在构造时强制非 null（`IllegalArgumentException`），但 wire 反序列化是宽松的（缺失必填字段替换为安全默认值并 log WARN）。治理层必须基于**构造后的对象**做校验，不依赖 wire 的宽松默认。

### 6.2 学习 Sprint 协议

每个角色在开始涉及新依赖或新协议的工作前，必须执行：

1. **只读学习**：架构师产出 `docs/learning-notes.md`，记录：读了哪些精确文件/URL；哪些行为影响设计；哪些假设需要人类确认。
2. **集成契约**：明确本项目**定义什么**、外部库的哪些行为被引用、哪些被隔离。若外部类型出现在本项目边界内，审核员标记为 finding。
3. **工程师实现前必须读学习笔记**；审核员审查时以学习笔记作为一致性基准。

**禁止**：“浏览仓库后参考实现”。必须给出精确路径和明确的集成边界。

## 7. CI/CD 门禁

每个 PR 必须通过以下检查，缺一不可：

1. `mvn -B verify`（编译 + 单元测试 + 集成测试）。
2. **SARIF 自举**：用本项目自己的扫描器扫描本项目或示例项目，上传 SARIF 到 GitHub Code Scanning。若产生 **critical** 发现，阻断合并。SARIF 必须为 **2.1.0** 版本，使用 `github/codeql-action/upload-sarif@v3` 上传。
3. 依赖漏洞扫描（Trivy 或 OWASP Dependency-Check），critical/high 阻断。
4. 契约测试：架构师定义的 OpenAPI/JSON Schema 契约必须通过。
5. JavaDoc 和 sources JAR 齐全（Maven Central 发布前额外校验 GPG 签名和 POM 元数据）。

**不得为了通过而削弱这些检查**。如果检查本身有误，修正检查并记录原因。

## 8. README 与文档规范

README 首屏必须在 10 秒内回答：**这是什么、为什么需要、怎么 30 秒跑起来**。

**必须包含的结构（按顺序）**：

1. 一句话定位。
2. 一个 GIF 或截图（Phase 1：扫描官方 filesystem server 的终端输出）。
3. 与 Wanaku / AI MCP Bridge / mocapi 的差异化表格（维度：扫描、运行时审计、Spring Boot Starter、策略回放、OWASP 映射）。
4. Quick Start：`docker run` 一行 + GitHub Action 五行 YAML。
5. 架构图。
6. 配置参考：环境变量和 YAML 策略的完整列表（类型、默认值、必填、安全影响）。
7. Roadmap（明确当前 Phase 边界）。
8. `CONTRIBUTING.md` / `SECURITY.md` 链接。
9. 中英双语：`README.md`（英文）+ `README.zh-CN.md`，首屏互相链接。

**禁止**：在未达到对应 Phase 完成定义前使用“生产就绪”“企业级”；将未实现的 Roadmap 项写成“支持”。

## 9. 版本与发布

- 版本号遵循 **SemVer**。
- Phase 1 发布标签：`v0.1.0-scan`；Phase 2：`v0.2.0-starter`；Phase 3：`v0.3.0-gateway`。
- Tag 触发 Maven Central 发布。
- `v1.0.0` 之前 Starter API 可以 breaking，但**必须在 CHANGELOG 中明确列出**。

## 10. 变更控制

以下变更**必须**有架构师产出的 ADR，且经人类 `Approved: Yes` 后才能进入 `READY`：

- 新增或替换跨切面依赖。
- 模块边界调整（如 `core` 引入 Spring 依赖）。
- 技术栈替换。
- 契约的 breaking change。
- 安全策略的默认行为变更（如默认 allow → deny）。

**豁免（waiver）**：对不变量的临时豁免必须是**显式的、有 owner、经过审核、有时间限制**的。未记录的豁免视为 CRITICAL finding。

**本文件的修改**：只能由人类发起 PR 并合并。AI 角色可以提出建议（作为 finding 或 Issue），但不得直接编辑 `AGENTS.md`。

> 最后更新：与当前活跃 Phase 和 `.worklog/` 状态保持同步。若本文件与仓库实际状态不一致，以实际状态为准，并应尽快由人类修正本文件。
