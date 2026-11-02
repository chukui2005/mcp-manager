package io.github.chukui2005.mcpgovernance.gateway.upstream.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.chukui2005.mcpgov.core.model.GovernedTool;
import io.github.chukui2005.mcpgov.core.runtime.McpGovernanceException;
import io.github.chukui2005.mcpgov.core.runtime.McpInvocationResult;
import io.github.chukui2005.mcpgovernance.gateway.config.RouteDefinition;
import io.github.chukui2005.mcpgovernance.gateway.model.JsonRpcRequest;
import io.github.chukui2005.mcpgovernance.gateway.model.JsonRpcResponse;
import io.github.chukui2005.mcpgovernance.gateway.upstream.UpstreamClient;
import io.github.chukui2005.mcpgovernance.gateway.upstream.auth.TokenRefResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于 Spring WebFlux {@link WebClient} 实现的 HTTP (Streamable HTTP / JSON-RPC POST) 出站客户端。
 * 遵循 MCP 规范发送 JSON-RPC 2.0 请求，支持 tools/list 工具拉取、tools/call 远程调用以及超时控制。
 * 严格遵循《AGENTS.md》安全基线：根据 tokenRef 解析注入凭据，日志中严禁暴露 Secret。
 *
 * @author chukui2005
 * @since 0.1.0
 */
public class HttpUpstreamClient implements UpstreamClient {

    private static final Logger log = LoggerFactory.getLogger(HttpUpstreamClient.class);

    private final String routeId;
    private final String baseUri;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final Duration requestTimeout;
    private final AtomicLong requestIdSequence = new AtomicLong(1);
    private volatile boolean closed = false;

    /**
     * 构造基于 HTTP 的 MCP 出站客户端。
     *
     * @param route            路由规则定义
     * @param tokenRefResolver 安全凭据解析器
     * @param webClientBuilder Spring WebClient.Builder
     * @param objectMapper     JSON 映射器
     * @param defaultTimeoutMs 默认请求超时时间（毫秒）
     */
    public HttpUpstreamClient(RouteDefinition route,
                              TokenRefResolver tokenRefResolver,
                              WebClient.Builder webClientBuilder,
                              ObjectMapper objectMapper,
                              int defaultTimeoutMs) {
        if (route == null) {
            throw new IllegalArgumentException("route 不能为空");
        }
        this.routeId = route.getId();
        this.baseUri = route.getUri() != null ? route.getUri().trim() : "";
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();

        int timeout = defaultTimeoutMs > 0 ? defaultTimeoutMs : 30000;
        this.requestTimeout = Duration.ofMillis(timeout);

        WebClient.Builder builder = (webClientBuilder != null ? webClientBuilder.clone() : WebClient.builder())
                .baseUrl(this.baseUri)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        // 注入下游专属凭证 Header
        if (tokenRefResolver != null && route.getAuth() != null) {
            String headerName = route.getAuth().getHeaderName() != null && !route.getAuth().getHeaderName().trim().isEmpty()
                    ? route.getAuth().getHeaderName().trim()
                    : HttpHeaders.AUTHORIZATION;
            String headerValue = tokenRefResolver.buildHeaderValue(route.getAuth());
            if (headerValue != null && !headerValue.isEmpty()) {
                builder.defaultHeader(headerName, headerValue);
                log.debug("成功为下游路由 [{}] 注入认证头 [{}]", this.routeId, headerName);
            }
        }

        this.webClient = builder.build();
    }

    @Override
    public String getRouteId() {
        return routeId;
    }

    @Override
    public Mono<List<GovernedTool>> listTools() {
        if (closed) {
            return Mono.error(new IllegalStateException("HttpUpstreamClient 已关闭: " + routeId));
        }

        JsonRpcRequest rpcRequest = new JsonRpcRequest(
                requestIdSequence.getAndIncrement(),
                "tools/list",
                Collections.emptyMap()
        );

        return sendRpc(rpcRequest)
                .timeout(requestTimeout)
                .map(response -> {
                    if (response.getError() != null) {
                        throw new McpGovernanceException("下游返回 tools/list 错误: " + response.getError().getMessage());
                    }
                    return parseToolsFromResult(response.getResult());
                })
                .doOnError(e -> log.error("下游路由 [{}] 执行 tools/list 异常: {}", routeId, e.getMessage()));
    }

    @Override
    public Mono<McpInvocationResult> callTool(String toolName, Map<String, Object> arguments) {
        if (closed) {
            return Mono.error(new IllegalStateException("HttpUpstreamClient 已关闭: " + routeId));
        }

        long startTime = System.currentTimeMillis();
        Map<String, Object> params = new HashMap<>();
        params.put("name", toolName);
        params.put("arguments", arguments != null ? arguments : Collections.emptyMap());

        JsonRpcRequest rpcRequest = new JsonRpcRequest(
                requestIdSequence.getAndIncrement(),
                "tools/call",
                params
        );

        return sendRpc(rpcRequest)
                .timeout(requestTimeout)
                .map(response -> {
                    long durationMs = System.currentTimeMillis() - startTime;
                    if (response.getError() != null) {
                        return McpInvocationResult.failure(
                                "下游工具执行错误 [" + response.getError().getCode() + "]: " + response.getError().getMessage(),
                                durationMs
                        );
                    }
                    return McpInvocationResult.success(response.getResult(), durationMs);
                })
                .onErrorResume(e -> {
                    long durationMs = System.currentTimeMillis() - startTime;
                    log.error("下游路由 [{}] 调用工具 [{}] 失败: {}", routeId, toolName, e.getMessage());
                    return Mono.just(McpInvocationResult.failure("下游调用失败: " + e.getMessage(), durationMs));
                });
    }

    @Override
    public Mono<Boolean> ping() {
        if (closed) {
            return Mono.just(false);
        }

        JsonRpcRequest rpcRequest = new JsonRpcRequest(
                requestIdSequence.getAndIncrement(),
                "ping",
                Collections.emptyMap()
        );

        return sendRpc(rpcRequest)
                .timeout(Duration.ofSeconds(3))
                .map(response -> response.getError() == null)
                .onErrorReturn(false);
    }

    @Override
    public void close() {
        this.closed = true;
        log.info("关闭下游路由 [{}] 的 HttpUpstreamClient", routeId);
    }

    /**
     * 发送底层 JSON-RPC POST 请求并反序列化响应。
     *
     * @param request JSON-RPC 请求
     * @return 响应式的 JsonRpcResponse
     */
    protected Mono<JsonRpcResponse> sendRpc(JsonRpcRequest request) {
        return webClient.post()
                .bodyValue(request)
                .retrieve()
                .bodyToMono(JsonRpcResponse.class);
    }

    /**
     * 从 tools/list 响应的 result 中解析 GovernedTool 列表。
     *
     * @param resultObj JSON-RPC result 对象
     * @return 解析出的 GovernedTool 列表
     */
    @SuppressWarnings("unchecked")
    private List<GovernedTool> parseToolsFromResult(Object resultObj) {
        if (resultObj == null) {
            return Collections.emptyList();
        }

        Map<String, Object> resultMap;
        if (resultObj instanceof Map) {
            resultMap = (Map<String, Object>) resultObj;
        } else {
            try {
                resultMap = objectMapper.convertValue(resultObj, new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                log.warn("无法将 tools/list 响应结果转换为 Map: {}", e.getMessage());
                return Collections.emptyList();
            }
        }

        Object toolsListObj = resultMap.get("tools");
        if (!(toolsListObj instanceof List)) {
            return Collections.emptyList();
        }

        List<?> rawList = (List<?>) toolsListObj;
        List<GovernedTool> governedTools = new ArrayList<>();

        for (Object item : rawList) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<String, Object> toolMap = (Map<String, Object>) item;
            String name = (String) toolMap.get("name");
            if (name == null || name.trim().isEmpty()) {
                continue;
            }
            String description = (String) toolMap.getOrDefault("description", "");
            Object inputSchemaObj = toolMap.get("inputSchema");
            Map<String, Object> inputSchema = Collections.emptyMap();
            if (inputSchemaObj instanceof Map) {
                inputSchema = (Map<String, Object>) inputSchemaObj;
            }

            governedTools.add(GovernedTool.of(name, description, inputSchema));
        }

        return governedTools;
    }
}
