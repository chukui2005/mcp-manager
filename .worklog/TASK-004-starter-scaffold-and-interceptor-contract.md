# Task: TASK-004 - mcp-governance-spring-boot-starter 模块脚手架与运行时请求拦截契约设计

## 状态元数据
- **Status**: DONE
- **Owner**: Human
- **Approved**: Yes
- **Created**: 2026-10-29
- **Updated**: 2026-10-29

---

## 1. 任务背景与目标
TASK-004 是 Phase 2（运行时治理与 Spring Boot Starter）的启航任务。
在 Phase 1 实现了静态配置审计、OWASP 规则检测、GitHub Action 自举与开源双语门面后，本项目从“静态离线扫描”向“运行时在线拦截与治理”推进：

1. **Starter 模块架构设计与依赖隔离**:
   - 遵照《AGENTS.md》第 4、5.1 与 6.1 节规范，设立 `mcp-governance-spring-boot-starter` 模块；
   - 严格保证 `mcp-governance-core` 纯 Java，严禁引入 Spring 依赖；
   - 在 starter 中引入 `@ConditionalOnClass` / `@ConditionalOnMissingBean` 条件装配机制，绝不强制引入不必要依赖；
   - 配置属性通过 `@ConfigurationProperties(prefix = "mcp.governance")` 绑定并具备完备 Javadoc 与安全默认值。

2. **运行时控制面拦截模型与契约设计**:
   - 设计运行时 MCP 请求/响应审计拦截器契约（如 `McpExecutionInterceptor` / `McpInvocationContext` 等不可变领域模型）；
   - 控制面模型与 Wire 模型严格分离，禁止将 MCP Java SDK 内部类型泄漏到治理层公开接口；
   - 支持前置拦截（鉴权、合规、脱敏）、后置审计与异常遥测。

3. **脚手架骨架与条件装配验证**:
   - 提供自动装配配置类 `McpGovernanceAutoConfiguration`；
   - 编写 Spring Boot 上下文测试用例，验证在未启用、缺省依赖或显式配置下的条件装配行为与非侵入性。

---

## 2. 验收标准 (Acceptance Criteria)
- [x] 架构师产出 ADR 0004: `docs/adr/0004-spring-boot-starter-and-runtime-interception.md`
- [x] 架构师产出设计蓝图: `docs/architecture/0004-blueprint.md`
- [x] 架构师定义/扩展运行时契约或接口规范
- [x] 人类项目负责人审查设计方案并在本文件签署 `Approved: Yes`
- [x] 工程师在隔离分支中完成 `mcp-governance-spring-boot-starter` 模块脚手架创建并纳入根 `pom.xml`
- [x] 工程师实现核心拦截器接口、上下文不可变模型与自动配置类
- [x] 工程师编写条件装配切面单测，验证 `@ConditionalOnMissingBean` 与属性开箱即用性
- [x] `mvn clean verify` 100% 编译通过，测试全部 PASS，Javadoc 零警告
- [x] 审核员完成代码与架构审查，输出 `verdict: PASS`
- [x] 人类项目负责人完成 PR 合并并推进到 DONE

---

## 3. 架构设计与契约约束 (Architect)
- **相关 ADR**: `docs/adr/0004-spring-boot-starter-and-runtime-interception.md`
- **技术蓝图**: `docs/architecture/0004-blueprint.md`
- **相关契约**: 
  - 核心运行时契约接口：`io.github.chukui2005.mcpgov.core.runtime.McpInterceptor`
  - 不可变调用上下文模型：`io.github.chukui2005.mcpgov.core.runtime.McpInvocationContext`
  - 调用结果抽象：`io.github.chukui2005.mcpgov.core.runtime.McpInvocationResult`
  - 责任链执行器：`io.github.chukui2005.mcpgov.core.runtime.McpInterceptorChain`
- **设计摘要**: 
  1. 遵照《AGENTS.md》硬性规范，保持 `core` 纯 Java 17，无 Spring 依赖；
  2. Starter 模块采用 Spring Boot 3.3.4 基线，基于 `AutoConfiguration.imports` 与 `@ConditionalOnClass` / `@ConditionalOnMissingBean` 实现条件装配；
  3. 控制面模型与 Wire 传输彻底解耦，公开 API 绝不暴露 MCP SDK 内部类型；
  4. 配置属性前缀 `mcp.governance`，具备开箱即用的敏感参数脱敏与调用审计能力。

---

## 4. 人类批准记录 (Human Approval Gate)
> 状态从 `DESIGN` 推进到 `READY` 的必要条件。必须为独立一行精确的 `Approved: Yes`。
Approved: Yes

---

## 5. 实现摘要 (Engineer)
- **PR 分支**: `feature/TASK-004-starter-and-runtime-interceptor`
- **主要变更文件**: 
  - `pom.xml`: 引入 Spring Boot 3.3.4 `dependencyManagement` 并添加 `mcp-governance-spring-boot-starter` 模块声明；
  - `mcp-governance-core/src/main/java/io/github/chukui2005/mcpgov/core/runtime/`: 纯 Java 运行时调用拦截核心抽象：
    * `McpInvocationContext`: 不可变 Record，入参及元数据防篡改只读视图；
    * `McpInvocationResult`: 不可变 Record，支持 `success`/`failure` 工厂方法；
    * `McpInterceptor`: 核心拦截接口，定义 `preHandle`、`postHandle`、`onError` 与 `getOrder`；
    * `McpInterceptorChain`: 责任链执行器，升序执行前置拦截、逆序执行后置拦截，支持 Fail-Fast；
    * `McpGovernanceException` 与 `McpAccessDeniedException`: 治理拦截与访问违规阻断异常；
  - `mcp-governance-core/src/test/java/io/github/chukui2005/mcpgov/core/runtime/McpInterceptorChainTest.java`: 覆盖上下文不可变性、责任链升/逆序、安全阻断与容错；
  - `mcp-governance-spring-boot-starter/pom.xml`: Spring Boot Starter 脚手架与依赖隔离配置；
  - `mcp-governance-spring-boot-starter/src/main/java/io/github/chukui2005/mcpgov/spring/boot/`:
    * `McpGovernanceProperties`: 映射 `mcp.governance` 配置，默认开启并具备完备敏感字段脱敏属性；
    * `LoggingMcpInterceptor`: 开箱即用的审计与敏感入参脱敏拦截器（支持递归 Map 掩码）；
    * `McpGovernanceAutoConfiguration`: 条件装配入口类，采用 `@ConditionalOnClass`、`@ConditionalOnMissingBean` 与 `@ConditionalOnProperty`；
  - `mcp-governance-spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`: Spring Boot 3.x 自动配置注册声明；
  - `mcp-governance-spring-boot-starter/src/test/java/io/github/chukui2005/mcpgov/spring/boot/autoconfigure/McpGovernanceAutoConfigurationTest.java`: 覆盖默认装配、disabled 关闭、Bean 覆盖、自定义拦截器注入与敏感参数脱敏等 6 组测试。
- **测试覆盖说明**: 
  - `mvn -B clean verify` 全部通过，全工程 40 个测试用例（core 13 + scanner 5 + rules 10 + cli 6 + starter 6）100% PASS；
  - `mvn -B javadoc:javadoc` 零警告，所有公开类、Record、方法与属性均具备规范规范中文 Javadoc。
- **分支状态**: 已在隔离分支完成提交（commit: `d2c6721`），严格遵守禁止直接 push 到 main 规范。

---

## 6. 审查与判定 (Reviewer)
- **Reviewer Verdict**: PASS
- **Findings**:
  - [无阻断性问题]：代码分层清晰，严格保持 `core` 纯 Java 零 Spring 依赖；控制面调用上下文使用不可变 Record 防篡改；Starter 自动装配具备完备条件注解；开箱即用支持 OWASP MCP01/MCP08 敏感参数脱敏与调用审计；全工程 40 个单元测试 100% 通过且 Javadoc 零警告。满足 `READY_TO_LAND` 门禁标准。
