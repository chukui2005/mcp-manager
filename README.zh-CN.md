# MCP Governance (mcp-manager)

> [English](README.md) | **简体中文**

**MCP 的治理与审计层，Java 原生，扫描优先。**

[![Build](https://img.shields.io/badge/build-passing-brightgreen)](#)
[![Java 17](https://img.shields.io/badge/Java-17-orange.svg)](#)
[![SARIF 2.1.0](https://img.shields.io/badge/SARIF-2.1.0-blue.svg)](https://docs.oasis-open.org/sarif/sarif/v2.1.0/sarif-v2.1.0.html)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

---

## 1. 什么是 MCP Governance？

随着企业级 AI 智能体应用的深入，开发者与组织接入了越来越多的 MCP Server、上百个工具端点与异构大模型。然而，当前在 MCP 体系下缺乏针对工具挂载与调用的统一可见性、安全审计与自动化策略执行层。

**MCP Governance (`mcp-manager`)** 是轻量级、纯 Java 原生的 MCP 静态审计与治理层基础设施。它能够在配置部署前对 MCP 配置文件进行静态分析，检测明文凭据泄露、权限范围蔓延、隐藏提示注入和工具同名影子劫持等风险，并将审计报告导出为标准的 **OASIS SARIF 2.1.0**，无缝接入 GitHub Code Scanning 安全仪表盘。

---

## 2. 终端高保真视觉效果

使用 `mcp-governance-cli` 对 MCP 配置文件执行静态扫描时，终端将呈现高可读性的 ANSI 彩色 Markdown 报告卡片，并依据漏洞等级触发严格的退出码阻断机制：

```text
================================================================================
                    MCP GOVERNANCE STATIC SCAN REPORT                           
================================================================================
Target: samples/vulnerable-mcp.json
Total Findings: 4 (CRITICAL: 1, HIGH: 2, MEDIUM: 1, LOW: 0)

[CRITICAL] MCP01-001 (Category: MCP01 Token Management)
  Rule: 明文凭据与 Token 泄露检测
  Target: github-server -> env.GITHUB_PERSONAL_ACCESS_TOKEN
  Message: 环境变量中检测到明文访问凭据/Token: ghp_****
  Remediation: 严禁明文硬编码敏感凭据，请改用系统环境变量引用或密钥管理系统。

[HIGH] MCP03-001 (Category: MCP03 Tool Poisoning)
  Rule: 工具描述中的隐藏提示注入检测
  Target: github-server -> read_secure_file
  Message: 工具描述包含试图篡改系统指令的注入关键词 ('ignore all previous instructions')。
  Remediation: 清理工具描述，移除所有尝试覆盖系统 Prompt 或越权控制模型的恶意指令文本。

[HIGH] MCP03-002 (Category: MCP03 Tool Poisoning)
  Rule: 跨服务工具同名影子劫持检测
  Target: 多个 MCP Server 声明了同名工具 'read_secure_file': [github-server, local-server]
  Message: 跨服务同名工具可能导致调用时的影子劫持或覆盖混淆风险。
  Remediation: 请为工具显式指定命名空间前缀或重命名冲突工具。

[MEDIUM] MCP02-001 (Category: MCP02 Overprivileged Scope)
  Rule: 过度宽泛权限与通配符参数检测
  Target: github-server -> read_secure_file -> wildcard_action
  Message: 工具参数 schema 定义了过度宽泛的通配正则表达式: '.*'
  Remediation: 严格收紧输入参数正则约束，建议使用枚举或具体格式校验。
================================================================================
Status: BLOCKED (Found 1 CRITICAL, 2 HIGH violations) -> Exit Code: 2
```

---

## 3. 差异化定位矩阵 (Why MCP Governance)

| 功能与特性维度 | Wanaku | AI MCP Bridge | mocapi | **MCP Governance (`mcp-manager`)** |
|---|---|---|---|---|
| **静态配置安全扫描** | ❌ 无 | ❌ 无 | ⚠️ 基础 Schema 校验 | ✅ **完整引擎 (`mcp.json` / Claude Desktop)** |
| **OWASP MCP Top 10 映射** | ❌ 无 | ❌ 无 | ❌ 无 | ✅ **内置 MCP01/02/03 核心规则集** |
| **SARIF 2.1.0 契约导出** | ❌ 无 | ❌ 无 | ❌ 无 | ✅ **原生 OASIS SARIF 标准报表** |
| **CI / GitHub Action 门禁** | ❌ 无 | ❌ 无 | ❌ 无 | ✅ **轻量 Composite Action + CodeQL 上传** |
| **Core 模块零 Spring 依赖** | ❌ 不适用 | ❌ 不适用 | ❌ 不适用 | ✅ **纯 Java 17 领域模型与扩展 SPI** |
| **运行时审计与 Starter** | ❌ 仅路由 | ❌ 仅桥接 | ❌ 仅 Mock | 🔄 **Phase 2 (Spring Boot Starter)** |
| **独立治理网关** | ✅ 活跃 | ⚠️ 代理 | ❌ 无 | 🔄 **Phase 3 (企业级网关)** |

---

## 4. 快速上手 (Quick Start，30 秒跑起来)

### 方式一：独立 CLI 可执行胖包 (Fat-JAR)

编译生成单 JAR 可执行包，直接对任何 MCP 配置文件发起扫描：

```bash
# 构建全部模块并生成 fat-jar
mvn clean package -DskipTests

# 扫描 MCP 配置文件并导出 SARIF 报告
java -jar mcp-governance-cli/target/mcp-governance-cli-0.1.0-SNAPSHOT-all.jar \
  mcp.json \
  --output scan-result.sarif
```

### 方式二：GitHub Action CI 流水线 (5 行 YAML)

直接在项目的 `.github/workflows/ci.yml` 中集成 MCP 静态审计能力：

```yaml
- name: Audit MCP Configuration
  uses: chukui2005/mcp-manager@v0.1.0
  with:
    target: 'mcp.json'
    upload-sarif: 'true'
    fail-on-findings: 'true'
```

---

## 5. 系统架构图 (Architecture)

```text
               +-------------------------------------------+
               |   MCP 配置文件 (mcp.json / Claude Desktop)|
               +-------------------------------------------+
                                     |
                                     v
               +-------------------------------------------+
               |         mcp-governance-scanner            |
               |        (纯静态 McpConfigParser 引擎)       |
               +-------------------------------------------+
                                     |
                        GovernedTool 与 ScanContext
                                     |
                                     v
               +-------------------------------------------+
               |        mcp-governance-rules-owasp         |
               |    SPI: ScanRule (MCP01 / MCP02 / MCP03)  |
               +-------------------------------------------+
                                     |
                              违规项列表 List<Finding>
                                     |
                   +-----------------+-----------------+
                   |                                   |
                   v                                   v
+------------------------------------+   +------------------------------------+
|        mcp-governance-cli          |   |          SarifSerializer           |
|  (ANSI 终端渲染与 Exit Code 2 门禁)|   |   (OASIS SARIF 2.1.0 GitHub 报告)  |
+------------------------------------+   +------------------------------------+
```

---

## 6. 内置安全规则参考 (Security Rules Reference)

| 规则编号 | OWASP 类别 | 严重级别 | 规则说明 | 修复建议 |
|---|---|---|---|---|
| **`MCP01-001`** | MCP01 Token 管理不当与密钥暴露 | `CRITICAL` | 静态扫描环境变量中的硬编码敏感 Token、API Key (`ghp_`, `sk-`, `Bearer `)。 | 严禁硬编码敏感凭据，使用系统环境变量或密钥托管组件注入。 |
| **`MCP02-001`** | MCP02 权限范围蔓延 | `MEDIUM` | 检查工具参数 schema 正则中是否包含过度宽松的通配模式 (`.*`, `.+`)。 | 收紧参数模式约束，优先使用限定长度或枚举值。 |
| **`MCP03-001`** | MCP03 工具投毒 | `HIGH` | 检查工具描述（Description）中是否包含试图篡改系统提示词的注入关键字。 | 清洗工具描述文本，去除越权提示注入内容。 |
| **`MCP03-002`** | MCP03 工具投毒 | `HIGH` | 静态扫描同一配置中跨 Server 声明的同名工具（影子工具劫持风险）。 | 重命名冲突工具或引入 Server 专属命名空间前缀。 |

---

## 7. 路线图 (Roadmap)

- **Phase 1: 静态扫描与审计 CLI (已结项交付)**
  - [x] 纯 Java 17 领域模型 (`GovernedTool`, `McpConfig`, 零 Spring 依赖)
  - [x] 标准 `mcp.json` 与 Claude Desktop 格式纯静态 AST 解析器
  - [x] 基于 SPI 的 OWASP MCP Top 10 规则集 (`MCP01-001`, `MCP02-001`, `MCP03-001`, `MCP03-002`)
  - [x] OASIS SARIF 2.1.0 序列化与 JSON Schema 契约校验
  - [x] CLI 可执行 Fat-JAR、ANSI 彩色终端报表与 Exit Code 门禁
  - [x] GitHub Composite Action 封装与仓库自举静态审计工作流
- **Phase 2: 运行时治理与 Spring Boot Starter (待启动)**
  - [ ] `mcp-governance-spring-boot-starter` 无侵入客户端/服务端拦截
  - [ ] 运行时上下文动态脱敏与 Prompt 过滤
  - [ ] 结构化审计日志与 OpenTelemetry 遥测指标接入
- **Phase 3: 独立治理网关与策略回放 (规划中)**
  - [ ] 独立 MCP 治理网关代理，强制 OAuth 2.1 鉴权
  - [ ] 策略动态重放与影子执行沙箱

---

## 8. 贡献与安全 (Contributing & Security)

- 欢迎参与共建！请参阅 [CONTRIBUTING.md](CONTRIBUTING.md) 了解代码风格、测试规范与 Pull Request 流程。
- 如需上报安全漏洞，请查阅 [SECURITY.md](SECURITY.md)。
