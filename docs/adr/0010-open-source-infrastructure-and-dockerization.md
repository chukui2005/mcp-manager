# 0010. 开源基建补齐、容器化交付与全生命周期规范基线

* **状态**: 提议中 (Proposed)
* **日期**: 2026-11-03
* **决策者**: 架构师 AI、项目负责人
* **参考文档**: AGENTS.md, docs/environment-baseline.md, docs/adr/0001-project-scope-and-modules.md, docs/adr/0008-phase3-gateway-architecture-preview.md, .worklog/TASK-014.md

---

## 背景 (Context)

在经历了 Phase 1（静态扫描器与 OWASP Top 10 规则库 `v0.1.0-scan`）、Phase 2（Spring Boot 运行时拦截器 Starter 与可观测性 `v0.2.0-starter`）以及 Phase 3（独立响应式控制面网关与治理责任链加固 `v0.3.0-gateway`）的连续高强度演进后，项目核心代码与测试资产已经全面成型。

然而，在对外开源发布、现代化云原生部署与开发者自举体验方面，项目仍存在以下关键基建缺失与合规断层：

1. **容器化交付能力缺失**：
   - 缺乏针对 `mcp-governance-gateway` 的生产级 Docker 镜像构建规范，使用者无法通过容器一键运行独立治理网关；
   - 缺乏针对 `mcp-governance-cli` 的开箱即用容器化扫描方案，违背了《AGENTS.md》规定的“`docker run ... scan` 30 秒跑起来”承诺；
   - 缺乏一键本地联调与体验的多容器编排文件（`docker-compose.yml`）。
2. **容器安全基线要求严格**：
   - 容器镜像必须防止特权逃逸，禁止以 `root` 用户运行进程；
   - 必须最小化镜像攻击面与镜像体积（优选轻量高安全的 Alpine JRE）；
   - 生产环境需提供容器健康检查机制（`HEALTHCHECK`），与 Spring Boot Actuator 无缝打通。
3. **开源治理与协同规范文档缺失**：
   - 缺少贡献者指南（`CONTRIBUTING.md`），外部开发者缺乏环境基线、分支规范与协作状态机遵循准则；
   - 缺少安全策略与脆弱性披露规范（`SECURITY.md`），不符合 GitHub 开源安全治理标准；
   - 缺少系统级版本发布历史（`CHANGELOG.md`），三大里程碑版本（`v0.1.0-scan`、`v0.2.0-starter`、`v0.3.0-gateway`）的演进细节与破坏性变更缺乏透明记录。
4. **统一构建自举环境缺失**：
   - 仓库内缺乏官方 Maven Wrapper 脚本（`mvnw`, `mvnw.cmd`, `.mvn/wrapper/`），导致不同开发者机器上的 Maven 版本差异（例如插件兼容性、并行构建行为）可能造成构建不一致。
5. **版本基线与模块拓扑树偏差**：
   - 当前 POM 依赖管理与模块版本需要对齐统一；
   - 仓库宪章《AGENTS.md》第 4 节的项目模块目录树未包含已落地的 `mcp-governance-gateway` 模块，架构文档存在时滞。

---

## 架构决策 (Decision)

### 1. 容器化交付架构与 Dockerfile 设计规范

#### 1.1 基础镜像选型与多阶段构建
- **构建阶段与运行阶段彻底隔离**：
  - 采用 **多阶段构建 (Multi-stage Build)** 模式，在构建阶段完成编译或在外部预编译后打入精简 JRE 运行镜像；
  - 运行时基础镜像统一采用轻量级、经过严格安全扫描的 **`eclipse-temurin:17-jre-alpine`**（体积通常 < 150MB，远优于全量 JDK 镜像或 Debian 镜像，且攻击面极小）。
- **非 root 安全用户基线**：
  - 镜像内部创建专用系统组与系统用户：`mcp`（UID/GID 统一设为 `10001:10001`）；
  - 严格限定进程运行权限为非 root，所有挂载目录、日志目录的读写所有权显式赋予 `mcp:mcp`。

#### 1.2 独立网关 Dockerfile (`mcp-governance-gateway/Dockerfile`)
- **工作目录与目录结构**：
  - 工作目录设为 `/app`；
  - 建立外部配置与动态路由挂载目录：`/app/config`；
- **暴露端口与健康探针**：
  - 暴露标准 Web 端口 `18080`；
  - 配置 Docker 原生 `HEALTHCHECK`，利用 `wget` 探针检查 `/actuator/health`（如 `http://localhost:18080/actuator/health`，超时 3s，间隔 15s，重试 3 次）；
- **环境变量参数化**：
  - 支持 `JAVA_OPTS`（JVM 堆内存与元空间调优，默认 `-XX:+UseG1GC -XX:+ExitOnOutOfMemoryError`）；
  - 支持 `SPRING_CONFIG_LOCATION` 与应用环境变量覆盖。

#### 1.3 命令行扫描器 Dockerfile (`mcp-governance-cli/Dockerfile`)
- **CLI 运行特性支持**：
  - 默认工作目录 `/workspace`，方便使用者将宿主机任意代码仓库或 MCP 配置文件挂载至容器内部；
  - 入口点 `ENTRYPOINT ["java", "-jar", "/app/mcp-governance-cli.jar"]`；
  - 默认命令 `CMD ["--help"]`，支持直接传递扫描参数（如 `docker run --rm -v $(pwd):/workspace mcp-scanner scan /workspace/mcp.json`）。

#### 1.4 本地多服务编排 (`docker-compose.yml`)
- 位于仓库根目录；
- 编排 `mcp-gateway` 服务：
  - 映射宿主机端口 `18080:18080`；
  - 挂载配置演示文件（如 `./config/routes.json` 或环境变量注入）；
  - 声明健康检查依赖与资源限制（CPU/Memory limits 模板）。

---

### 2. 开源合规与治理文档基线

#### 2.1 贡献者指南 (`CONTRIBUTING.md`)
- **环境要求**：明确 JDK 17+、Maven 3.9+、UTF-8 字符集；
- **分立角色与状态机**：详细介绍《AGENTS.md》规定的“三权分立”（架构师/工程师/审核员/人类决策者）以及状态机流转门禁（`DESIGN -> READY -> IN_REVIEW -> READY_TO_LAND -> DONE`）；
- **开发与提交流程**：分支命名规范（`feature/*`, `fix/*`）、代码风格（Google Java Style 变种）、Javadoc 零警告要求；
- **CI 门禁与自举要求**：阐明 PR 必跑的 `mvn verify`、SARIF 静态扫描自举（2.1.0 无 CRITICAL）及依赖合规检查。

#### 2.2 安全策略 (`SECURITY.md`)
- **支持版本矩阵**：明确当前维护的版本线（`0.3.x` 活跃维护；旧版本安全补丁策略）；
- **漏洞披露指引**：明确非公开负责任报告渠道（GitHub Private Vulnerability Reporting 或专用安全邮箱）；
- **SLA 承诺**：48 小时内确认接收，7 天内提供评估与临时规避方案，安全补丁随下一补丁版本统一发布；
- **核心安全设计原则重申**：凭据不落地、控制面与 Wire 严格分离、安全默认。

#### 2.3 版本演进日志 (`CHANGELOG.md`)
- 遵循 **Keep a Changelog 1.1.0** 标准；
- 系统性梳理并归档三大里程碑阶段：
  1. `[0.1.0-scan]` - 2026-11-01：Phase 1 静态扫描引擎、OWASP MCP Top 10 规则库（MCP01/MCP02/MCP03）、SARIF 2.1.0 报告器、picocli 命令行工具与 GitHub Action；
  2. `[0.2.0-starter]` - 2026-11-02：Phase 2 Spring Boot Starter、运行时上下文拦截责任链、工具调用动态准入控制（ACL）、参数白名单与路径穿越防御、Micrometer 指标采集与 Spring Actuator 端点、安全审计事件模型与敏感字段脱敏；
  3. `[0.3.0-gateway]` - 2026-11-03：Phase 3 独立控制面网关、Spring WebFlux/Netty 响应式内核、多协议出站代理（Streamable HTTP/SSE/STDIO 进程池托管）、动态路由热加载、全链路治理流水线贯通（消除 Mock 假成功）、连接容错与自愈。

---

### 3. 构建基础设施补齐 (Maven Wrapper)

- **标准化包装器资产**：
  - 根目录下补充 `mvnw`（POSIX 兼容 shell 脚本）与 `mvnw.cmd`（Windows 批处理脚本）；
  - 目录 `.mvn/wrapper/` 补充 `maven-wrapper.properties`，声明标准的 `distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip`；
  - 确保跨平台开发者均可通过 `./mvnw clean verify` 或 `mvnw.cmd clean verify` 获得确定性的构建环境。

---

### 4. 架构拓扑与版本基线一致性保障

- **《AGENTS.md》同步**：在项目结构树中补齐 `mcp-governance-gateway/` 模块定位说明，确保文档与代码拓扑 100% 吻合；
- **POM 版本统一性**：保持根 POM 及所有 6 个子模块版本一致（`0.1.0-SNAPSHOT`，为 Phase 1~3 全部功能完成后的一致性发布做准备）；
- **全文档语言要求**：所有开源基建文档与说明严格遵循项目宪章，统一采用中文呈现（保留通用英文技术术语）。

---

## 影响与后果 (Consequences)

### 正向影响
1. **开箱即用交付**：开发者和企业级用户能够通过 Docker / Docker Compose 极速体验独立网关与 CLI 扫描，降低试用成本；
2. **生产级安全强化**：非 root 用户与轻量 Alpine JRE 保证了最小攻击面与低资源消耗，自带 Actuator 健康探测保障 K8s 等编排平台调度；
3. **开源生态正规化**：补齐 CONTRIBUTING、SECURITY 与全历程 CHANGELOG，使项目达到高质量开源软件门面标准，提供规范透明的社区协同途径；
4. **确定性构建保证**：Maven Wrapper 消除了构建环境差异带来的非预期失败。

### 潜在风险与缓解
- **Alpine glibc 兼容性**：若后续子进程涉及原生 binary（如某些特殊 STDIO server），可能依赖 glibc；缓解：目前测试与标准 MCP server 均基于 Node.js/Python/Java，Alpine 均能良好兼容，若有特殊需求可派生 Debian-slim 变种。
- **镜像构建时依赖本地编译包**：建议在 Dockerfile 中支持直接打入预构建的 jar 包（适合 CI/CD 流程），并保持指令精简高效。

---

## 替代方案评估 (Alternatives)

1. **方案 A：基于 Ubuntu/Debian 基础镜像**
   - *劣势*：镜像体积达 300MB~500MB，包含大量无用包与潜在 CVE 漏洞，严重违背轻量化安全基线；
   - *结论*：不采纳，坚持选择 Alpine JRE。
2. **方案 B：将 Maven 全量构建打包直接内置在 Dockerfile 中**
   - *劣势*：在无本地缓存的网络环境下会导致镜像构建极慢，拉取 Maven 仓库容易受限；
   - *优势/折中*：Dockerfile 采用灵活设计，既可接收构建产物 `target/*.jar`，也可通过 CI 先执行 `mvn verify` 再进行快速装配打包。
