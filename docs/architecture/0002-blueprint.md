# 架构技术蓝图：MCP 配置文件解析与 OWASP MCP02/MCP03 规则集实现 (TASK-002)

## 1. 架构目标与设计原则

本技术蓝图指导工程师完成 TASK-002 的具体工程实现，核心原则如下：
1. **纯粹性与零 Spring 依赖**：`mcp-governance-core` 保持纯 Java 17 领域模型，只依赖基础 Jackson 注解与 Java 标准库。
2. **控制面与传输面隔离**：配置解析后严格收敛为不可变 Record 实体（`McpConfig`, `McpServerConfig`, `GovernedTool`）。
3. **安全默认（零代码执行）**：静态审计不拉起外部进程，防范 MCP05 命令执行。
4. **高质量与文档门禁**：所有新增类、字段与公开方法提供严谨 Javadoc，`mvn javadoc:javadoc` 零警告，注释与文档全中文。

---

## 2. 模块职责与变更清单

### 2.1 主要新增/修改文件清单 (Primary Scope)

| 模块 | 文件路径 | 变更类型 | 核心职责说明 |
| :--- | :--- | :--- | :--- |
| `core` | `io.github.chukui2005.mcpgov.core.model.McpServerConfig` | 新增 | MCP 单个服务静态配置实体 (Record)，包含 command, args, env, declaredTools |
| `core` | `io.github.chukui2005.mcpgov.core.model.McpConfig` | 新增 | MCP 配置集合根实体 (Record)，包含 servers 映射与跨服务检索工具函数 |
| `core` | `io.github.chukui2005.mcpgov.core.spi.ScanContext` | 修改 | 新增 `mcpConfig` 字段与工厂方法，向下兼容旧构造器 |
| `scanner` | `io.github.chukui2005.mcpgov.scanner.parser.McpConfigParser` | 新增 | 解析 `mcp.json` / Claude Desktop JSON 格式，提取 Server 与内嵌 Tool |
| `rules-owasp` | `io.github.chukui2005.mcpgov.rules.owasp.mcp02.BroadScopeScanRule` | 新增 | `MCP02-001`：宽泛权限通配符与高危系统操作检测 |
| `rules-owasp` | `io.github.chukui2005.mcpgov.rules.owasp.mcp03.PromptInjectionScanRule` | 新增 | `MCP03-001`：工具描述注入指令检测（Ignore instructions / System 冒充） |
| `rules-owasp` | `io.github.chukui2005.mcpgov.rules.owasp.mcp03.ToolShadowingScanRule` | 新增 | `MCP03-002`：跨 Server 同名工具劫持检测 |
| `cli` | `io.github.chukui2005.mcpgov.cli.render.ConsoleReportRenderer` | 新增 | 控制台 ANSI 彩色 Markdown 报告渲染器 |
| `cli` | `io.github.chukui2005.mcpgov.cli.McpScanCommand` | 修改 | 集成解析器、装配新规则集并接入控制台报告渲染 |

### 2.2 次要修改与测试文件 (Secondary Scope & Tests)

| 模块 | 测试文件路径 | 核心验证内容 |
| :--- | :--- | :--- |
| `core` | `McpConfigTest` | 验证 `McpConfig` 不可变性、跨服务聚合工具及按名称索引 |
| `scanner` | `McpConfigParserTest` | 覆盖标准 `mcp.json`、Claude Desktop JSON 及内嵌 tools 声明解析 |
| `rules-owasp` | `BroadScopeScanRuleTest` | 验证通配符 `*`、未受限 schema 与高危系统操作的命中与放行边界 |
| `rules-owasp` | `PromptInjectionScanRuleTest` | 验证常见 Prompt 注入关键字（大小写变体、标签变体）检测 |
| `rules-owasp` | `ToolShadowingScanRuleTest` | 验证单服务正常、多服务同名碰撞冲突告警 |
| `cli` | `ConsoleReportRendererTest` | 验证 ANSI 格式化字符串拼接正确性 |
| `cli` | `McpScanCommandTest` | 验证多规则集成扫描及退出码逻辑 |

---

## 3. 详细设计规范

### 3.1 领域模型契约 (`mcp-governance-core`)

#### `McpServerConfig.java`
```java
package io.github.chukui2005.mcpgov.core.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 单个 MCP Server 静态配置实体。
 *
 * @param id 服务唯一标识名称 (非空，如 "filesystem", "github")
 * @param command 启动命令 (如 "npx", "docker")
 * @param args 参数列表 (只读不可变)
 * @param env 环境变量表 (只读不可变)
 * @param declaredTools 静态声明的受控工具列表 (只读不可变)
 */
public record McpServerConfig(
        String id,
        String command,
        List<String> args,
        Map<String, String> env,
        List<GovernedTool> declaredTools
) {
    public McpServerConfig {
        Objects.requireNonNull(id, "服务标识 (id) 不能为空");
        command = command != null ? command : "";
        args = args != null ? List.copyOf(args) : Collections.emptyList();
        env = env != null ? Map.copyOf(env) : Collections.emptyMap();
        declaredTools = declaredTools != null ? List.copyOf(declaredTools) : Collections.emptyList();
    }
}
```

#### `McpConfig.java`
```java
package io.github.chukui2005.mcpgov.core.model;

import java.util.*;

/**
 * MCP 配置文件整体抽象模型。
 *
 * @param servers 服务配置映射表 (Key: serverId, Value: McpServerConfig)
 */
public record McpConfig(
        Map<String, McpServerConfig> servers
) {
    public McpConfig {
        servers = servers != null ? Map.copyOf(servers) : Collections.emptyMap();
    }

    /**
     * 汇聚获取当前配置内所有 Server 声明的受控工具。
     *
     * @return 工具列表
     */
    public List<GovernedTool> getAllDeclaredTools() {
        List<GovernedTool> list = new ArrayList<>();
        for (McpServerConfig server : servers.values()) {
            list.addAll(server.declaredTools());
        }
        return Collections.unmodifiableList(list);
    }
}
```

### 3.2 静态配置解析算法 (`McpConfigParser.java`)

1. **结构识别与容错**：
   - 支持解析标准 JSON 文本；
   - 优先检查根节点 `mcpServers`（标准 Claude Desktop 与标准 MCP 规范）；若根节点无 `mcpServers`，检查是否直接平铺了 Server 映射或顶层字段。
2. **Server 提取**：
   - 针对每个 Server 节点，提取 `command` (字符串)、`args` (字符串数组)、`env` (键值对象)；
   - 提取 `tools` 节点：若配置中内嵌定义了工具数组，遍历每个 Tool 节点，读取 `name`、`description` 与 `inputSchema`，构造为 `GovernedTool`。

### 3.3 OWASP 规则检测算法

#### 1. `MCP02-001` (权限范围蔓延与过度授权)
- **匹配维度 1：通配符滥用**
  - 递归遍历 `inputSchema` 的 `properties`：若参数默认值等于 `"*"` 或 `"**"`，或模式 `pattern` 为 `.*`，标记 `MCP02-001`。
- **匹配维度 2：高危敏感操作与无界输入**
  - 工具名称包含 `exec`, `eval`, `shell`, `command`, `delete`, `drop` 等破坏性动词；
  - 同时其 `inputSchema` 中不存在任何枚举范围（`enum`）或必填限定参数（`required` 为空或无校验）。

#### 2. `MCP03-001` (提示注入防御)
- **正则表达式库**：
  - `(?i)(ignore\s+(all\s+)?previous\s+instructions?|disregard\s+previous|forget\s+all\s+previous)`
  - `(?i)(<\s*system\s*>|<\s*/system\s*>|\[\s*system\s*\]|system\s*prompt\s*:)`
  - `(?i)(send\s+.*(secret|token|password|credential|key).*to)`
- **检查对象**：`GovernedTool.description()` 以及 `inputSchema.properties.*.description`。

#### 3. `MCP03-002` (工具影子与混淆劫持)
- 遍历 `McpConfig.servers()`，统计每个 Tool Name 出现的 Server 归属：
  - 构建映射 `Map<String, List<String>> toolToServerMap`；
  - 当某个工具名称对应的 Server 数量大于 1 时，生成 `MCP03-002` 发现项，列举所有冲突的 Server 名称。

### 3.4 控制台 ANSI Markdown 渲染

- 遵循 CLI 终端规范，通过 ANSI 转义码高亮：
  - CRITICAL: `\u001B[1;31m` (加粗红)
  - HIGH: `\u001B[33m` (黄)
  - MEDIUM: `\u001B[34m` (蓝)
  - LOW: `\u001B[90m` (灰)
- 输出结构清晰的 Markdown 概览表与详细卡片，提升终端审计可读性。

---

## 4. 实施顺序 (Implementation Steps for Engineer)

为确保有序递进与测试驱动开发 (TDD)，工程师需按以下步骤实施：

1. **第一步（领域层基石）**：在 `mcp-governance-core` 中新增 `McpServerConfig`、`McpConfig`，扩展 `ScanContext` 并编写对应的单元测试。
2. **第二步（解析引擎）**：在 `mcp-governance-scanner` 中实现 `McpConfigParser`，并编写解析标准配置及内嵌 Tool 的单元测试。
3. **第三步（规则集实现）**：在 `mcp-governance-rules-owasp` 中实现 `BroadScopeScanRule` (`MCP02-001`)、`PromptInjectionScanRule` (`MCP03-001`)、`ToolShadowingScanRule` (`MCP03-002`)，并完成覆盖性单测。
4. **第四步（表现层呈现）**：在 `mcp-governance-cli` 中实现 `ConsoleReportRenderer`，重构 `McpScanCommand` 集成配置解析与新规则。
5. **第五步（全量验证）**：运行 `mvn clean verify` 与 `mvn javadoc:javadoc`，确保 100% 测试通过且 Javadoc 零警告。
