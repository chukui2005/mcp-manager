# 0005. TASK-005 技术实施蓝图：动态运行时策略拦截器集与 Spring Boot 深度集成

## 1. 架构交互图与责任链流转

```
 ┌────────────────────────────────────────────────────────┐
 │ 外部调用者 / Agent / 大模型                               │
 └───────────────────────────┬────────────────────────────┘
                             │ (1) 调用工具: toolName, arguments, attributes
                             ▼
 ┌────────────────────────────────────────────────────────┐
 │ McpGovernanceAdapter (端到端统一治理适配器)               │
 │ 构造初始 McpInvocationContext                            │
 └───────────────────────────┬────────────────────────────┘
                             │ (2) 进入责任链 applyPreHandle
                             ▼
 ┌─────────────────────────────────────────────────────────────────────────┐
 │ McpInterceptorChain                                                     │
 │                                                                         │
 │ ┌─────────────────────────────────────────────────────────────────────┐ │
 │ │ Interceptor 1: ToolAclMcpInterceptor (Order: 100)                   │ │
 │ │ 职责: OWASP MCP02 权限治理 (黑名单拦截 / 白名单校验)                   │ │
 │ │ 失败: 抛出 McpAccessDeniedException 立即 Fail-Fast 阻断             │ │
 │ └──────────────────────────────────┬──────────────────────────────────┘ │
 │                                    │ (通过)                             │
 │                                    ▼                                    │
 │ ┌─────────────────────────────────────────────────────────────────────┐ │
 │ │ Interceptor 2: ParameterValidationMcpInterceptor (Order: 200)       │ │
 │ │ 职责: 入参必填性校验、高危命令注入/路径遍历正则匹配防范              │ │
 │ │ 失败: 抛出 McpGovernanceException 阻断非法入参                     │ │
 │ └──────────────────────────────────┬──────────────────────────────────┘ │
 │                                    │ (通过)                             │
 │                                    ▼                                    │
 │ ┌─────────────────────────────────────────────────────────────────────┐ │
 │ │ Interceptor 3: LoggingMcpInterceptor (Order: 1000)                  │ │
 │ │ 职责: 敏感字段掩码脱敏 (password, token, secret 等) 及调用审计日志   │ │
 │ └──────────────────────────────────┬──────────────────────────────────┘ │
 └────────────────────────────────────┼────────────────────────────────────┘
                                      │ (3) 安全合规，执行真实底层 Tool
                                      ▼
 ┌────────────────────────────────────────────────────────┐
 │ 真实业务 Tool 执行 (ToolExecutionCallback)              │
 └───────────────────────────┬────────────────────────────┘
                             │ (4) 产出结果 / 捕获异常
                             ▼
 ┌────────────────────────────────────────────────────────┐
 │ McpInterceptorChain: applyPostHandle / applyOnError    │
 └───────────────────────────┬────────────────────────────┘
                             │ (5) 返回安全脱敏后的 McpInvocationResult
                             ▼
 ┌────────────────────────────────────────────────────────┐
 │ 转换回宿主 RPC / Wire 响应返回给调用方                  │
 └────────────────────────────────────────────────────────┘
```

---

## 2. 领域对象、接口契约与包结构划分

### 2.1 `mcp-governance-core` (包: `io.github.chukui2005.mcpgov.core.runtime.policy`)

#### 1. `AclMode.java` (枚举)
```java
package io.github.chukui2005.mcpgov.core.runtime.policy;

/**
 * Tool ACL 访问控制模式枚举。
 */
public enum AclMode {
    /** 白名单模式：仅允许白名单内工具执行，其余默认拒绝 (Default Deny)。 */
    WHITELIST,
    /** 黑名单模式：仅阻断黑名单内工具执行，其余默认放行 (Default Allow)。 */
    BLACKLIST
}
```

#### 2. `ToolAclPolicy.java` (不可变策略模型与匹配引擎)
```java
package io.github.chukui2005.mcpgov.core.runtime.policy;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Tool ACL 访问控制策略定义（纯 Java 17，无外部依赖）。
 */
public record ToolAclPolicy(
    boolean enabled,
    AclMode defaultMode,
    List<String> allowedTools,
    List<String> blockedTools
) {
    public ToolAclPolicy {
        defaultMode = defaultMode == null ? AclMode.WHITELIST : defaultMode;
        allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
        blockedTools = blockedTools == null ? List.of() : List.copyOf(blockedTools);
    }

    /**
     * 判断指定工具是否被当前策略允许执行。
     *
     * @param toolName 工具名称
     * @return true 表示允许执行，false 表示拒绝
     */
    public boolean isAllowed(String toolName) {
        if (!enabled) {
            return true;
        }
        if (toolName == null || toolName.isBlank()) {
            return false;
        }

        // 1. 黑名单优先检查：若命中任何 blockedPattern，立即拒绝
        for (String pattern : blockedTools) {
            if (matchesPattern(toolName, pattern)) {
                return false;
            }
        }

        // 2. 根据模式决定白名单检查
        if (defaultMode == AclMode.BLACKLIST) {
            return true;
        }

        // 3. WHITELIST 模式：必须至少命中一个 allowedPattern
        for (String pattern : allowedTools) {
            if (matchesPattern(toolName, pattern)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesPattern(String text, String pattern) {
        if (pattern.equals("*")) return true;
        if (!pattern.contains("*")) return text.equals(pattern);
        String regex = "\\Q" + pattern.replace("*", "\\E.*\\Q") + "\\E";
        return text.matches(regex);
    }
}
```

#### 3. `ParameterValidationRule.java` (参数校验规则模型)
```java
package io.github.chukui2005.mcpgov.core.runtime.policy;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 工具入参参数校验规则。
 */
public record ParameterValidationRule(
    String toolPattern,
    String parameterName,
    boolean required,
    String disallowedRegex,
    String requiredRegex,
    String errorMessage
) {
    public ParameterValidationRule {
        toolPattern = (toolPattern == null || toolPattern.isBlank()) ? "*" : toolPattern;
        Objects.requireNonNull(parameterName, "parameterName 不能为 null");
    }

    public boolean appliesTo(String toolName) {
        if (toolPattern.equals("*")) return true;
        if (!toolPattern.contains("*")) return toolName.equals(toolPattern);
        String regex = "\\Q" + toolPattern.replace("*", "\\E.*\\Q") + "\\E";
        return toolName.matches(regex);
    }
}
```

#### 4. `ToolAclMcpInterceptor.java` (拦截器实现)
置于 `io.github.chukui2005.mcpgov.core.runtime.policy`：
- 实现 `McpInterceptor`；
- 具备成员 `private final ToolAclPolicy policy`；
- `getOrder()` 返回 `100`；
- `preHandle` 实现：
  ```java
  if (!policy.isAllowed(context.toolName())) {
      throw new McpAccessDeniedException(
          String.format("Tool '%s' is rejected by ACL policy [%s] (OWASP MCP02 Scope Creep Violation)",
              context.toolName(), policy.defaultMode())
      );
  }
  return context;
  ```

#### 5. `ParameterValidationMcpInterceptor.java` (参数校验拦截器)
置于 `io.github.chukui2005.mcpgov.core.runtime.policy`：
- 实现 `McpInterceptor`；
- 具备成员 `private final List<ParameterValidationRule> rules`；
- `getOrder()` 返回 `200`；
- `preHandle` 实现：
  遍历命中的规则，若参数必填且不存在/为 null，抛出异常；若存在参数值且命中 `disallowedRegex` 或未命中 `requiredRegex`，抛出 `McpGovernanceException`。

---

### 2.2 `mcp-governance-spring-boot-starter` 模块配置扩展

#### 1. 扩展 `McpGovernanceProperties.java`
在现有类中增加 `AclProperties` 与 `ValidationProperties` 嵌套内部类：
```java
private AclProperties acl = new AclProperties();
private ValidationProperties validation = new ValidationProperties();

public static class AclProperties {
    private boolean enabled = true;
    private AclMode defaultMode = AclMode.WHITELIST;
    private List<String> allowedTools = new ArrayList<>();
    private List<String> blockedTools = new ArrayList<>();
    // getters and setters with Javadoc
}

public static class ValidationProperties {
    private boolean enabled = false;
    private List<RuleProperties> rules = new ArrayList<>();

    public static class RuleProperties {
        private String toolPattern = "*";
        private String parameter;
        private boolean required = false;
        private String disallowedRegex;
        private String requiredRegex;
        private String errorMessage;
        // getters and setters with Javadoc
    }
}
```

#### 2. 更新 `McpGovernanceAutoConfiguration.java`
- 注册 `ToolAclMcpInterceptor` Bean：
  ```java
  @Bean
  @ConditionalOnMissingBean(ToolAclMcpInterceptor.class)
  @ConditionalOnProperty(prefix = "mcp.governance.acl", name = "enabled", havingValue = "true", matchIfMissing = true)
  public ToolAclMcpInterceptor toolAclMcpInterceptor(McpGovernanceProperties properties) {
      McpGovernanceProperties.AclProperties acl = properties.getAcl();
      ToolAclPolicy policy = new ToolAclPolicy(
          acl.isEnabled(),
          acl.getDefaultMode(),
          acl.getAllowedTools(),
          acl.getBlockedTools()
      );
      return new ToolAclMcpInterceptor(policy);
  }
  ```
- 注册 `ParameterValidationMcpInterceptor` Bean：
  ```java
  @Bean
  @ConditionalOnMissingBean(ParameterValidationMcpInterceptor.class)
  @ConditionalOnProperty(prefix = "mcp.governance.validation", name = "enabled", havingValue = "true")
  public ParameterValidationMcpInterceptor parameterValidationMcpInterceptor(McpGovernanceProperties properties) {
      // 映射 RuleProperties 到 ParameterValidationRule
      List<ParameterValidationRule> rules = properties.getValidation().getRules().stream()
          .map(r -> new ParameterValidationRule(
              r.getToolPattern(),
              r.getParameter(),
              r.isRequired(),
              r.getDisallowedRegex(),
              r.getRequiredRegex(),
              r.getErrorMessage()
          ))
          .toList();
      return new ParameterValidationMcpInterceptor(rules);
  }
  ```

#### 3. 新增 `McpGovernanceAdapter.java` 辅助器 (可选且便捷)
置于 `io.github.chukui2005.mcpgov.spring.boot.adapter`：
提供高层 API，让宿主业务能一行代码接入治理拦截生命周期，统一异常处理与耗时统计。

---

## 3. 实施细节与文件清单

### 3.1 研发工程师工作清单

1. **`mcp-governance-core` 策略层研发 (纯 Java 17，无 Spring 依赖)**:
   - 新增包 `io.github.chukui2005.mcpgov.core.runtime.policy`；
   - 新建 `AclMode.java`；
   - 新建 `ToolAclPolicy.java`；
   - 新建 `ParameterValidationRule.java`；
   - 新建 `ToolAclMcpInterceptor.java`；
   - 新建 `ParameterValidationMcpInterceptor.java`；
   - 编写单测 `ToolAclMcpInterceptorTest.java`（覆盖白名单放行、白名单默认拒绝、黑名单精确阻断、通配符通配、空值异常等）；
   - 编写单测 `ParameterValidationMcpInterceptorTest.java`（覆盖必填校验、路径遍历禁用正则、非法格式阻断等）。

2. **`mcp-governance-spring-boot-starter` 自动装配与适配层研发**:
   - 修改 `McpGovernanceProperties.java`：增加 `AclProperties` 与 `ValidationProperties` 配置项及其 Getter/Setter 和完整 Javadoc；
   - 修改 `McpGovernanceAutoConfiguration.java`：通过 `@Bean` 条件装配上述两个拦截器；
   - 新建 `McpGovernanceAdapter.java` 提供便捷调用包装模板；
   - 扩展 `McpGovernanceAutoConfigurationTest.java`：
     - 测试 YAML 属性绑定装配 `ToolAclMcpInterceptor`；
     - 测试当声明了自定义 `ToolAclMcpInterceptor` 时，`@ConditionalOnMissingBean` 生效；
     - 测试白名单模式下通过拦截链阻断未授权 Tool 调用。

3. **编译与验证门禁**:
   - 执行 `mvn -B clean verify`，确保所有单元测试 100% 通过；
   - 执行 `mvn -B javadoc:javadoc`，保证公开类与方法 Javadoc 零警告。

---

## 4. 测试用例与验收设计

| 测试类 | 核心用例方法 | 验证目标 |
| :--- | :--- | :--- |
| `ToolAclMcpInterceptorTest` | `testWhitelistAllowed()` | 白名单模式下，在列表中的 Tool 正常放行，上下文不被篡改 |
| `ToolAclMcpInterceptorTest` | `testWhitelistDefaultDeny()` | 白名单模式下，未在列表中的 Tool 抛出 `McpAccessDeniedException` (MCP02) |
| `ToolAclMcpInterceptorTest` | `testBlacklistBlocked()` | 黑名单模式下，命中黑名单规则的 Tool 抛出拒绝异常 |
| `ToolAclMcpInterceptorTest` | `testWildcardPatternMatching()` | 验证通配符 `file_*`、`db:*` 能准确匹配前缀与通配 |
| `ParameterValidationMcpInterceptorTest` | `testRequiredParameterMissing()` | 缺少必填参数时拦截并抛出治理异常 |
| `ParameterValidationMcpInterceptorTest` | `testDisallowedRegexTraversal()` | 参数包含 `../` 命中防路径遍历正则，抛出阻断异常 |
| `McpGovernanceAutoConfigurationTest` | `testAclAndValidationAutoConfigured()` | 验证 Starter 正确将配置属性绑定并注册两个拦截器入链 |
| `McpGovernanceAutoConfigurationTest` | `testCustomAclInterceptorOverride()` | 验证自定义 ACL Bean 能够覆盖默认装配 |
