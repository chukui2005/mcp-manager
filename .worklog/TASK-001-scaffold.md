# Task: TASK-001 - Phase 1 Maven 多模块架构骨架与契约基线

## 状态元数据
- **Status**: IN_REVIEW
- **Owner**: Reviewer
- **Approved**: Yes
- **Created**: 2026-10-29
- **Updated**: 2026-10-29

---

## 1. 任务背景与目标
正式启动 Phase 1 阶段（静态扫描 CLI + GitHub Action）：
搭建 `mcp-manager` 的多模块聚合工程骨架，明确分层边界与依赖管理，确保：
1. 建立根 POM，管理全局插件、版本、编译目标（Java 17）；
2. 划分 4 个纯 Java 核心模块：
   - `mcp-governance-core`：纯领域模型（GovernedTool 等）、SPI 接口（ScanRule 等），严禁 Spring 依赖。
   - `mcp-governance-scanner`：核心静态扫描引擎、规则加载器、SARIF 2.1.0 生成器。
   - `mcp-governance-rules-owasp`：首批 OWASP MCP Top 10 扫描规则实现（MCP01、MCP02、MCP03）。
   - `mcp-governance-cli`：基于 Picocli 的命令行可执行模块，组装 scanner 并提供终端输出与 exit code。
3. 建立第一个核心契约文件：`docs/contracts/scan-result.schema.json`（对应 SARIF 2.1.0 规范子集）。
4. 产出架构决策记录：`docs/adr/0001-project-scope-and-modules.md`。

---

## 2. 验收标准 (Acceptance Criteria)
- [ ] 架构师产出 ADR 0001 与 `docs/contracts/scan-result.schema.json` 契约
- [ ] 人类在本文档签署 `Approved: Yes`
- [ ] 工程师创建隔离分支 `task/001-maven-scaffold`
- [ ] 建立根 `pom.xml` 及 4 个子模块的 `pom.xml`
- [ ] 每个子模块包含标准的 `src/main/java` 与 `src/test/java` 目录结构及基础标识类
- [ ] 本地执行 `mvn clean compile` 构建成功，零告警
- [ ] 审核员完成 diff 审查，确认 `core` 模块零 Spring 依赖，给出 `verdict: PASS`
- [ ] 人类最终完成合并

---

## 3. 架构设计与契约约束 (Architect)
- **相关 ADR**: `docs/adr/0001-project-scope-and-modules.md`
- **相关契约**: `docs/contracts/scan-result.schema.json`
- **设计摘要**:
  1. 根 POM 管理版本：Java 17 LTS, Jackson 2.17.2, Picocli 4.7.6, SLF4J 2.0.13, JUnit 5.10.3, AssertJ 3.26.3。
  2. 划分 4 个纯 Java 模块：`core` (领域模型/SPI，无 Spring/无 Picocli)、`scanner` (引擎/SARIF)、`rules-owasp` (OWASP 规则实现)、`cli` (命令行入口)。
  3. 控制面强类型模型 `GovernedTool` 隔离 SDK wire 模型，计算 SHA-256 完整性指纹。
  4. 静态扫描器默认不拉起 STDIO 子进程，禁止未授权动态命令执行。
  5. 产出契约符合 OASIS SARIF 2.1.0 标准。

---

## 4. 人类批准记录 (Human Approval Gate)
Approved: Yes

---

## 5. 实现摘要 (Engineer)
- **PR 链接 / Commit Hash**: 分支 `task/001-maven-scaffold` (已完成提交)
- **主要变更文件**:
  - `pom.xml`: 根聚合工程配置，锁定 Java 17，统一版本管理与 Javadoc 配置
  - `mcp-governance-core`: 纯领域模型与 SPI 接口（`GovernedTool`, `Finding`, `PhysicalLocation`, `OwaspCategory`, `Severity`, `ScanRule`, `ScanContext`），并附带测试用例验证不可变性与 SHA-256 计算。无 Spring 依赖，无 Picocli 依赖。
  - `mcp-governance-scanner`: 静态执行引擎 `ScanEngine` 与符合 SARIF 2.1.0 标准契约的 `SarifSerializer`，及单测。
  - `mcp-governance-rules-owasp`: OWASP MCP Top 10 首条规则 `PlaintextTokenScanRule` (MCP01-001)，带测试用例验证明文凭证阻断与环境引用放行。
  - `mcp-governance-cli`: 基于 Picocli 的命令行工具 `McpScanCommand`，支持 exit code 2 阻断与 SARIF 报表导出，及端到端测试用例。
- **测试覆盖与门禁验证**:
  - `mvn clean compile`: 100% SUCCESS
  - `mvn test`: 10 个测试用例全部通过（0 failures, 0 errors, 0 skipped）
  - `mvn dependency:tree -pl mcp-governance-core`: 确认零 Spring 依赖
  - `mvn javadoc:javadoc`: 全部模块 SUCCESS 零告警通过
  - `mvn clean verify`: 全量验证通过

---

## 6. 审查与判定 (Reviewer)
- **Reviewer Verdict**: PENDING
- **Findings**:
  - [None]
