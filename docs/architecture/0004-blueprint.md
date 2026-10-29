# 0004. TASK-004 技术实施蓝图：Spring Boot Starter 模块脚手架与运行时请求拦截契约

## 1. 架构交互图与调用拦截流程

```
 ┌────────────────────────────────────────────────────────┐
 │ 外部调用者 / Agent / 大模型                               │
 └───────────────────────────┬────────────────────────────┘
                             │ (1) 发起 Tool 调用 (RPC/Wire 格式)
                             ▼
 ┌────────────────────────────────────────────────────────┐
 │ 运行时适配层 (Spring AI / MCP SDK Adapter)                │
 │ 职责: 解包 Wire 载荷，封装为不可变 McpInvocationContext      │
 └───────────────────────────┬────────────────────────────┘
                             │ (2) 传入统一治理拦截链
                             ▼
 ┌────────────────────────────────────────────────────────┐
 │ McpInterceptorChain (core)                             │
 │                                                        │
 │ ┌────────────────────────────────────────────────────┐ │
 │ │ Interceptor 1: 权限控制 (McpAccessDeniedException)  │ │
 │ └─────────────────────────┬──────────────────────────┘ │
 │                           ▼                            │
 │ ┌────────────────────────────────────────────────────┐ │
 │ │ Interceptor 2: 动态敏感词/凭据脱敏 (Masking)         │ │
 │ └─────────────────────────┬──────────────────────────┘ │
 │                           ▼                            │
 │ ┌────────────────────────────────────────────────────┐ │
 │ │ Interceptor 3: 结构化调用审计 (LoggingInterceptor)   │ │
 │ └─────────────────────────┬──────────────────────────┘ │
 └───────────────────────────┼────────────────────────────┘
                             │ (3) 校验通过，执行真正业务 Tool
                             ▼
 ┌────────────────────────────────────────────────────────┐
 │ 目标 Tool 实际执行 (Target Tool Execution)              │
 └───────────────────────────┬────────────────────────────┘
                             │ (4) 产出结果 / 捕获异常
                             ▼
 ┌────────────────────────────────────────────────────────┐
 │ McpInterceptorChain (执行 postHandle / onError 流程)    │
 └───────────────────────────┬────────────────────────────┘
                             │ (5) 封装为脱敏安全的 McpInvocationResult
                             ▼
 ┌────────────────────────────────────────────────────────┐
 │ 运行时适配层转换为底层响应 Wire 协议返回                    │
 └────────────────────────────────────────────────────────┘
```

---

## 2. 领域对象、接口契约与包结构划分

### 2.1 `mcp-governance-core` (包路径: `io.github.chukui2005.mcpgov.core.runtime`)

1. **`McpInvocationContext.java`**:
   ```java
   package io.github.chukui2005.mcpgov.core.runtime;

   import java.time.Instant;
   import java.util.Collections;
   import java.util.HashMap;
   import java.util.Map;
   import java.util.Objects;

   /**
    * 运行时 MCP 工具调用上下文（不可变领域模型，防篡改）。
    */
   public record McpInvocationContext(
       String toolName,
       Map<String, Object> arguments,
       Map<String, Object> attributes,
       Instant timestamp
   ) {
       public McpInvocationContext {
           Objects.requireNonNull(toolName, "toolName 不能为 null");
           arguments = arguments == null ? Map.of() : Collections.unmodifiableMap(new HashMap<>(arguments));
           attributes = attributes == null ? Map.of() : Collections.unmodifiableMap(new HashMap<>(attributes));
           timestamp = timestamp == null ? Instant.now() : timestamp;
       }

       public McpInvocationContext withArguments(Map<String, Object> newArgs) {
           return new McpInvocationContext(this.toolName, newArgs, this.attributes, this.timestamp);
       }

       public McpInvocationContext withAttribute(String key, Object value) {
           Map<String, Object> newAttr = new HashMap<>(this.attributes);
           newAttr.put(key, value);
           return new McpInvocationContext(this.toolName, this.arguments, newAttr, this.timestamp);
       }
   }
   ```

2. **`McpInvocationResult.java`**:
   ```java
   package io.github.chukui2005.mcpgov.core.runtime;

   /**
    * 运行时工具调用结果模型。
    */
   public record McpInvocationResult(
       boolean success,
       Object result,
       String errorMessage,
       long durationMs
   ) {
       public static McpInvocationResult success(Object result, long durationMs) {
           return new McpInvocationResult(true, result, null, durationMs);
       }

       public static McpInvocationResult failure(String errorMessage, long durationMs) {
           return new McpInvocationResult(false, null, errorMessage, durationMs);
       }
   }
   ```

3. **`McpInterceptor.java`**:
   ```java
   package io.github.chukui2005.mcpgov.core.runtime;

   /**
    * MCP 运行时拦截器核心接口。
    */
   public interface McpInterceptor {
       /**
        * 拦截器排序号，越小越优先执行。
        */
       default int getOrder() {
           return 0;
       }

       /**
        * 前置拦截处理。
        * @throws McpGovernanceException 若鉴权失败或存在高危违规，可抛出异常阻断执行
        */
       default McpInvocationContext preHandle(McpInvocationContext context) {
           return context;
       }

       /**
        * 后置拦截处理。
        */
       default McpInvocationResult postHandle(McpInvocationContext context, McpInvocationResult result) {
           return result;
       }

       /**
        * 异常处理钩子。
        */
       default void onError(McpInvocationContext context, Throwable throwable) {
       }
   }
   ```

4. **`McpInterceptorChain.java`**:
   ```java
   package io.github.chukui2005.mcpgov.core.runtime;

   import java.util.Collections;
   import java.util.Comparator;
   import java.util.List;

   /**
    * 运行时拦截器链执行器。
    */
   public class McpInterceptorChain {
       private final List<McpInterceptor> interceptors;

       public McpInterceptorChain(List<McpInterceptor> interceptors) {
           if (interceptors == null || interceptors.isEmpty()) {
               this.interceptors = Collections.emptyList();
           } else {
               this.interceptors = interceptors.stream()
                   .sorted(Comparator.comparingInt(McpInterceptor::getOrder))
                   .toList();
           }
       }

       public McpInvocationContext applyPreHandle(McpInvocationContext context) {
           McpInvocationContext current = context;
           for (McpInterceptor interceptor : interceptors) {
               current = interceptor.preHandle(current);
           }
           return current;
       }

       public McpInvocationResult applyPostHandle(McpInvocationContext context, McpInvocationResult result) {
           McpInvocationResult current = result;
           for (int i = interceptors.size() - 1; i >= 0; i--) {
               current = interceptors.get(i).postHandle(context, current);
           }
           return current;
       }

       public void applyOnError(McpInvocationContext context, Throwable ex) {
           for (McpInterceptor interceptor : interceptors) {
               try {
                   interceptor.onError(context, ex);
               } catch (Exception ignored) {
               }
           }
       }

       public List<McpInterceptor> getInterceptors() {
           return interceptors;
       }
   }
   ```

5. **`McpGovernanceException.java`** 与 **`McpAccessDeniedException.java`**:
   - 继承自标准 `RuntimeException`，表达治理拦截阻断异常。

### 2.2 `mcp-governance-spring-boot-starter` (包路径: `io.github.chukui2005.mcpgov.spring.boot`)

1. **`McpGovernanceProperties.java`**:
   - 带有 `@ConfigurationProperties(prefix = "mcp.governance")`；
   - 包含 `enabled` (boolean, default true)、`failFast` (boolean, default true)；
   - 嵌套 `AuditProperties`：`enabled` (boolean, default true)、`logLevel` (default "INFO")、`maskSensitiveArguments` (default true)。

2. **`LoggingMcpInterceptor.java`**:
   - 实现 `McpInterceptor`；
   - 排序号设定为较大值（如 `1000`），作为外层审计日志器；
   - 根据配置执行敏感字段脱敏打印（如对名称包含 password/secret/token/key 的入参替换为 `******`）。

3. **`McpGovernanceAutoConfiguration.java`**:
   - 标注 `@AutoConfiguration`；
   - 条件注解配置：`@ConditionalOnProperty(prefix = "mcp.governance", name = "enabled", havingValue = "true", matchIfMissing = true)`；
   - 通过 Spring `ObjectProvider<List<McpInterceptor>>` 收集 Bean 并按 Order 构造 `McpInterceptorChain`；
   - 配置 `@ConditionalOnMissingBean`。

4. **资源声明文件**:
   - `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 填入全类名：
     `io.github.chukui2005.mcpgov.spring.boot.autoconfigure.McpGovernanceAutoConfiguration`

---

## 3. 实施细节与文件清单

### 3.1 研发工程师工作清单
1. **修改根 `pom.xml`**:
   - 在 `<modules>` 中添加 `<module>mcp-governance-spring-boot-starter</module>`；
   - 在 `<dependencyManagement>` 声明 Starter 模块与 Spring Boot 3.3.4 依赖基线。
2. **在 `mcp-governance-core` 中增加运行时契约**:
   - `McpInvocationContext.java`
   - `McpInvocationResult.java`
   - `McpInterceptor.java`
   - `McpInterceptorChain.java`
   - `McpGovernanceException.java` 与 `McpAccessDeniedException.java`
   - 为上述类提供单元测试 `McpInterceptorChainTest.java`。
3. **新建 `mcp-governance-spring-boot-starter` 模块**:
   - 创建 `pom.xml`（遵循 Maven 规范，依赖 core、spring-boot-autoconfigure、spring-boot-configuration-processor）；
   - 实现 `McpGovernanceProperties.java`；
   - 实现 `LoggingMcpInterceptor.java`；
   - 实现 `McpGovernanceAutoConfiguration.java`；
   - 创建 `META-INF/spring/...AutoConfiguration.imports`。
4. **单测验证**:
   - 使用 `ApplicationContextRunner` 验证：
     a) 默认配置下，自动装配正常加载 `McpInterceptorChain` 与 `LoggingMcpInterceptor`；
     b) 当 `mcp.governance.enabled=false` 时，没有任何治理 Bean 被注入；
     c) 当用户自定义了 `McpInterceptorChain` 时，`@ConditionalOnMissingBean` 生效，不注入默认链。

---

## 4. 依赖引入与 POM 变更设计

### 根 `pom.xml` 增加内容：
```xml
<properties>
    <spring-boot.version>3.3.4</spring-boot.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>${spring-boot.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>io.github.chukui2005</groupId>
            <artifactId>mcp-governance-spring-boot-starter</artifactId>
            <version>${project.version}</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### `mcp-governance-spring-boot-starter/pom.xml` 核心配置：
```xml
<dependencies>
    <dependency>
        <groupId>io.github.chukui2005</groupId>
        <artifactId>mcp-governance-core</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-autoconfigure</artifactId>
        <optional>true</optional>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-configuration-processor</artifactId>
        <optional>true</optional>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

---

## 5. 测试策略与验收用例设计

1. **核心责任链单测 (`McpInterceptorChainTest`)**:
   - 验证拦截器按照 `getOrder()` 升序执行 `preHandle`；
   - 验证后置拦截 `postHandle` 逆序执行；
   - 验证某一拦截器抛出 `McpAccessDeniedException` 时，后续拦截器不再触发；
   - 验证入参 `arguments` 视图不可变，防止被篡改。
2. **Starter 自动装配单测 (`McpGovernanceAutoConfigurationTest`)**:
   - `testDefaultConfiguration()`: 验证装配了默认拦截链与日志拦截器；
   - `testDisabledConfiguration()`: 设置 `mcp.governance.enabled=false`，断言容器中不含相关 Bean；
   - `testCustomInterceptor()`: 注册自定义 `McpInterceptor`，验证被自动纳入拦截链。
