# TASK-014 实现蓝图：开源基建补齐与 Docker 容器化交付

## 1. 任务背景与目标

本蓝图为 `TASK-014` 开源基础设施补齐与 Docker 容器化交付提供清晰、可落地的工程实施方案。
目标是为整个 `mcp-manager` 项目交付开箱即用的容器化镜像构建方案、多服务编排配置、标准的开源社区规范文档（CONTRIBUTING、SECURITY、CHANGELOG）以及环境无关的 Maven Wrapper 脚本，并完成架构拓扑树的一致性同步。

严格遵循《AGENTS.md》与《.agents/architect.md》规范：
- 架构师角色只读，仅产出设计蓝图、ADR 与契约；
- 实施阶段交由研发工程师（engineer）负责编写；
- 审核员（reviewer）严格对照本蓝图进行门禁复核。

---

## 2. 交付清单与文件映射

| 序号 | 交付文件类别 | 文件路径 | 职责与内容要求 |
| :--- | :--- | :--- | :--- |
| 1 | **网关 Dockerfile** | `mcp-governance-gateway/Dockerfile` | 多阶段/精简构建，基于 `eclipse-temurin:17-jre-alpine`，非 root 用户（UID 10001），暴露 18080，配置健康检查 |
| 2 | **CLI 扫描器 Dockerfile** | `mcp-governance-cli/Dockerfile` | 轻量 Alpine JRE 17，非 root 用户，默认入口点为 CLI，支持挂载扫描与参数穿透 |
| 3 | **服务编排文件** | `docker-compose.yml` | 根目录一键启动 `mcp-gateway`，支持挂载路由配置与环境变量配置 |
| 4 | **开源贡献指南** | `CONTRIBUTING.md` | 中文，明确环境要求、三权分立开发流程、PR 协作规范与 CI 门禁标准 |
| 5 | **安全漏洞策略** | `SECURITY.md` | 中文，支持版本线、漏洞披露渠道、响应 SLA 及核心安全基线 |
| 6 | **版本演进日志** | `CHANGELOG.md` | 中文，严格 Keep a Changelog 格式，系统记录 `v0.1.0-scan`, `v0.2.0-starter`, `v0.3.0-gateway` 历程 |
| 7 | **Maven 包装器资产** | `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties` | 跨平台一键构建脚本与 Maven 3.9.9 标准分发包定义 |
| 8 | **架构拓扑同步** | `AGENTS.md` | 在项目结构与文档边界树中增加 `mcp-governance-gateway/` 模块定位说明 |

---

## 3. 详细设计与实现规约

### 3.1 网关 Dockerfile (`mcp-governance-gateway/Dockerfile`)

#### 设计要点：
1. **基础镜像**：`eclipse-temurin:17-jre-alpine`；
2. **安全配置**：
   - 执行 `addgroup -g 10001 -S mcp && adduser -u 10001 -S mcp -G mcp`；
   - 切换非 root 用户：`USER mcp:mcp`；
3. **目录规划**：
   - 工作目录：`/app`；
   - 配置挂载点：`/app/config`；
4. **包复制**：
   - 将已编译构建好的 `mcp-governance-gateway/target/mcp-governance-gateway-*.jar` 拷贝至 `/app/gateway.jar`（或采用两阶段：`FROM maven:3.9-eclipse-temurin-17-alpine AS builder` + 运行阶段）；
   - 为适应本地极速构建与 CI 环境，采用优化的精简装配阶段；
5. **探针与暴露**：
   - `EXPOSE 18080`；
   - `HEALTHCHECK --interval=15s --timeout=3s --retries=3 CMD wget -qO- http://localhost:18080/actuator/health || exit 1`；
6. **启动命令**：
   - `ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/gateway.jar"]`。

---

### 3.2 CLI 扫描器 Dockerfile (`mcp-governance-cli/Dockerfile`)

#### 设计要点：
1. **基础镜像**：`eclipse-temurin:17-jre-alpine`；
2. **安全用户**：同样创建并启用 `mcp:mcp` 用户；
3. **工作区目录**：
   - 设为 `/workspace`；
   - 方便宿主机运行命令：`docker run --rm -v $(pwd):/workspace mcp-scanner scan mcp.json`；
4. **程序入口**：
   - 放置 `/app/cli.jar`；
   - `ENTRYPOINT ["java", "-jar", "/app/cli.jar"]`；
   - 默认命令 `CMD ["--help"]`。

---

### 3.3 根目录多服务编排 (`docker-compose.yml`)

#### 样板结构：
```yaml
version: '3.8'

services:
  mcp-gateway:
    build:
      context: .
      dockerfile: mcp-governance-gateway/Dockerfile
    image: mcp-governance-gateway:latest
    container_name: mcp-governance-gateway
    restart: unless-stopped
    ports:
      - "18080:18080"
    environment:
      - JAVA_OPTS=-Xms256m -Xmx512m -XX:+UseG1GC
      - SPRING_PROFILES_ACTIVE=prod
      - MCP_GATEWAY_TOKEN_AUTH_ENABLED=false
    volumes:
      - ./config:/app/config:ro
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:18080/actuator/health || exit 1"]
      interval: 15s
      timeout: 3s
      retries: 3
```

---

### 3.4 开源规范文档规约

#### 1. `CONTRIBUTING.md`（贡献指南）
- **欢迎与导言**：介绍项目使命（MCP 的治理与审计层，Java 原生，扫描优先）；
- **开发环境配置**：
  - JDK 17 及以上；
  - Maven 3.9+（推荐使用自带 `./mvnw`）；
  - 编码字符集严格采用 UTF-8；
- **核心角色分工与三权分立**：
  - 介绍《AGENTS.md》中的架构师、工程师、审核员协作状态机：`DESIGN -> READY -> IN_REVIEW -> READY_TO_LAND -> DONE`；
- **开发与提交流程**：
  - 代码格式、Javadoc 无警告要求；
  - 架构变更必须有 ADR；
  - 禁止将 SDK Wire 类型泄漏至治理层；
- **质量门禁（PR 验收清单）**：
  - `mvn verify` 全绿；
  - 扫描器自身 SARIF 自举无 Critical；
  - 契约测试与单元测试覆盖。

#### 2. `SECURITY.md`（安全策略）
- **支持版本 (Supported Versions)**：
  - `0.3.x` 处于活跃支持；
- **报告漏洞方式 (Reporting a Vulnerability)**：
  - 严禁通过公开 Issue 报告安全漏洞；
  - 使用 GitHub Security Advisories 私密提交漏洞，或发送至指定安全联络邮箱；
- **漏洞响应时效 (SLA)**：
  - 48 小时内确认接收；
  - 7 天内提供漏洞评估与防护方案；
- **安全基本原则**：
  - 凭据绝不落盘与明文回显；
  - 最小权限原则与默认安全（Secure by Default）；
  - 非 root 容器隔离。

#### 3. `CHANGELOG.md`（版本更新日志）
严格遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 格式：
- `[Unreleased]`：当前开发中变动与计划；
- `[0.3.0-gateway] - 2026-11-03`：
  - **Added**：Phase 3 独立控制面网关模块 `mcp-governance-gateway`，基于 Netty/Spring WebFlux 高性能响应式内核；
  - **Added**：出站多协议客户端（Streamable HTTP/SSE 与 STDIO 进程池托管）；
  - **Added**：动态路由加载机制（JSON/YAML/SPI）；
  - **Added**：全链路治理流水线打通（Metrics、Audit、ACL、Validation 拦截器挂载）；
  - **Security**：移除伪造成功 Mock，连接超时/中断真实暴露；SseUpstreamClient 响应队列防泄漏自愈；
  - **Added**：Docker 容器化与 docker-compose 一键启动支持。
- `[0.2.0-starter] - 2026-11-02`：
  - **Added**：Phase 2 Spring Boot Starter 自动装配模块 `mcp-governance-spring-boot-starter`；
  - **Added**：运行时上下文拦截责任链 `McpInterceptorChain` 与动态治理切面；
  - **Added**：细粒度工具准入控制 `ToolAclMcpInterceptor`；
  - **Added**：参数防御性校验与路径穿越防御 `ParameterValidationMcpInterceptor`；
  - **Added**：Micrometer 指标统计与 Spring Boot Actuator 端点暴露；
  - **Added**：审计事件发布与数据脱敏支持。
- `[0.1.0-scan] - 2026-11-01`：
  - **Added**：Phase 1 静态扫描引擎 `mcp-governance-scanner`；
  - **Added**：核心领域模型与 Wire 隔离层 `mcp-governance-core`；
  - **Added**：OWASP MCP Top 10 规则库 `mcp-governance-rules-owasp`（MCP01、MCP02、MCP03）；
  - **Added**：SARIF 2.1.0 报告生成器；
  - **Added**：picocli 命令行工具 `mcp-governance-cli` 与 GitHub Action 封装。

---

### 3.5 Maven Wrapper 规范

- 在根目录建立 `.mvn/wrapper/maven-wrapper.properties`：
  ```properties
  distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip
  wrapperUrl=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar
  ```
- 生成标准的跨平台入口脚本：
  - `mvnw`（Shell 脚本，兼容 POSIX 环境）；
  - `mvnw.cmd`（Windows 批处理脚本）。

---

### 3.6 项目拓扑树更新 (`AGENTS.md`)

在 `AGENTS.md` 第 4 节“项目结构与文档边界”中更新模块列表：
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
├── mcp-governance-spring-boot-starter/   # Phase 2
└── mcp-governance-gateway/        # Phase 3 独立控制面网关
```

---

## 4. 实施顺序与交付物核验清单

研发工程师在获得人类授权 `Approved: Yes` 进入 `READY` 状态后，按照以下依赖顺序逐项落地：

1. **Step 1: Maven Wrapper 落地**
   - 建立 `.mvn/wrapper/maven-wrapper.properties`；
   - 落地 `mvnw` 与 `mvnw.cmd` 脚本并赋予可执行权限；
   - 验证：在本地执行 `./mvnw -version` 或 `mvnw.cmd -version` 输出正确的 Maven 3.9.9 信息。
2. **Step 2: 容器化资产落地**
   - 编写 `mcp-governance-gateway/Dockerfile`；
   - 编写 `mcp-governance-cli/Dockerfile`；
   - 编写根目录 `docker-compose.yml`；
   - 验证：Dockerfile 语法合规，非 root 用户声明无误，健康检查端点正确。
3. **Step 3: 开源合规文档落地**
   - 编写 `CONTRIBUTING.md`；
   - 编写 `SECURITY.md`；
   - 编写 `CHANGELOG.md`；
   - 验证：全中文撰写，内容详尽准确，对齐三大版本里程碑。
4. **Step 4: 拓扑对齐与全量验证**
   - 更新 `AGENTS.md` 项目结构树；
   - 执行全工程编译与测试构建：`mvn clean verify` 保证零故障。
