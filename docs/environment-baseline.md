# 环境与决策基线 (Environment Baseline)

> 本文档记录本项目的核心技术基线、外部依赖版本、环境约束与架构共识。
> 任何破坏此基线的变更必须通过架构师提出 ADR 并由人类批准。

## 1. 基础信息与决策

- **项目名称**：`mcp-manager`
- **GitHub 仓库**：`https://github.com/chukui2005/mcp-manager.git`
- **开源协议**：`Apache-2.0`
- **定位**：MCP 的治理与审计层，Java 原生，扫描优先。
- **Maven GroupId**：`io.github.chukui2005`（基于 GitHub 组织/用户名的官方 Maven Central Portal 标准命名空间）
- **当前 Phase**：`Phase 1: 静态扫描 CLI + GitHub Action`

## 2. 运行时与构建环境

- **JDK 版本**：Java 17 LTS（禁止在核心库使用仅 Java 21+ 支持的非预览语法，保证通用兼容性）
- **构建工具**：Apache Maven 3.9+（包含 Maven Wrapper `mvnw`）
- **字符编码**：UTF-8
- **模块分层原则**：
  - `mcp-governance-core`：纯 Java，严禁引入 Spring 或重量级框架。
  - `mcp-governance-scanner`：扫描引擎，纯 Java。
  - `mcp-governance-cli`：基于 `picocli` 的轻量命令行。
  - `mcp-governance-rules-owasp`：内置 OWASP MCP 规则实现。
  - `mcp-governance-action`：GitHub Action 包装层。

## 3. 依赖基线与集成协议

- **MCP Java SDK**：
  - 核心原则：**控制面模型与 Wire 模型严格分离**。
  - 内部 SDK 类型绝不泄漏至治理层核心公开 API。
  - `inputSchema` 处理原则：遵循 JSON Schema 2020-12，针对构造后的治理模型做非空校验与 SHA-256 签名。
- **输出格式**：
  - 静态扫描报表输出：标准 **SARIF 2.1.0** JSON 规范。
  - 控制台输出：人类友好的 ANSI 彩色 Markdown 摘要。

## 4. 安全与合规门禁

- **静态扫描安全默认**：默认不执行 STDIO 目标进程，扫描 STDIO MCP Server 必须显式传递 `--allow-execute`。
- **凭据处理**：绝不在报表、日志或异常信息中打印明文 Secret。
- **CI 门禁**：
  1. `mvn -B verify` 测试通过。
  2. SARIF 自举扫描零 Critical。
  3. Javadoc 零警告。
