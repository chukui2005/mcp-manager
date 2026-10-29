# MCP 协议与 OWASP MCP Top 10 学习调研笔记 (Learning Notes)

> **任务标识**: TASK-001 / task-1 (Learning Sprint)  
> **编写角色**: 架构师 (Architect)  
> **日期**: 2026-10-29  
> **基准规范**: Model Context Protocol Specification (2024-11-05), OWASP MCP Top 10, MCP Java SDK 2.0  
> **语言规范**: 中文 (遵循 `AGENTS.md` 与 `.agents/architect.md`)

---

## 1. 调研背景与阅读路径

为了确保 `mcp-manager` 项目从 Phase 1（静态扫描与 CI 门禁）到 Phase 2（Spring Boot Starter 运行时治理）及 Phase 3（独立治理网关）的技术决策具备权威依据与前瞻性，架构师执行了对 Model Context Protocol (MCP) 官方规范及 OWASP MCP Top 10 安全威胁模型的深入只读调研。

### 1.1 精确查阅的文件与 URL 路径
1. **MCP 官方架构规范**:
   - `https://modelcontextprotocol.io/specification/2024-11-05/architecture/`
   - 核心重点：Client-Host-Server 三层拓扑架构、JSON-RPC 2.0 通信语义、能力协商 (Capability Negotiation) 机制。
2. **MCP 工具核心规范 (Tools)**:
   - `https://modelcontextprotocol.io/specification/2024-11-05/server/tools/`
   - 核心重点：`tools/list`、`tools/call`、`notifications/tools/list_changed`、InputSchema 结构（JSON Schema 2020-12）、人在回路 (Human-in-the-Loop) 原则。
3. **MCP 资源与提示词规范 (Resources & Prompts)**:
   - `https://modelcontextprotocol.io/specification/2024-11-05/server/resources/`
   - `https://modelcontextprotocol.io/specification/2024-11-05/server/prompts/`
   - 核心重点：URI 寻址、URI 模版 (RFC 6570)、只读上下文挂载、提示词模版渲染。
4. **MCP 传输层协议规范 (Transports)**:
   - `https://modelcontextprotocol.io/specification/2024-11-05/basic/transports/`
   - 核心重点：STDIO 标准输入输出传输协议、Streamable HTTP (SSE + POST) 传输协议、Origin 校验与本地绑定约束。
5. **OWASP Top 10 for Model Context Protocol (MCP)**:
   - 社区安全标准提案与 Top 10 漏洞分类定义（MCP01 ~ MCP10）。
6. **MCP Java SDK 2.0 源码与行为契约**:
   - `io.modelcontextprotocol.sdk:mcp-core` 类库模型与反序列化边界。

---

## 2. Model Context Protocol (MCP) 核心规范剖析

### 2.1 拓扑架构与角色划分
MCP 采用非对称的三层架构：
- **Host (宿主应用)**：如 Claude Desktop、IDE、企业 Agent 编排框架。负责管理多个 Client 实例、维持 LLM 会话上下文、聚合上下文、执行安全策略与人在回路 (Human-in-the-Loop) 审批。
- **Client (客户端)**：由 Host 实例化，与具体 Server 维持 1:1 的独立有状态会话连接。负责协议能力协商、请求双向路由、生命周期维持。
- **Server (服务端)**：暴露 Tools、Resources、Prompts 基础原语的进程或独立服务，**不得**窥探整个对话历史，**不得**跨 Server 感知。

### 2.2 四大核心原语 (Core Primitives)

| 原语 | 控制模式 (Control Model) | 语义与定位 | 协议交互行为 | 治理关键考量 |
| :--- | :--- | :--- | :--- | :--- |
| **Tools** | **Model-Controlled** (模型主动调用) | 带有副作用的操作或动态数据查询 (如写数据库、调 API、系统执行) | `tools/list`<br>`tools/call`<br>`notifications/tools/list_changed` | **风险最高**。需要静态防投毒、Schema 参数白名单校验、SHA-256 防篡改指纹。 |
| **Resources** | **Application-Controlled** (宿主/应用控制) | 只读上下文附件 (文件内容、静态元数据、监控大盘快照) | `resources/list`<br>`resources/read`<br>`resources/templates/list` | 防止敏感文件目录越界泄漏 (Path Traversal)、防止恶意超大文件上下文耗尽 (DoS)。 |
| **Prompts** | **User-Controlled** (用户显式触发) | 预置提示词模板与斜杠命令 (如 `/review`, `/debug`) | `prompts/list`<br>`prompts/get` | 防止模版中隐藏间接提示词注入 (Indirect Prompt Injection)。 |
| **Transports** | **Infrastructure-Controlled** (基础设施与连接) | 物理/逻辑通信通道 (STDIO, Streamable HTTP) | JSON-RPC 2.0 序列化、进程标准流重定向、SSE 流 | **执行面安全核心**。STDIO 防未授权本地执行，HTTP 强制防 DNS Rebinding / 强制鉴权。 |

### 2.3 传输通道的实现细节与安全边界

#### 1. STDIO 传输
- **机制**：Host 启动本地可执行子进程，通过进程的 `stdin` 写入 JSON-RPC 请求，从 `stdout` 逐行读取响应（换行符 `\n` 分隔）。
- **致命安全风险**：如果客户端解析不可信配置文件（如恶意的 `mcp.json`）并自动启动命令行，等同于**远程任意代码执行 (RCE)**。
- **规范要求与治理约束**：扫描器默认**绝不执行**本地命令行，仅对配置内容、路径有效性与环境变量进行静态语义检查。

#### 2. Streamable HTTP 传输
- **机制**：基于 HTTP 协议，客户端使用 HTTP POST 发送请求，服务器使用 Server-Sent Events (SSE) 建立长连接返回事件流。
- **安全约束**：
  - 本地模式必须绑定 `127.0.0.1`，严禁默认监听 `0.0.0.0`。
  - 必须对入站请求严格校验 `Origin` 标头，防止浏览器端恶意网站发起跨站请求伪造 (CSRF/DNS Rebinding)。
  - 远程共享模式必须强制采用 OAuth 2.1 鉴权，严禁使用 Cookie/Session 认证，且必须验证每个请求的 Audience/Token 范围。

---

## 3. OWASP MCP Top 10 安全威胁深度映射

针对 OWASP 社区定义的 MCP Top 10 安全威胁，本项目（`mcp-manager`）在 Phase 1（静态扫描）与后续阶段的治理映射如下表所示：

| 编号 | 威胁名称 | 威胁场景与利用手法 | Phase 1 静态扫描规则 (ScanRule) | Phase 2/3 运行时/网关治理策略 |
| :--- | :--- | :--- | :--- | :--- |
| **MCP01** | **Token 管理不当与密钥暴露** (Improper Token Management) | `mcp.json`、启动脚本或环境变量中硬编码明文 API Key、私钥或云凭证；扫描日志中回显秘钥。 | 规则 `MCP01-001`：明文 Secret 检测（高危变量名正则、私钥模式、高熵字符串）。报告脱敏，仅输出行号与置信度。 | 强制引用注入（`${env:KEY}`），网关阻断包含明文硬编码 Token 的连接请求。 |
| **MCP02** | **权限范围蔓延与过度授权** (Scope Creep & Over-privileged Access) | 单个 MCP Server 挂载过度宽泛的工具集（如读写全盘、通配符执行），InputSchema 缺乏边界约束。 | 规则 `MCP02-001`：InputSchema 缺失约束检测（无 enum、无 pattern、未限定范围的高危字符串入参）。 | 基于 RBAC/ABAC 的 Tool 调用切面拦截；参数校验拦截器阻断越界传参。 |
| **MCP03** | **工具投毒与动态篡改** (Tool Poisoning & Rug-pulling) | Server 在运行中通过 `list_changed` 动态替换 Tool 的 `description`，注入恶意诱导词（Prompt Injection）欺骗 LLM 调用恶意函数。 | 规则 `MCP03-001`：Tool 描述提示词注入检测（检测 `Ignore previous instructions`、系统覆盖等暗语模式）。 | `GovernedTool` 记录 SHA-256 指纹；运行时校验工具描述与 Schema 指纹变动（Rug-pull 阻断）。 |
| **MCP04** | **供应链攻击与不可信来源** (Supply Chain Vulnerabilities) | 引用未经审计的公共 npm (`npx`)、Python (`uvx`) 包或容器镜像，内部植入恶意木马。 | 检查命令是否使用非官方 Registry 或临时无版本锁定的执行命令。 | 建立已验证 MCP Server 白名单机制，校验包签名与版本固定。 |
| **MCP05** | **命令注入与未授权代码执行** (Command Injection & Untrusted Execution) | STDIO 配置中直接使用 `bash -c`、`cmd.exe /c` 拼接动态参数；扫描器意外触发执行。 | 静态检测 STDIO 命令行中的 shell 包装器及管道字符（`|`, `;`, `&&`）。静态扫描器默认禁止执行进程。 | 沙箱隔离（Container/Wasm/Restricted Shell）；强制使用静态参数列表，拒绝拼接。 |
| **MCP06** | **上下文载荷中的间接提示注入** (Indirect Prompt Injection via Payloads) | 资源读取或 Tool 返回结果中包含诱导攻击文本，破坏模型后续行为。 | 扫描静态资源配置文件中预置的注入特征。 | 网关层对 Tool 输出与 Resource 返回结果执行敏感词与越权提示词二次清洗。 |
| **MCP07** | **认证与授权不足** (Insufficient Auth & Authz) | HTTP 端点未启用认证，或错误接受非当前 Server 颁发的 Token，未校验 `Origin`。 | 静态检查 HTTP 配置是否缺失 TLS、缺失授权头或配置了弱安全绑定。 | 强制 OAuth 2.1 令牌验证，严禁跨 Server 转发下游凭证。 |
| **MCP08** | **审计与遥测缺失** (Lack of Audit Trail & Telemetry) | 工具调用黑盒化，无法追踪哪个 Agent、在何时、使用何参数执行了破坏性操作。 | 检查配置文件是否配置审计输出通道。 | 强制结构化审计日志（JSON-RPC 追踪 ID、调用上下文、前后指纹、响应延迟与状态）。 |
| **MCP09** | **影子 MCP Server** (Shadow MCP Servers) | 开发者私自启动未经备案的本地 MCP Server 并连接生产数据库或核心内网资产。 | CI 扫描仓库与开发者配置，检测未在组织资产库注册的端点和配置。 | 网络层准入审计，自动发现未声明的 MCP 协议端口。 |
| **MCP10** | **上下文过度共享** (Excessive Context Sharing) | 多个 Agent 之间无隔离共享记忆或上下文，导致私有会话数据向第三方 Server 泄漏。 | 检查配置中是否存在跨 Agent 共享未脱敏资源的模式。 | 严格遵循 MCP 规范第 3 条原则：禁止 Server 窥探完整对话历史和其它 Server 状态。 |

---

## 4. 架构设计关键影响 (Architectural Impacts)

基于上述调研，确定对 `mcp-manager` 的以下关键架构设计决策：

### 4.1 控制面模型必须与 Wire 传输模型强隔离 (Decoupling Core from Wire)
- **发现**：MCP Java SDK 2.0 中，`Tool.inputSchema` 是原生的 `Map<String, Object>`，其底层反序列化是极为宽松的（容忍字段缺失并赋予默认值）。
- **设计决策**：
  - `mcp-governance-core` 严禁直接复用 SDK 的 `McpSchema.Tool` 作为领域实体。
  - 定义独立的不可变值对象 `GovernedTool`：
    1. 强制持有强类型的标准化元数据（`name`, `description`）；
    2. 强制持有规范的 JSON Schema 2020-12 校验结构（不得为 null）；
    3. 实时计算并固化工具定义的 **SHA-256 完整性指纹**（用于防范 MCP03 工具投毒与篡改）；
    4. 附加治理属性：`RiskLevel`（CRITICAL, HIGH, MEDIUM, LOW）、`Set<RiskTag>`、`AuditPolicy`。
  - 只有在 `scanner` 或网络适配层需要序列化/反序列化时，才在边界处将 Wire JSON 映射至 `GovernedTool`。

### 4.2 零信任静态扫描策略 (Zero-Trust Static Scanning Policy)
- **发现**：MCP 配置（如 `claude_desktop_config.json` 或 `mcp.json`）通常声明大量的 STDIO 启动命令（如 `npx -y @modelcontextprotocol/server-filesystem /path`）。
- **设计决策**：
  - 扫描器在执行静态分析时，**默认严禁调用系统 `ProcessBuilder` 或 `Runtime.getRuntime().exec`**。
  - 所有检测均基于 AST、文本语法分析、正则表达式与 JSON Schema 静态推导。
  - 若未来提供动态探测功能，必须置于独立的 CLI 标志 `--allow-execute` 下，且必须在终端与报表中显式打印 CRITICAL 级别的高风险安全告警。

### 4.3 扫描规则引擎的可扩展 SPI 架构 (ScanRule SPI)
- **设计决策**：
  - 在 `mcp-governance-core` 中定义抽象的规则契约接口 `ScanRule`：
    ```java
    public interface ScanRule {
        String getId();               // 如 MCP01-001
        String getName();             // 规则中文名称
        OwaspCategory getCategory();  // 对应的 OWASP MCP 分类
        Severity getSeverity();       // 严重级别: CRITICAL, HIGH, MEDIUM, LOW, INFO
        String getRemediation();      // 中文修复建议
        List<ScanFinding> scan(ScanContext context);
    }
    ```
  - 规则实现完全模块化，首批规则收敛在 `mcp-governance-rules-owasp` 模块，通过 Java `ServiceLoader` 或明确的 Registry 注册，保持内核的极简与零框架侵入。

### 4.4 行业标准 SARIF 2.1.0 报表产出
- **设计决策**：
  - 扫描产出必须严格对接 OASIS SARIF 2.1.0 (Static Analysis Results Interchange Format) 标准。
  - 契约由 `docs/contracts/scan-result.schema.json` 固化。
  - 每一项扫描发现（Finding）必须关联准确的文件路径、起始行号、列号、规则 ID、OWASP 标签以及清晰的中文修复指导（Remediation），以便原生集成到 GitHub Code Scanning、GitLab SAST 与 SonarQube 中。

---

## 5. 待人类确认的边界与设计假设 (Assumptions & Open Questions)

1. **假设 1：Phase 1 聚焦静态配置文件与声明扫描**
   - *假设描述*：Phase 1 扫描器输入目标主要是各类客户端配置文件（`mcp.json`, `claude_desktop_config.json`）、Tool JSON Schema 描述定义文件以及工程代码中的硬编码配置。
   - *需确认事项*：是否需要在 Phase 1 支持对在线运行的 Streamable HTTP MCP Server 发起只读探测（仅发送 `tools/list` 而不执行 `tools/call`）？（当前默认：**Phase 1 仅做离线静态文件扫描**）。

2. **假设 2：Java 17 最小化依赖基线**
   - *假设描述*：`core` 模块仅引入纯标准库与最基础的 Jackson 注解、SLF4J API。绝不引入任何 Spring、Guava 或沉重依赖。
   - *需确认事项*：是否允许在 `rules-owasp` 中引入高精度的正则/密码学工具类？（当前默认：采用 Java 17 标准库 `MessageDigest` 与 `java.util.regex` 实现，避免第三方依赖膨胀）。

---

## 6. 学习 Sprint 结论与移交准备

本次调研为 TASK-001 后续代码骨架的搭建奠定了坚实的理论与规范基础：
1. **理论与规范支撑就绪**：厘清了 MCP 协议核心实体与 OWASP MCP Top 10 的映射关系。
2. **架构边界清晰**：确立了 `GovernedTool` 隔离模型、`ScanRule` SPI 规范以及 `core` 模块零框架依赖原则。
3. **交付物就绪**：本学习笔记已完整归档至 `docs/learning-notes.md`，可作为工程师编码与审核员审查的一致性基准。
