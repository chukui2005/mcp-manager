package io.github.chukui2005.mcpgovernance.gateway.upstream.client;

import io.github.chukui2005.mcpgovernance.gateway.config.RouteDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 受控本地 STDIO 进程池托管与生命周期管理器。
 * 负责本地子进程客户端的实例维护、生命周期保活、动态回收与停机清理，
 * 遵循 Phase 3 架构设计与安全基线，杜绝僵尸进程与未关闭的文件管道句柄泄露。
 *
 * @author chukui2005
 * @since 0.1.0
 */
@Component
public class ManagedStdioProcessPool implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ManagedStdioProcessPool.class);

    private final Map<String, StdioUpstreamClient> processClients = new ConcurrentHashMap<>();

    /**
     * 默认构造函数。
     */
    public ManagedStdioProcessPool() {
    }

    /**
     * 注册受托的 STDIO 客户端实例。
     *
     * @param client STDIO 客户端
     */
    public void register(StdioUpstreamClient client) {
        if (client == null || client.getRouteId() == null) {
            return;
        }
        processClients.put(client.getRouteId(), client);
        log.info("受控本地 STDIO 进程池已接管路由 [{}] 的子进程实例", client.getRouteId());
    }

    /**
     * 根据路由 ID 获取托管中的 STDIO 客户端。
     *
     * @param routeId 路由 ID
     * @return StdioUpstreamClient 实例，不存在则返回 null
     */
    public StdioUpstreamClient getClient(String routeId) {
        if (routeId == null) {
            return null;
        }
        return processClients.get(routeId);
    }

    /**
     * 逐出并安全关闭指定路由的本地子进程。
     *
     * @param routeId 路由 ID
     */
    public void evict(String routeId) {
        if (routeId == null) {
            return;
        }
        StdioUpstreamClient client = processClients.remove(routeId);
        if (client != null) {
            try {
                client.close();
                log.info("成功从受控 STDIO 进程池中注销并销毁路由 [{}] 的子进程", routeId);
            } catch (Exception e) {
                log.warn("销毁路由 [{}] 的子进程时异常: {}", routeId, e.getMessage());
            }
        }
    }

    /**
     * 获取当前托管中的子进程数量。
     *
     * @return 活跃进程客户端数量
     */
    public int size() {
        return processClients.size();
    }

    /**
     * 清理并安全关闭所有受控子进程（Spring 容器销毁或网关停机回调）。
     */
    @Override
    public void destroy() {
        log.info("正在关闭受控本地 STDIO 进程池，当前托管 {} 个子进程...", processClients.size());
        for (Map.Entry<String, StdioUpstreamClient> entry : processClients.entrySet()) {
            try {
                entry.getValue().close();
                log.debug("已安全终止受控子进程: {}", entry.getKey());
            } catch (Exception e) {
                log.warn("终止受控子进程 [{}] 时异常: {}", entry.getKey(), e.getMessage());
            }
        }
        processClients.clear();
        log.info("受控本地 STDIO 进程池已全部销毁清理完毕");
    }
}
