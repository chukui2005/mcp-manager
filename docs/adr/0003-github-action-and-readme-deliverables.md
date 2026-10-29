# ADR 0003: GitHub Action 自举静态审计、可执行胖包打包与双语 README 交付标准

## 1. 状态 (Status)
PROPOSED (待人类签署 `Approved: Yes` 后进入 ACCEPTED)

## 2. 背景与上下文 (Context)
在 Phase 1 的前序任务中，mcp-manager 已构建了：
1. 模块化 Maven 工程体系 (`core`, `scanner`, `rules-owasp`, `cli`)；
2. 遵循 OASIS SARIF 2.1.0 契约的报表生成引擎；
3. 支持解析标准 `mcp.json` 与 Claude Desktop 格式的纯静态解析器；
4. 针对 OWASP MCP Top 10 的核心检测规则集（`MCP01-001` 明文密钥、`MCP02-001` 权限蔓延通配符、`MCP03-001` 隐藏提示注入、`MCP03-002` 工具同名影子劫持）；
5. 终端 ANSI 彩色 Markdown 报告渲染器及 Exit Code 2 CI 阻断门禁。

然而，在对外开源交付与工程闭环（Phase 1 收官交付）层面，项目仍存在以下核心空缺：
- **执行形态分散**：当前 `mcp-governance-cli` 仅生成普通 jar，缺乏包含所有依赖及 `META-INF/services/` 的 Fat-JAR（可执行胖包），用户或 CI 环境难以直接 `java -jar` 一行运行；
- **缺乏 CI 自动化集成**：下游开发者若想在 GitHub CI 中审计 MCP 配置文件，缺乏开箱即用的 GitHub Action 封装，无法一键接入 GitHub Code Scanning 安全仪表盘；
- **自举审计未落地**：项目本身尚未建立基于自身静态扫描器的 GitHub Actions 工作流，未能实现“吃自己的狗粮”（Dogfooding）与 SARIF 上传自闭环验证；
- **项目门面文档缺失**：仓库根目录尚无正式的 `README.md` 与 `README.zh-CN.md`，外界无法在 10 秒内理解项目定位、架构蓝图、差异化价值及快速上手方式。

## 3. 架构决策 (Decision)

### 3.1 CLI 打包形态决策：Maven Shade Fat-JAR
决定在 `mcp-governance-cli/pom.xml` 中引入 `maven-shade-plugin`：
- 生成 `mcp-governance-cli-${version}-all.jar`，Main-Class 指向 `io.github.chukui2005.mcpgov.cli.McpScanCommand`；
- 配置 `ServicesResourceTransformer`，确保合并打包时各模块的 SPI 声明文件（如 `META-INF/services/io.github.chukui2005.mcpgov.core.spi.ScanRule`）不被覆盖丢失；
- 保留原始瘦 jar (`original-mcp-governance-cli-...jar`)，确保 Maven 模块间依赖解析不受污染。

### 3.2 GitHub Action 封装决策：根目录 Composite Action
决定在项目根目录提供 `action.yml`，选用 **Composite Action** 模式而非 Docker Container Action：
- **设计理由**：
  1. GitHub-hosted runner (`ubuntu-latest`) 自带经过优化的 Java 17/21 运行时与 Maven 缓存，Composite Action 仅需几秒即可就绪；而 Docker Action 每次运行均需拉取或构建体积较大的基础镜像，大幅增加 CI 冷启动耗时；
  2. 易于维护与自测：Composite Action 支持复用 runner 现有的构建环境与依赖缓存；
  3. 输入参数契约：
     - `target`: 待扫描的 MCP 配置文件或目录路径（默认 `mcp.json`）；
     - `output-sarif`: 输出 SARIF 文件的目标路径（默认 `scan-result.sarif`）；
     - `upload-sarif`: 是否自动调用 `github/codeql-action/upload-sarif@v3` 上传（布尔值，默认 `'true'`）；
     - `fail-on-findings`: 是否在发现 CRITICAL/HIGH 违规时中断构建（布尔值，默认 `'true'`）。

### 3.3 自举审计工作流决策：`.github/workflows/mcp-governance-scan.yml`
决定为本仓库建立自举扫描流水线：
1. 触发事件：`push` 到 `main` 分支及所有涉及配置变更或 Java 代码的 `pull_request`；
2. 流水线步骤：
   - 检出代码并设置 JDK 17；
   - 执行 `mvn clean package -DskipTests` 构建 CLI Fat-JAR；
   - 扫描仓库自测安全基准样例 `samples/clean-mcp.json`（预期零漏洞通过）；
   - 运行针对高危用例的阻断性验证（可选验证 Exit Code 2）；
   - 使用 `github/codeql-action/upload-sarif@v3` 上传生成的 `scan-result.sarif` 至 GitHub Security 标签页；
   - 设立门禁：任何非预期的 CRITICAL 漏洞将阻断 PR 合并（遵循《AGENTS.md》第 7 节）。

### 3.4 示例工程目录规范：`samples/`
在仓库根目录建立 `samples/` 目录，存放两类自测试与演示配置：
- `samples/clean-mcp.json`：合规配置样例，无明文 token、权限受限、描述干净、无影子冲突，供 CI 自举扫描作为通过基线；
- `samples/vulnerable-mcp.json`：故意包含 MCP01 明文凭据、MCP02 通配符权限、MCP03 提示注入的恶意配置样例，供演示及 CLI 阻断验证。

### 3.5 双语 README 交付标准
严格执行《AGENTS.md》第 8 节要求，交付 `README.md` (英文) 与 `README.zh-CN.md` (中文)：
1. **首屏 10 秒法则**：顶部清晰阐明“Java 原生 MCP 治理与静态审计层，扫描优先”；
2. **终端高保真视觉呈现**：提供 ANSI 彩色终端输出的文本卡片预览（展现 MCP01/02/03 漏洞定位）；
3. **差异化矩阵表**：对比 Wanaku、AI MCP Bridge、mocapi，横向对比“静态扫描”、“运行时审计”、“Spring Boot Starter”、“策略回放”、“OWASP 映射” 5 大维度；
4. **Quick Start**：提供一行 CLI 运行命令与 5 行 GitHub Action 工作流配置；
5. **系统架构图**：ASCII/Mermaid 形式呈现从 MCP Config 到 Scanner、Rule SPI、SARIF 与 Console 的数据流向；
6. **配置与规则参考**：列出 Phase 1 支持的 4 条 OWASP MCP 规则详解及参数；
7. **Roadmap 严格求真**：清晰勾勒 Phase 1（已完成）、Phase 2（运行时 Starter）、Phase 3（独立网关），严禁虚标；
8. **双向互链**：首行第一句提供语言切换徽标与链接。

## 4. 后果与影响 (Consequences)

### 正向影响 (Positive)
1. **开箱即用**：下游开发者可以直接引用 `uses: chukui2005/mcp-manager@v0.1.0` 或运行 `java -jar mcp-governance-cli-all.jar`，大幅降低接入摩擦；
2. **安全左移**：通过 GitHub Code Scanning 内联展示 MCP 安全风险，在 PR 阶段直接拦截高危 MCP Server 挂载；
3. **项目品牌与可读性**：完备的中英双语门面与差异化定位，让开源社区 10 秒建立对项目的完整认知；
4. **自举严密性**：仓库自身 CI 集成保证了未来所有变更绝不破坏 SARIF 生成与规则判定逻辑。

### 负向与限制 (Negative / Trade-offs)
1. **Fat-JAR 体积**：引入 Shade 插件后 CLI 产物体积将增加至约 15~20MB（包含 Jackson、Picocli、Logback 等），但对于本地与 CI 执行完全可接受；
2. **Composite Action 环境依赖**：Composite Action 假定当前环境已有 Java 17（或利用 action 内部的 setup-java 安装），若未来用户在无 Java 环境直接调用，可能需补充 setup 逻辑。

## 5. 替代方案考虑 (Alternatives)
- **方案 A：Docker Container Action**  
  *评估*：隔离性更好，但 GitHub Actions 每次 pull 镜像或构建需耗费 30~60 秒，且在 Windows/macOS runner 上无法直接运行 Linux 容器。否决。
- **方案 B：仅提供 Maven 源码构建使用**  
  *评估*：对普通开发者门槛过高，违背开源基础设施 30 秒跑起来的体验原则。否决。
- **方案 C：将双语写在同一个 README 内**  
  *评估*：文件过长，降低阅读体验，且不符合国际化开源仓库常规惯例。《AGENTS.md》明确规定必须拆分为独立的 `README.md` 与 `README.zh-CN.md`。否决。
