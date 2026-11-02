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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于 Spring WebFlux {@link WebClient} 实现的 Server-Sent Events (SSE) 长连接 MCP 出站客户端。
 * 遵循 MCP 规范，客户端建立 SSE 长连接以接收服务端推送事件与 endpoint 初始化通知，并通过 POST 端点发送 JSON-RPC 交互。
 * 当尚未收到下游推导的动态端点时，默认回退至基础 URI 进行 POST 发送。
 *
 * @author chukui2005
 * @since 0.1.0
 */
public class SseUpstreamClient implements UpstreamClient {

    private static final Logger log = LoggerFactory.getLogger(SseUpstreamClient.class);

    private final String routeId;
    private final String sseUri;
    private final WebClient sseWebClient;
    private final WebClient postWebClient;
    private final ObjectMapper objectMapper;
    private final Duration requestTimeout;
    private final AtomicLong requestIdSequence = new AtomicLong(1);

    /**
     * 等待响应的 Pending 字典：requestId -> Sinks.One
     */
    private final Map<String, Sinks.One<JsonRpcResponse>> pendingRequests = new ConcurrentHashMap<>();

    private volatile String messagePostUri;
    private volatile Disposable sseSubscription;
    private volatile boolean closed = false;

    /**
     * 构造基于 SSE 长连接的 MCP 出站客户端。
     *
     * @param route            路由规则定义
     * @param tokenRefResolver 安全凭据解析器
     * @param webClientBuilder Spring WebClient.Builder
     * @param objectMapper     JSON 映射器
     * @param defaultTimeoutMs 默认请求超时时间（毫秒）
     */
    public SseUpstreamClient(RouteDefinition route,
                             TokenRefResolver tokenRefResolver,
                             WebClient.Builder webClientBuilder,
                             ObjectMapper objectMapper,
                             int defaultTimeoutMs) {
        if (route == null) {
            throw new IllegalArgumentException("route 不能为空");
        }
        this.routeId = route.getId();
        this.sseUri = route.getUri() != null ? route.getUri().trim() : "";
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();

        int timeout = defaultTimeoutMs > 0 ? defaultTimeoutMs : 30000;
        this.requestTimeout = Duration.ofMillis(timeout);

        WebClient.Builder baseBuilder = webClientBuilder != null ? webClientBuilder.clone() : WebClient.builder();

        // 注入安全凭据
        if (tokenRefResolver != null && route.getAuth() != null) {
            String headerName = route.getAuth().getHeaderName() != null && !route.getAuth().getHeaderName().trim().isEmpty()
                    ? route.getAuth().getHeaderName().trim()
                    : HttpHeaders.AUTHORIZATION;
            String headerValue = tokenRefResolver.buildHeaderValue(route.getAuth());
            if (headerValue != null && !headerValue.isEmpty()) {
                baseBuilder.defaultHeader(headerName, headerValue);
                log.debug("成功为下游 SSE 路由 [{}] 注入认证头 [{}]", this.routeId, headerName);
            }
        }

        // SSE 监听专用客户端（Accept: text/event-stream）
        this.sseWebClient = baseBuilder.clone()
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                .build();

        // POST 交互专用客户端（Accept: application/json, Content-Type: application/json）
        this.postWebClient = baseBuilder.clone()
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();

        // 启动后台 SSE 监听
        connectSse();
    }

    /**
     * 发起 SSE 连接并在流中持续监听事件。
     */
    private synchronized void connectSse() {
        if (closed || sseUri.isEmpty()) {
            return;
        }

        ParameterizedTypeReference<ServerSentEvent<String>> typeRef = new ParameterizedTypeReference<>() {};

        this.sseSubscription = sseWebClient.get()
                .uri(sseUri)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(typeRef)
                .doOnSubscribe(s -> log.info("下游 SSE 客户端 [{}] 正在订阅端点: {}", routeId, sseUri))
                .subscribe(
                        this::handleSseEvent,
                        error -> {
                            if (!closed) {
                                log.warn("下游 SSE 路由 [{}] 连接异常: {}，准备重连...", routeId, error.getMessage());
                                scheduleReconnect();
                            }
                        },
                        () -> {
                            if (!closed) {
                                log.info("下游 SSE 路由 [{}] 连接正常结束，准备重连...", routeId);
                                scheduleReconnect();
                            }
                        }
                );
    }

    /**
     * 处理收到的单条 SSE 事件。
     *
     * @param sseEvent SSE 事件对象
     */
    protected void handleSseEvent(ServerSentEvent<String> sseEvent) {
        if (sseEvent == null) {
            return;
        }

        String eventType = sseEvent.event();
        String data = sseEvent.data();

        log.debug("下游 SSE [{}] 接收到事件: event={}, data={}", routeId, eventType, data);

        // 1. 若收到 endpoint 事件，按规范更新后续 POST 交互地址
        if ("endpoint".equalsIgnoreCase(eventType) && data != null && !data.trim().isEmpty()) {
            this.messagePostUri = resolveRelativeUri(sseUri, data.trim());
            log.info("下游 SSE [{}] 更新 POST 端点为: {}", routeId, this.messagePostUri);
            return;
        }

        // 2. 若收到 message 事件或默认包含 JSON-RPC 响应，分发至 pending 等待队列
        if (data != null && !data.trim().isEmpty()) {
            try {
                JsonRpcResponse response = objectMapper.readValue(data, JsonRpcResponse.class);
                if (response != null && response.getId() != null) {
                    String reqId = String.valueOf(response.getId());
                    Sinks.One<JsonRpcResponse> sink = pendingRequests.remove(reqId);
                    if (sink != null) {
                        sink.tryEmitValue(response);
                    }
                }
            } catch (Exception ignored) {
                // 非 JsonRpcResponse 格式数据，忽略
            }
        }
    }

    @Override
    public String getRouteId() {
        return routeId;
    }

    @Override
    public Mono<List<GovernedTool>> listTools() {
        if (closed) {
            return Mono.error(new IllegalStateException("SseUpstreamClient 已关闭: " + routeId));
        }

        long reqId = requestIdSequence.getAndIncrement();
        JsonRpcRequest rpcRequest = new JsonRpcRequest(
                reqId,
                "tools/list",
                Collections.emptyMap()
        );

        return sendRpcAndAwait(rpcRequest)
                .timeout(requestTimeout)
                .retryWhen(reactor.util.retry.Retry.backoff(2, Duration.ofMillis(300))
                        .filter(ex -> !(ex instanceof McpGovernanceException)))
                .map(response -> {
                    if (response.getError() != null) {
                        throw new McpGovernanceException("下游 SSE 返回 tools/list 错误: " + response.getError().getMessage());
                    }
                    return parseToolsFromResult(response.getResult());
                })
                .doOnError(e -> log.error("下游 SSE 路由 [{}] 执行 tools/list 异常: {}", routeId, e.getMessage()));
    }

    @Override
    public Mono<McpInvocationResult> callTool(String toolName, Map<String, Object> arguments) {
        if (closed) {
            return Mono.error(new IllegalStateException("SseUpstreamClient 已关闭: " + routeId));
        }

        long startTime = System.currentTimeMillis();
        long reqId = requestIdSequence.getAndIncrement();
        Map<String, Object> params = new HashMap<>();
        params.put("name", toolName);
        params.put("arguments", arguments != null ? arguments : Collections.emptyMap());

        JsonRpcRequest rpcRequest = new JsonRpcRequest(
                reqId,
                "tools/call",
                params
        );

        return sendRpcAndAwait(rpcRequest)
                .timeout(requestTimeout)
                .map(response -> {
                    long durationMs = System.currentTimeMillis() - startTime;
                    if (response.getError() != null) {
                        return McpInvocationResult.failure(
                                "下游 SSE 工具调用错误 [" + response.getError().getCode() + "]: " + response.getError().getMessage(),
                                durationMs
                        );
                    }
                    return McpInvocationResult.success(response.getResult(), durationMs);
                })
                .onErrorResume(e -> {
                    long durationMs = System.currentTimeMillis() - startTime;
                    log.error("下游 SSE 路由 [{}] 调用工具 [{}] 失败: {}", routeId, toolName, e.getMessage());
                    return Mono.just(McpInvocationResult.failure("下游 SSE 调用失败: " + e.getMessage(), durationMs));
                });
    }

    @Override
    public Mono<Boolean> ping() {
        if (closed) {
            return Mono.just(false);
        }

        long reqId = requestIdSequence.getAndIncrement();
        JsonRpcRequest rpcRequest = new JsonRpcRequest(
                reqId,
                "ping",
                Collections.emptyMap()
        );

        return sendRpcAndAwait(rpcRequest)
                .timeout(Duration.ofSeconds(3))
                .retryWhen(reactor.util.retry.Retry.backoff(2, Duration.ofMillis(300)))
                .map(response -> response.getError() == null)
                .onErrorReturn(false);
    }

    @Override
    public synchronized void close() {
        this.closed = true;
        if (sseSubscription != null && !sseSubscription.isDisposed()) {
            sseSubscription.dispose();
        }
        for (Sinks.One<JsonRpcResponse> sink : pendingRequests.values()) {
            sink.tryEmitError(new IllegalStateException("SSE 客户端连接已关闭"));
        }
        pendingRequests.clear();
        log.info("关闭下游路由 [{}] 的 SseUpstreamClient", routeId);
    }

    /**
     * 发送 JSON-RPC POST 请求并等待返回（优先从 SSE 接收推送响应，若 POST 自身直接回包亦兼容支持）。
     *
     * @param request JSON-RPC 请求
     * @return 响应式的 JsonRpcResponse
     */
    protected Mono<JsonRpcResponse> sendRpcAndAwait(JsonRpcRequest request) {
        String reqId = String.valueOf(request.getId());
        Sinks.One<JsonRpcResponse> sink = Sinks.one();
        pendingRequests.put(reqId, sink);

        String targetUri = this.messagePostUri != null && !this.messagePostUri.isEmpty()
                ? this.messagePostUri
                : this.sseUri;

        // 发送 POST 请求至 targetUri
        postWebClient.post()
                .uri(targetUri)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(JsonRpcResponse.class)
                .subscribe(
                        directResponse -> {
                            // 如果 HTTP POST 直接响应了 JSON-RPC 结果，直接触发完成
                            if (directResponse != null) {
                                Sinks.One<JsonRpcResponse> pending = pendingRequests.remove(reqId);
                                if (pending != null) {
                                    pending.tryEmitValue(directResponse);
                                }
                            }
                        },
                        error -> {
                            // HTTP 发送异常时标记失败
                            Sinks.One<JsonRpcResponse> pending = pendingRequests.remove(reqId);
                            if (pending != null) {
                                pending.tryEmitError(error);
                            }
                        }
                );

        // 核心加固：挂载 doFinally，无论完成、超时错误或下游取消，均确保移除
        return sink.asMono()
                .doFinally(signalType -> pendingRequests.remove(reqId));
    }

    /**
     * 调度断线指数退避重连。
     */
    private void scheduleReconnect() {
        if (closed) {
            return;
        }
        Mono.delay(Duration.ofSeconds(2))
                .subscribe(ignore -> {
                    if (!closed) {
                        connectSse();
                    }
                });
    }

    /**
     * 工具方法：根据基准 SSE URI 解析相对端点。
     *
     * @param baseUri  基准 URI
     * @param endpoint 目标 endpoint（可能是相对路径或绝对路径）
     * @return 解析后的绝对 URI
     */
    private String resolveRelativeUri(String baseUri, String endpoint) {
        try {
            URI base = URI.create(baseUri);
            return base.resolve(endpoint).toString();
        } catch (Exception e) {
            log.warn("无法解析 SSE 相对端点: base={}, endpoint={}", baseUri, endpoint);
            return endpoint;
        }
    }

    /**
     * 解析 tools/list 返回的工具清单。
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
