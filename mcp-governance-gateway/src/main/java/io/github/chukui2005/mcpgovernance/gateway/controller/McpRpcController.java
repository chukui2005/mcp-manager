package io.github.chukui2005.mcpgovernance.gateway.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.chukui2005.mcpgov.core.runtime.McpAccessDeniedException;
import io.github.chukui2005.mcpgov.core.runtime.McpGovernanceException;
import io.github.chukui2005.mcpgov.core.runtime.McpInvocationContext;
import io.github.chukui2005.mcpgov.core.runtime.McpInvocationResult;
import io.github.chukui2005.mcpgovernance.gateway.adapter.GatewayExecutionAdapter;
import io.github.chukui2005.mcpgovernance.gateway.aggregation.ToolAggregator;
import io.github.chukui2005.mcpgovernance.gateway.config.GatewayServerProperties;
import io.github.chukui2005.mcpgovernance.gateway.model.JsonRpcError;
import io.github.chukui2005.mcpgovernance.gateway.model.JsonRpcRequest;
import io.github.chukui2005.mcpgovernance.gateway.model.JsonRpcResponse;
import io.github.chukui2005.mcpgovernance.gateway.route.RouteManager;
import io.github.chukui2005.mcpgovernance.gateway.route.RouteMatchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MCP 网关入站响应式 JSON-RPC 控制器。
 * 遵循 MCP 规范，处理 Streamable HTTP (POST /mcp/v1/rpc) 端点调用。
 * 支持 initialize, tools/list, tools/call 及异常错误处理。
 *
 * @author chukui2005
 * @since 0.1.0
 */
@RestController
@RequestMapping("/mcp/v1")
public class McpRpcController {

    private static final Logger log = LoggerFactory.getLogger(McpRpcController.class);

    private final RouteManager routeManager;
    private final GatewayExecutionAdapter executionAdapter;
    private final GatewayServerProperties properties;
    private final ObjectMapper objectMapper;
    private final ToolAggregator toolAggregator;

    /**
     * 构造函数注入依赖。
     *
     * @param routeManager     路由管理器
     * @param executionAdapter 网关执行适配器
     * @param properties       网关配置属性
     * @param objectMapper     JSON 对象映射器
     * @param toolAggregator   工具聚合器
     */
    public McpRpcController(RouteManager routeManager,
                            GatewayExecutionAdapter executionAdapter,
                            GatewayServerProperties properties,
                            ObjectMapper objectMapper,
                            ToolAggregator toolAggregator) {
        this.routeManager = routeManager;
        this.executionAdapter = executionAdapter;
        this.properties = properties;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.toolAggregator = toolAggregator;
    }

    /**
     * 处理 Streamable HTTP JSON-RPC 入站请求。
     *
     * @param request 入站 JSON-RPC 请求
     * @return 响应式封装的 JSON-RPC 响应
     */
    @PostMapping(value = "/rpc", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<JsonRpcResponse> handleRpc(@RequestBody JsonRpcRequest request) {
        if (request == null) {
            return Mono.just(JsonRpcResponse.error(null, JsonRpcError.invalidRequest("请求体不能为空")));
        }

        Object id = request.getId();
        String method = request.getMethod();

        if (method == null || method.trim().isEmpty()) {
            return Mono.just(JsonRpcResponse.error(id, JsonRpcError.invalidRequest("方法名 method 不能为空")));
        }

        log.debug("收到 MCP JSON-RPC 请求: id={}, method={}", id, method);

        switch (method) {
            case "initialize":
                return Mono.just(handleInitialize(id, request.getParams()));
            case "tools/list":
                return handleToolsList(id, request.getParams());
            case "tools/call":
                return handleToolsCall(id, request.getParams());
            default:
                log.warn("未知或未支持的 JSON-RPC 方法: method={}", method);
                return Mono.just(JsonRpcResponse.error(id, JsonRpcError.methodNotFound("不支持的方法: " + method)));
        }
    }

    /**
     * 处理 MCP initialize 初始化协议握手。
     *
     * @param id     请求标识
     * @param params 握手参数
     * @return 握手响应
     */
    private JsonRpcResponse handleInitialize(Object id, Object params) {
        Map<String, Object> result = new HashMap<>();
        result.put("protocolVersion", "2024-11-05");

        Map<String, Object> serverInfo = new HashMap<>();
        serverInfo.put("name", "mcp-governance-gateway");
        serverInfo.put("version", "0.1.0-SNAPSHOT");
        result.put("serverInfo", serverInfo);

        Map<String, Object> capabilities = new HashMap<>();
        Map<String, Object> toolsCapability = new HashMap<>();
        toolsCapability.put("listChanged", true);
        capabilities.put("tools", toolsCapability);
        result.put("capabilities", capabilities);

        Map<String, Object> instructions = new HashMap<>();
        instructions.put("description", "Enterprise Governed MCP Standalone Gateway");
        result.put("instructions", instructions);

        return JsonRpcResponse.success(id, result);
    }

    /**
     * 处理 tools/list 聚合工具发现请求。
     * 调用 ToolAggregator 并发拉取并聚合所有下游路由的工具信息，注入命名空间前缀并执行租户 ACL 过滤。
     *
     * @param id     请求标识
     * @param params 请求参数
     * @return 响应式工具列表响应
     */
    private Mono<JsonRpcResponse> handleToolsList(Object id, Object params) {
        if (toolAggregator == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("tools", Collections.emptyList());
            return Mono.just(JsonRpcResponse.success(id, result));
        }

        return toolAggregator.aggregateTools()
                .map(tools -> {
                    List<Map<String, Object>> formattedTools = toolAggregator.formatMcpTools(tools);
                    Map<String, Object> result = new HashMap<>();
                    result.put("tools", formattedTools);
                    return JsonRpcResponse.success(id, result);
                })
                .onErrorResume(e -> {
                    log.error("聚合 tools/list 失败: {}", e.getMessage(), e);
                    return Mono.just(JsonRpcResponse.error(id, JsonRpcError.internalError("tools/list 聚合失败: " + e.getMessage())));
                });
    }

    /**
     * 处理 tools/call 工具调用分发请求。
     *
     * @param id     请求标识
     * @param params 调用参数
     * @return 响应式调用结果
     */
    private Mono<JsonRpcResponse> handleToolsCall(Object id, Object params) {
        if (!(params instanceof Map)) {
            return Mono.just(JsonRpcResponse.error(id, JsonRpcError.invalidParams("tools/call 参数必须为 JSON 对象")));
        }

        Map<String, Object> paramsMap;
        try {
            paramsMap = objectMapper.convertValue(params, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Mono.just(JsonRpcResponse.error(id, JsonRpcError.invalidParams("参数解析失败: " + e.getMessage())));
        }

        Object nameObj = paramsMap.get("name");
        if (!(nameObj instanceof String)) {
            return Mono.just(JsonRpcResponse.error(id, JsonRpcError.invalidParams("tools/call 缺少必填参数 'name'")));
        }

        String aggregatedToolName = (String) nameObj;
        Optional<RouteMatchResult> matchOpt = routeManager.matchByToolName(aggregatedToolName);
        if (matchOpt.isEmpty()) {
            log.warn("无法解析工具命名空间前缀或未匹配到下游路由: tool={}", aggregatedToolName);
            return Mono.just(JsonRpcResponse.error(id, new JsonRpcError(-32601, "未找到工具对应的下游路由: " + aggregatedToolName)));
        }

        RouteMatchResult matchResult = matchOpt.get();
        Object rawArguments = paramsMap.get("arguments");
        Map<String, Object> arguments = Collections.emptyMap();
        if (rawArguments instanceof Map) {
            arguments = objectMapper.convertValue(rawArguments, new TypeReference<Map<String, Object>>() {});
        }

        McpInvocationContext context;
        try {
            context = executionAdapter.buildContext(matchResult, arguments, "mcp-gateway-agent");
        } catch (Exception e) {
            return Mono.just(JsonRpcResponse.error(id, JsonRpcError.invalidParams("构建调用上下文失败: " + e.getMessage())));
        }

        return executionAdapter.executeReactive(context)
                .map(invocationResult -> {
                    Map<String, Object> callResult = new HashMap<>();
                    List<Map<String, Object>> contentList = new ArrayList<>();
                    Map<String, Object> textContent = new HashMap<>();
                    textContent.put("type", "text");
                    try {
                        textContent.put("text", objectMapper.writeValueAsString(
                                invocationResult.result() != null ? invocationResult.result() : invocationResult.errorMessage()
                        ));
                    } catch (Exception e) {
                        textContent.put("text", String.valueOf(invocationResult.result()));
                    }
                    contentList.add(textContent);

                    callResult.put("content", contentList);
                    callResult.put("isError", !invocationResult.success());

                    return JsonRpcResponse.success(id, callResult);
                })
                .onErrorResume(McpAccessDeniedException.class, e -> {
                    log.warn("MCP 访问控制拒绝: tool={}, reason={}", aggregatedToolName, e.getMessage());
                    return Mono.just(JsonRpcResponse.error(id, JsonRpcError.accessDenied("访问拒绝: " + e.getMessage())));
                })
                .onErrorResume(McpGovernanceException.class, e -> {
                    log.warn("MCP 治理校验失败: tool={}, reason={}", aggregatedToolName, e.getMessage());
                    return Mono.just(JsonRpcResponse.error(id, new JsonRpcError(-32000, "治理校验失败: " + e.getMessage())));
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("执行 MCP 工具调用异常: tool={}", aggregatedToolName, e);
                    return Mono.just(JsonRpcResponse.error(id, JsonRpcError.internalError("执行失败: " + e.getMessage())));
                });
    }
}
