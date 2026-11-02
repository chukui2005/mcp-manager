package io.github.chukui2005.mcpgovernance.gateway.upstream.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.chukui2005.mcpgov.core.model.GovernedTool;
import io.github.chukui2005.mcpgov.core.runtime.McpGovernanceException;
import io.github.chukui2005.mcpgov.core.runtime.McpInvocationResult;
import io.github.chukui2005.mcpgovernance.gateway.config.RouteDefinition;
import io.github.chukui2005.mcpgovernance.gateway.config.StdioCommandConfig;
import io.github.chukui2005.mcpgovernance.gateway.model.JsonRpcError;
import io.github.chukui2005.mcpgovernance.gateway.model.JsonRpcRequest;
import io.github.chukui2005.mcpgovernance.gateway.model.JsonRpcResponse;
import io.github.chukui2005.mcpgovernance.gateway.upstream.UpstreamClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于受控本地子进程标准输入输出（stdin / stdout）的 MCP STDIO 出站客户端。
 * 遵循《AGENTS.md》安全基线与 ADR 0008：
 * 1. 严禁使用任何形式的 shell 字符串拼接执行，仅通过结构化 List&lt;String&gt; 启动 ProcessBuilder；
 * 2. 严格过滤环境变量中的敏感凭证（如 TOKEN, SECRET, PASSWORD, KEY 等），仅继承受信基础变量；
 * 3. stdin 管道受锁保护按行刷新，stdout 由独立后台线程异步解析 JSON-RPC 2.0 并唤醒等待 Sink；
 * 4. stderr 管道独立后台消费并记录日志，避免操作系统管道缓冲区满引发死锁；
 * 5. 支持优雅停机与进程销毁保护（先 SIGTERM，超时强制 SIGKILL）。
 *
 * @author chukui2005
 * @since 0.1.0
 */
public class StdioUpstreamClient implements UpstreamClient {

    private static final Logger log = LoggerFactory.getLogger(StdioUpstreamClient.class);

    private static final Set<String> SENSITIVE_KEYWORD_SET = Set.of(
            "KEY", "TOKEN", "SECRET", "PASSWORD", "PASSWD", "CREDENTIAL", "AUTH"
    );

    private final String routeId;
    private final StdioCommandConfig commandConfig;
    private final ObjectMapper objectMapper;
    private final Duration requestTimeout;
    private final AtomicLong requestIdSequence = new AtomicLong(1);

    /**
     * 等待响应的并发请求池：requestId -> Sinks.One
     */
    private final Map<String, Sinks.One<JsonRpcResponse>> pendingRequests = new ConcurrentHashMap<>();

    private final Object writeLock = new Object();
    private Process process;
    private BufferedWriter stdinWriter;
    private Thread stdoutReaderThread;
    private Thread stderrReaderThread;
    private volatile boolean closed = false;

    /**
     * 构造函数并初始化受控子进程。
     *
     * @param route            下游路由定义
     * @param objectMapper     JSON 序列化器
     * @param defaultTimeoutMs 默认请求超时时间（毫秒）
     */
    public StdioUpstreamClient(RouteDefinition route,
                              ObjectMapper objectMapper,
                              int defaultTimeoutMs) {
        if (route == null) {
            throw new IllegalArgumentException("route 不能为空");
        }
        this.routeId = route.getId();
        this.commandConfig = route.getStdioCommand();
        if (this.commandConfig == null || this.commandConfig.getCommand() == null || this.commandConfig.getCommand().trim().isEmpty()) {
            throw new IllegalArgumentException("路由 [" + routeId + "] 未提供有效的 stdioCommand 命令行配置");
        }
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        int timeout = defaultTimeoutMs > 0 ? defaultTimeoutMs : 30000;
        this.requestTimeout = Duration.ofMillis(timeout);

        startProcess();
    }

    /**
     * 启动受控本地子进程并拉起双工守护线程。
     */
    private synchronized void startProcess() {
        List<String> commandList = new ArrayList<>();
        commandList.add(commandConfig.getCommand().trim());
        if (commandConfig.getArgs() != null) {
            for (String arg : commandConfig.getArgs()) {
                if (arg != null) {
                    commandList.add(arg);
                }
            }
        }

        ProcessBuilder pb = new ProcessBuilder(commandList);

        // 工作目录校验与设置
        if (commandConfig.getWorkingDirectory() != null && !commandConfig.getWorkingDirectory().trim().isEmpty()) {
            File workDir = new File(commandConfig.getWorkingDirectory().trim());
            if (!workDir.exists() || !workDir.isDirectory()) {
                throw new IllegalArgumentException("指定的子进程工作目录不存在或不是有效目录: " + commandConfig.getWorkingDirectory());
            }
            pb.directory(workDir);
        }

        // 环境变量过滤沙箱：清理所有敏感凭证
        Map<String, String> env = pb.environment();
        sanitizeEnvironment(env);

        try {
            this.process = pb.start();
            log.info("成功为下游路由 [{}] 启动本地受控子进程, 命令: {}", routeId, commandList);

            this.stdinWriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));

            // 启动 stdout 异步读取线程
            this.stdoutReaderThread = new Thread(this::readStdoutLoop, "stdio-out-" + routeId);
            this.stdoutReaderThread.setDaemon(true);
            this.stdoutReaderThread.start();

            // 启动 stderr 异步读取线程（核心防死锁设计）
            this.stderrReaderThread = new Thread(this::readStderrLoop, "stdio-err-" + routeId);
            this.stderrReaderThread.setDaemon(true);
            this.stderrReaderThread.start();

        } catch (IOException e) {
            log.error("启动下游路由 [{}] 的本地受控进程失败: {}", routeId, e.getMessage());
            throw new McpGovernanceException("无法启动受控本地子进程: " + e.getMessage(), e);
        }
    }

    /**
     * 对环境变量进行安全脱敏，剔除所有包含敏感关键字的变量。
     *
     * @param env 环境变量映射表
     */
    static void sanitizeEnvironment(Map<String, String> env) {
        if (env == null || env.isEmpty()) {
            return;
        }
        Set<String> keysToRemove = new HashSet<>();
        for (String key : env.keySet()) {
            if (isSensitiveEnvKey(key)) {
                keysToRemove.add(key);
            }
        }
        for (String key : keysToRemove) {
            env.remove(key);
        }
    }

    /**
     * 判断环境变量名称是否包含敏感关键字。
     *
     * @param key 环境变量名
     * @return true 表示属于敏感变量
     */
    static boolean isSensitiveEnvKey(String key) {
        if (key == null) {
            return false;
        }
        String upper = key.toUpperCase(Locale.ROOT);
        for (String keyword : SENSITIVE_KEYWORD_SET) {
            if (upper.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * stdout 循环读取并分发 JSON-RPC 2.0 报文。
     */
    private void readStdoutLoop() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while (!closed && (line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                handleStdoutMessage(trimmed);
            }
        } catch (IOException e) {
            if (!closed) {
                log.warn("下游路由 [{}] stdout 读取线程异常终止: {}", routeId, e.getMessage());
                failAllPending(new McpGovernanceException("子进程 stdout 管道中断: " + e.getMessage(), e));
            }
        }
    }

    /**
     * stderr 异步消费处理，输出诊断日志，防止缓冲区阻塞导致子进程挂起。
     */
    private void readStderrLoop() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while (!closed && (line = reader.readLine()) != null) {
                log.warn("[STDIO-ERR][{}] {}", routeId, line);
            }
        } catch (IOException e) {
            if (!closed) {
                log.debug("下游路由 [{}] stderr 读取流结束", routeId);
            }
        }
    }

    /**
     * 处理子进程输出的单行 JSON-RPC 文本。
     *
     * @param rawJson 原始 JSON 字符串
     */
    protected void handleStdoutMessage(String rawJson) {
        try {
            JsonRpcResponse response = objectMapper.readValue(rawJson, JsonRpcResponse.class);
            if (response != null && response.getId() != null) {
                String reqId = String.valueOf(response.getId());
                Sinks.One<JsonRpcResponse> sink = pendingRequests.remove(reqId);
                if (sink != null) {
                    sink.tryEmitValue(response);
                } else {
                    log.debug("下游 STDIO [{}] 收到未知或已超时的请求响应: id={}", routeId, reqId);
                }
            }
        } catch (Exception e) {
            log.debug("下游 STDIO [{}] 收到非 JsonRpcResponse 文本: {}", routeId, rawJson);
        }
    }

    @Override
    public String getRouteId() {
        return routeId;
    }

    @Override
    public Mono<List<GovernedTool>> listTools() {
        if (closed) {
            return Mono.error(new IllegalStateException("StdioUpstreamClient 已关闭: " + routeId));
        }

        long reqId = requestIdSequence.getAndIncrement();
        JsonRpcRequest rpcRequest = new JsonRpcRequest(
                reqId,
                "tools/list",
                Collections.emptyMap()
        );

        return sendRpcAndAwait(rpcRequest)
                .timeout(requestTimeout)
                .map(response -> {
                    if (response.getError() != null) {
                        throw new McpGovernanceException("下游 STDIO 返回 tools/list 错误: " + response.getError().getMessage());
                    }
                    return parseToolsFromResult(response.getResult());
                })
                .doOnError(e -> log.error("下游 STDIO 路由 [{}] 执行 tools/list 异常: {}", routeId, e.getMessage()));
    }

    @Override
    public Mono<McpInvocationResult> callTool(String toolName, Map<String, Object> arguments) {
        if (closed) {
            return Mono.error(new IllegalStateException("StdioUpstreamClient 已关闭: " + routeId));
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
                                "下游 STDIO 工具调用错误 [" + response.getError().getCode() + "]: " + response.getError().getMessage(),
                                durationMs
                        );
                    }
                    return McpInvocationResult.success(response.getResult(), durationMs);
                })
                .onErrorResume(e -> {
                    long durationMs = System.currentTimeMillis() - startTime;
                    log.error("下游 STDIO 路由 [{}] 调用工具 [{}] 失败: {}", routeId, toolName, e.getMessage());
                    return Mono.just(McpInvocationResult.failure("下游 STDIO 调用失败: " + e.getMessage(), durationMs));
                });
    }

    @Override
    public Mono<Boolean> ping() {
        if (closed || process == null || !process.isAlive()) {
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
                .map(response -> response.getError() == null)
                .onErrorReturn(false);
    }

    /**
     * 通过 stdin 写入单行 JSON 报文并注册异步等待 Sink。
     *
     * @param request JSON-RPC 请求
     * @return 响应式的 JsonRpcResponse
     */
    protected Mono<JsonRpcResponse> sendRpcAndAwait(JsonRpcRequest request) {
        if (closed || process == null || !process.isAlive()) {
            return Mono.error(new IllegalStateException("下游 STDIO 子进程未运行或已关闭: " + routeId));
        }

        String reqId = String.valueOf(request.getId());
        Sinks.One<JsonRpcResponse> sink = Sinks.one();
        pendingRequests.put(reqId, sink);

        try {
            String jsonLine = objectMapper.writeValueAsString(request);
            synchronized (writeLock) {
                if (closed || stdinWriter == null) {
                    pendingRequests.remove(reqId);
                    return Mono.error(new IllegalStateException("stdin 已关闭"));
                }
                stdinWriter.write(jsonLine);
                stdinWriter.newLine();
                stdinWriter.flush();
            }
        } catch (Exception e) {
            pendingRequests.remove(reqId);
            return Mono.error(new McpGovernanceException("写入子进程 stdin 失败: " + e.getMessage(), e));
        }

        return sink.asMono().doFinally(signalType -> pendingRequests.remove(reqId));
    }

    /**
     * 判断当前子进程是否仍处于存活运行状态。
     *
     * @return true 表示正在运行
     */
    public boolean isAlive() {
        return !closed && process != null && process.isAlive();
    }

    /**
     * 优雅停机并销毁子进程。
     * 遵循两阶段销毁安全协议：先优雅 destroy() 等待 3 秒，若超时未退出则强制 destroyForcibly()。
     */
    @Override
    public synchronized void close() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        log.info("开始优雅关闭下游 STDIO 子进程客户端 [{}]...", routeId);

        // 1. 关闭 stdin 管道
        if (stdinWriter != null) {
            try {
                stdinWriter.close();
            } catch (IOException ignored) {
            }
        }

        // 2. 终止子进程（优雅退隐）
        if (process != null && process.isAlive()) {
            process.destroy();
            try {
                if (!process.waitFor(3, TimeUnit.SECONDS)) {
                    log.warn("子进程 [{}] 未在 3 秒内优雅退出，正在执行强制销毁 (SIGKILL)...", routeId);
                    process.destroyForcibly();
                    process.waitFor(2, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }

        // 3. 失败并清空所有未完成的挂起请求
        failAllPending(new IllegalStateException("STDIO 进程已被销毁关闭: " + routeId));

        log.info("下游 STDIO 子进程客户端 [{}] 已成功关闭并释放资源", routeId);
    }

    private void failAllPending(Throwable cause) {
        for (Sinks.One<JsonRpcResponse> sink : pendingRequests.values()) {
            sink.tryEmitError(cause);
        }
        pendingRequests.clear();
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
