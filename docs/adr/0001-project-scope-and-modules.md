# 0001. Maven 多模块架构与领域分层边界基线

* **状态**: 提议中 (Proposed)
* **日期**: 2026-10-29
* **决策者**: 架构师 AI、人类负责人
* **参考文档**: AGENTS.md, .agents/architect.md, docs/adr/0000-dry-run-workflow.md

---

## 背景 (Context)

`mcp-manager` 项目的 Phase 1 阶段核心目标是交付一个轻量、可嵌入、Java 原生的静态扫描命令行工具（`mcp-governance-cli`）与 GitHub Action，用于静态检查 MCP Server 配置文件（`mcp.json`、Claude Desktop 配置、Tool 定义），防御 OWASP MCP Top 10 安全风险。

为了支撑后续阶段（Phase 2: Spring Boot Starter 运行时治理，Phase 3: 独立网关与策略回放）的无缝演进，必须在 Phase 1 确立清晰稳固的 Maven 多模块架构与依赖隔离边界：

1. **核心领域严禁引入 Spring（零框架侵入）**：
   若核心模型或扫描规则依赖 Spring，会导致命令行工具打包膨胀至数十兆、冷启动缓慢，且无法轻量嵌入至非 Spring 项目中。
2. **Wire 传输模型与治理控制面模型强隔离**：
   MCP Java SDK 的传输实体（如 `McpSchema.Tool` 内部使用宽泛的 `Map<String, Object>`）反序列化宽松且频繁变动。若直接渗透到核心治理实体中，将无法实现严格的构造期校验、模式校验与防篡改摘要。
3. **安全默认执行策略（防范 MCP05）**：
   扫描器在 CI 或开发环境运行第三方配置时，默认绝不拉起未受信任的 STDIO 本地进程。
4. **统一标准报表格式**：
   扫描结果必须严格遵循 OASIS SARIF 2.1.0 规范，以无缝对接 GitHub Code Scanning 及各类安全大屏。

---

## 架构决策 (Decision)

我们确立基于 `io.github.chukui2005:mcp-manager` 的 4 模块聚合工程体系：

### 1. 模块层次结构与有向无环图 (DAG)

```
mcp-manager (根聚合工程 / Parent POM)
 │
 ├── mcp-governance-core
 │    └── (纯 Java 17 领域模型，零框架依赖，SPI 规则接口)
 │
 ├── mcp-governance-scanner
 │    ├── 依赖: mcp-governance-core
 │    └── (静态解析引擎、配置评估器、SARIF 2.1.0 序列化器)
 │
 ├── mcp-governance-rules-owasp
 │    ├── 依赖: mcp-governance-core
 │    └── (ScanRule SPI 规则具体实现: MCP01, MCP02, MCP03)
 │
 └── mcp-governance-cli
      ├── 依赖: mcp-governance-scanner, mcp-governance-rules-owasp
      └── (基于 Picocli 的命令行入口，装配扫描器，管理输出与退出码)
```

**严格依赖约束（架构不变量）**：
- `mcp-governance-core` **严禁依赖 Spring 或 Picocli**，只允许引用最少量的纯工具注解（Jackson 基础注解）与 SLF4J 接口。
- `mcp-governance-scanner` 严禁反向依赖 `cli`。
- `mcp-governance-rules-owasp` 仅依赖 `core` 模块，与外部引擎及命令行彻底解耦。
- `mcp-governance-cli` 作为外层运行宿主，组装引擎与规则集。

### 2. 技术栈与核心版本基线

- **Java 版本**: Java 17 LTS（`maven.compiler.release = 17`）。
- **构建工具**: Apache Maven 3.9+。
- **文档与编码**: 统一 UTF-8 编码，所有技术文档和注释强制采用**规范中文**。
- **JSON 与模型映射**: Jackson 2.17.2（`jackson-databind`, `jackson-core`, `jackson-annotations`）。
- **命令行框架**: Picocli 4.7.6（`info.picocli:picocli`，搭配注解处理器生成预热元数据）。
- **日志抽象**: SLF4J 2.0.13 API，实现层（Logback 1.5.6）严格局限于 CLI 模块和单元测试环境。
- **测试框架**: JUnit Jupiter 5.10.3 + AssertJ Core 3.26.3。
- **Javadoc 门禁**: `maven-javadoc-plugin` 配置 `<failOnWarnings>true</failOnWarnings>`，零告警通过。

### 3. 模型隔离设计 (`GovernedTool` 控制面封装)

- 核心模型置于 `io.github.chukui2005.mcpgov.core.model` 包下。
- `GovernedTool` 采用不可变 Record 设计，包含：
  - 标准化名称与描述；
  - 强制非空的 JSON Schema 2020-12 规范对象；
  - 实时计算并持有的 SHA-256 完整性指纹（用于防范 MCP03 工具篡改与 Rug-pull）；
  - 风险标签集合（`Set<RiskTag>`）与严重等级（`RiskLevel`）。
- 外部输入（`mcp.json` 或网络传输的 JSON）由 `scanner` 模块负责解析并校验映射为 `GovernedTool`，绝不直接使用原始 Loose Map。

### 4. 零信任默认行为

- 扫描器默认仅执行静态文本/AST 与 Schema 规则匹配。
- 扫描 STDIO 类型的 MCP Server 时，默认禁止拉起本地进程。只有显式指定 `--allow-execute` 时才放行，并强制记录警告日志。

---

## 结果与收益 (Consequences)

### 正向收益 (Positive)
- **极致轻量**：核心库无任何沉重框架，体积小、秒级甚至毫秒级启动。
- **平滑演进**：后续 Phase 2 的 `mcp-governance-spring-boot-starter` 可以直接以只读依赖方式引入 `core` 与 `scanner`，无依赖冲突风险。
- **原生安全**：彻底杜绝静态分析过程中因恶意 MCP Server 命令而产生的本地代码执行风险。
- **自动化集成**：标准 SARIF 2.1.0 报表可直接上传至 GitHub Code Scanning，内联呈现行内修复建议。

### 成本与权衡 (Trade-offs)
- 增加了静态解析层将外部配置映射到内部强类型 `GovernedTool` 的转换代码成本。

---

## 合规与审查检查点 (Checkpoints)

1. **依赖纯洁性检查**：`mvn dependency:tree` 校验 `core` 模块零 Spring 依赖。
2. **文档规范检查**：所有设计文档、ADR 必须全部为**中文**。
3. **Javadoc 检查**：`mvn javadoc:javadoc` 必须零告警通过。
4. **输出契约检查**：CLI 输出的报表必须符合 `docs/contracts/scan-result.schema.json` 定义。
