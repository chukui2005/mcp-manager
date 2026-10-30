# 0005. 运行时策略拦截器集与 Tool ACL 权限治理设计

* **状态**: 提议中 (Proposed)
* **日期**: 2026-10-29
* **决策者**: 架构师 AI、项目负责人
* **参考文档**: AGENTS.md, docs/environment-baseline.md, docs/adr/0004-spring-boot-starter-and-runtime-interception.md, .worklog/TASK-005.md

---

## 背景 (Context)

在 TASK-004 中，项目确立了运行时治理拦截契约（`McpInvocationContext`、`McpInvocationResult`、`McpInterceptor`、`McpInterceptorChain`），并在 `mcp-governance-spring-boot-starter` 中完成了脚手架自动装配与默认审计日志拦截器（`LoggingMcpInterceptor`）。

然而，面对企业多 Agent、多 MCP Server 的生产运行时场景，缺乏核心的动态访问控制和安全防护能力，主要体现在：
1. **OWASP MCP02 权限蔓延风险（Scope Creep）**：AI Agent 可能会自主选择调用超出其业务必要范围的敏感 Tool（如高危系统命令、未经授权的数据变更工具等）。系统必须具备基于 Tool 名称、模式匹配、角色/调用者维度的精细化访问控制列表（Tool ACL），支持**白名单允许模式（默认拒绝）**与**黑名单拦截模式**。
2. **越权与恶意入参注入风险（OWASP MCP05 / 规则校验）**：大模型生成的入参存在不确定性，可能包含路径遍历（如 `../`）、注入代码或超出取值范围的违规入参。需要具备灵活的轻量级参数校验拦截能力。
3. **架构强约束（严格只读与纯 Java 分层）**：
   - 依据《AGENTS.md》第 4 节：`mcp-governance-core` 必须保持**纯 Java 17，严禁引入 Spring 依赖**。策略匹配引擎、ACL 规则判断与参数校验核心逻辑必须在 `core` 模块中实现。
   - 依据《AGENTS.md》第 6.1 节：控制面模型与 Wire 传输模型强隔离，不得将任何特定 MCP SDK 的内部 DTO 耦合进策略模型中。
4. **Spring Boot 开箱即用与声明式绑定**：在 Starter 模块中，必须通过 `McpGovernanceProperties` 将 YAML/Properties 声明式策略与 `core` 的纯 Java 策略实例无缝绑定，并作为标准 `McpInterceptor` 注册到容器中的 `McpInterceptorChain`。

---

## 架构决策 (Decision)

### 1. 核心策略与 ACL 领域模型（置于 `mcp-governance-core`）

在 `io.github.chukui2005.mcpgov.core.runtime.policy` 包下定义纯 Java 策略模型：

#### 1.1 Tool ACL 访问控制策略 (`ToolAclPolicy`)
- **策略模式 (`AclMode`)**：
  - `WHITELIST`（白名单优先 / 默认拒绝）：仅允许明确在允许列表或匹配允许模式的 Tool 执行，未命中则抛出 `McpAccessDeniedException`。此为高安全环境推荐模式。
  - `BLACKLIST`（黑名单优先 / 默认放行）：仅阻断在黑名单或命中拒绝模式的 Tool 执行，其余放行。
- **匹配规则**：
  - 支持**精确匹配**（Exact Match，例如 `system_exec`）；
  - 支持**通配符/前缀匹配**（Pattern Match，例如 `file_*`、`admin:*`、`db:delete*`），采用轻量纯 Java 正则或通配符匹配器（无第三方依赖）。
- **主体/调用者粒度（Caller Scope）可选匹配**：
  - 支持基于 `McpInvocationContext.attributes()` 中的 `callerId` / `clientRole` 进行区分规则判定（例如非 admin 用户禁止调用 `admin:*` 前缀工具）。

#### 1.2 参数校验策略 (`ParameterValidationRule`)
- **规则定义**：
  - `toolNamePattern`: 适用的工具名称模式（支持 `*` 通配）；
  - `parameterName`: 校验的目标参数名；
  - `required`: 是否必填；
  - `regexPattern`: 正则表达式匹配约束（如禁止包含 `(\\.\\./|/etc/|cmd\\.exe)` 或要求符合特定业务格式）；
  - `errorMessage`: 校验失败时返回的安全提示信息。
- **校验违规处理**：
  - 一旦参数校验不通过，抛出 `McpAccessDeniedException` 或 `McpGovernanceException`，附带具体规则与违规字段，立即 Fail-Fast 阻断下游调用。

### 2. 运行时拦截器实现（置于 `mcp-governance-core`）

在 `mcp-governance-core` 中提供标准开箱即用的拦截器实现，实现 `McpInterceptor` 接口：

1. **`ToolAclMcpInterceptor` (排序号: `100`)**：
   - 在 `preHandle` 阶段执行；
   - 提取 `context.toolName()` 与 `context.attributes()`；
   - 依序进行黑名单命中检查、白名单许可检查；
   - 校验未通过时，抛出 `McpAccessDeniedException`，并在异常中明确标识违规原因（如 `"Tool 'system_exec' is blocked by ACL policy (MCP02 Violation)"`）。

2. **`ParameterValidationMcpInterceptor` (排序号: `200`)**：
   - 在 `preHandle` 阶段执行（在 ACL 判定之后）；
   - 根据工具名称筛选生效的参数规则；
   - 检查 `context.arguments()` 中的值；
   - 若必填项缺失或正则模式匹配失败，抛出 `McpGovernanceException`，阻断非法入参执行。

### 3. 配置属性与 Spring Boot 自动装配（置于 `mcp-governance-spring-boot-starter`）

在 `mcp-governance-spring-boot-starter` 中扩展配置与装配层：

#### 3.1 扩展 `McpGovernanceProperties`
在 `mcp.governance` 下新增 `acl` 与 `validation` 配置节点：
```yaml
mcp:
  governance:
    enabled: true
    fail-fast: true
    acl:
      enabled: true
      default-mode: WHITELIST # 或 BLACKLIST
      allowed-tools:
        - "calculator"
        - "search_*"
        - "file_read"
      blocked-tools:
        - "system_exec"
        - "bash"
        - "*_delete"
    validation:
      enabled: true
      rules:
        - tool-pattern: "file_*"
          parameter: "path"
          required: true
          disallowed-regex: ".*(\\.\\./|~).*" # 路径遍历防护
          error-message: "Path traversal is strictly prohibited"
```

#### 3.2 自动装配更新 `McpGovernanceAutoConfiguration`
- 当 `mcp.governance.acl.enabled=true` 时，自动实例化 `ToolAclPolicy` 并注册 `ToolAclMcpInterceptor` Bean（支持 `@ConditionalOnMissingBean`）。
- 当 `mcp.governance.validation.enabled=true` 时，自动解析规则集并注册 `ParameterValidationMcpInterceptor` Bean（支持 `@ConditionalOnMissingBean`）。
- 两者自动被已有的 `McpInterceptorChain` 按 `getOrder()` 升序组装入链。

### 4. 端到端拦截调用适配规范 (Adapter Model)

为方便宿主项目或上层网关将任意 MCP SDK（如 MCP Java SDK、Spring AI）接入治理链，在 Starter 中提供轻量标准适配模板：
- **`McpGovernanceAdapter` 抽象**：
  提供标准封装模板函数：
  ```java
  public McpInvocationResult executeGoverned(
      String toolName,
      Map<String, Object> arguments,
      Map<String, Object> attributes,
      ToolExecutionCallback callback
  ) {
      McpInvocationContext initialContext = new McpInvocationContext(toolName, arguments, attributes, Instant.now());
      McpInvocationContext governedContext = chain.applyPreHandle(initialContext);
      long start = System.currentTimeMillis();
      try {
          Object rawResult = callback.execute(governedContext.arguments());
          long duration = System.currentTimeMillis() - start;
          return chain.applyPostHandle(governedContext, McpInvocationResult.success(rawResult, duration));
      } catch (Throwable t) {
          long duration = System.currentTimeMillis() - start;
          chain.applyOnError(governedContext, t);
          if (t instanceof RuntimeException re) throw re;
          throw new McpGovernanceException("Tool execution failed", t);
      }
  }
  ```
- 彻底解耦底层 Wire 通信协议，保证无论是 STDIO、SSE 还是 HTTP 传输，均能通过统一契约进行运行时控制。

---

## 影响与后果 (Consequences)

### 正面影响
1. **OWASP MCP02 落地解决**：提供可配置、支持正则/通配符的白名单与黑名单机制，彻底阻断权限蔓延风险。
2. **纯 Java 架构纯粹性**：所有规则引擎和匹配器均为纯 Java 17 标准实现，无引入三方重量级表达式引擎（如 SpEL 或 OGNL），防范二次表达式注入漏洞。
3. **零侵入与高度可扩展**：Starter 通过 Spring Boot 配置直接开启或关闭，用户可通过注入自定义 Bean 轻松覆盖或扩展。
4. **统一执行顺序**：责任链按照 `ACL (100) -> 参数校验 (200) -> 业务执行 -> 脱敏审计日志 (1000)` 顺序流转，安全且直观。

### 潜在制约与权衡 (Trade-offs)
- 通配符与正则匹配在海量工具调用场景下的计算消耗：
  - *应对措施*：在 `ToolAclPolicy` 构造时预编译正则表达式（`java.util.regex.Pattern`），并在内部对已判断过的工具名采用轻量高速缓存（如 ConcurrentHashMap 缓存命中结果）。

---

## 替代方案 (Alternatives Considered)

1. **引入 Spring EL (SpEL) 实现动态策略表达式**：
   - *否决原因*：严重违反 `mcp-governance-core` 纯 Java 零 Spring 依赖红线，且 SpEL 若配置不当存在严重的 RCE 表达式注入风险。纯正则与通配符规则轻量、高效且绝对安全。
2. **使用外部 OPA (Open Policy Agent) 或 Rego 引擎**：
   - *否决原因*：引入外部网络调用或复杂 C-Go-JNI 依赖，破坏了 Java 原生、轻量可嵌入的定位。
