# 版本更新日志 (Changelog)

本项目遵循 [Semantic Versioning (SemVer 2.0.0)](https://semver.org/lang/zh-CN/) 语义化版本规范。
更新日志格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 标准。

---

## [Unreleased]

### Added
- 补充根目录 Maven Wrapper (`mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`)，基于 Maven 3.9.9 提供跨平台一致的开箱即用构建环境。
- 补充独立网关 `mcp-governance-gateway/Dockerfile` 与扫描器 `mcp-governance-cli/Dockerfile`，基于 Alpine JRE 17 并采用非 root 安全用户 (`10001:10001`) 运行。
- 补充根目录 `docker-compose.yml`，提供独立控制面网关一键启动与健康检查编排。
- 补充开源合规与门面文档：`CONTRIBUTING.md`、`SECURITY.md`、`CHANGELOG.md`。

---

## [0.3.0-gateway] - 2026-11-03

### Added
- **独立控制面网关模块 (`mcp-governance-gateway`)**：
  - 基于 Spring WebFlux 与 Netty 高性能非阻塞响应式内核构建。
  - 提供多协议出站上游代理：支持 Streamable HTTP / Server-Sent Events (SSE) 以及标准输入输出 (STDIO) 进程池托管。
  - 支持配置化动态路由热加载机制（支持 JSON、YAML 配置及 SPI 扩展）。
  - 集成全链路治理流水线：打通 Metrics 指标统计、Audit 审计记录、Tool ACL 细粒度准入控制以及 Parameter Validation 防御拦截。
  - 内置 Spring Boot Actuator 端点（`/actuator/health`, `/actuator/metrics`）及网关就绪态探针。

### Security
- 彻底移除任何假成功 Mock 代码，所有真实上游连接中断与网络故障均能精准向客户端抛出相应 RPC 错误。
- 强化 SSE 上游客户端异常自愈机制，防止长连接队列在网络抖动时发生内存泄漏。
- 统一暴露网关端口为 `18080`，默认支持非特权端口监听。

---

## [0.2.0-starter] - 2026-11-02

### Added
- **Spring Boot Starter 自动装配模块 (`mcp-governance-spring-boot-starter`)**：
  - 提供无侵入的 `@EnableMcpGovernance` 自动装配能力，通过 `@ConditionalOnClass` 与 `@ConditionalOnMissingBean` 保持松耦合。
  - 实现运行时上下文拦截责任链 `McpInterceptorChain` 与动态治理切面。
  - 细粒度工具准入控制拦截器 `ToolAclMcpInterceptor`：支持基于工具名及标签的动态鉴权。
  - 参数防御性校验拦截器 `ParameterValidationMcpInterceptor`：全面拦截路径穿越 (`../`)、非法字符注入及未授权参数。
  - 集成 Micrometer 指标采集体系，提供治理拒绝率、拦截耗时等核心运维指标。
  - 提供标准审计事件 `AuditEvent` 发布机制与敏感字段（Secret/Token）脱敏支持。

---

## [0.1.0-scan] - 2026-11-01

### Added
- **静态扫描核心引擎 (`mcp-governance-scanner`)**：
  - 支持静态分析 MCP 配置文件与工具元数据定义。
  - 实现 OWASP MCP Top 10 安全规则库（覆盖 MCP01 凭据暴露、MCP02 权限蔓延、MCP03 工具投毒等核心规则）。
- **核心模型与 Wire 隔离层 (`mcp-governance-core`)**：
  - 纯 Java 实现，零 Spring 外部依赖。
  - 引入 `GovernedTool` 抽象，严格防止 MCP SDK Wire 内部传输类型向治理层边界泄漏。
- **命令行工具与标准化报告 (`mcp-governance-cli` / `mcp-governance-action`)**：
  - 基于 picocli 构建开箱即用的命令行扫描器。
  - 实现 SARIF 2.1.0 标准静态分析报告生成器，支持无缝接入 GitHub Code Scanning。
  - 封装 GitHub Action 运行脚本。
