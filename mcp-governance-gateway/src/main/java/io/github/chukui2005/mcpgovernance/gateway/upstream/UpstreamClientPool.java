package io.github.chukui2005.mcpgovernance.gateway.upstream;

import io.github.chukui2005.mcpgovernance.gateway.config.RouteDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 下游出站客户端连接池与生命周期管理器。
 * 负责缓存与复用各个下游路由的长连接与出站客户端，避免高并发下无节制重复建连。
 * 实现 {@link DisposableBean}，在网关停机时优雅关闭并释放所有出站连接。
 *
 * @author chukui2005
 * @since 0.1.0
 */
@Component
public class UpstreamClientPool implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(UpstreamClientPool.class);

    private final UpstreamClientFactory clientFactory;
    private final Map<String, UpstreamClient> pool = new ConcurrentHashMap<>();

    /**
     * 构造函数注入客户端工厂。
     *
     * @param clientFactory 出站客户端工厂
     */
    public UpstreamClientPool(UpstreamClientFactory clientFactory) {
        if (clientFactory == null) {
            throw new IllegalArgumentException("clientFactory 不能为空");
        }
        this.clientFactory = clientFactory;
    }

    /**
     * 获取或创建对应路由的出站客户端实例。
     *
     * @param route 路由定义
     * @return 缓存或新创建的 UpstreamClient
     */
    public UpstreamClient getClient(RouteDefinition route) {
        if (route == null || route.getId() == null) {
            throw new IllegalArgumentException("route 及其 id 不能为空");
        }

        return pool.computeIfAbsent(route.getId(), id -> {
            log.info("连接池中未命中下游客户端，正在初始化路由 [{}] 的客户端...", id);
            return clientFactory.createClient(route);
        });
    }

    /**
     * 根据路由 ID 获取已缓存的客户端实例（若不存在则返回 null）。
     *
     * @param routeId 路由唯一标识符
     * @return 缓存的 UpstreamClient，不存在时返回 null
     */
    public UpstreamClient getExistingClient(String routeId) {
        if (routeId == null) {
            return null;
        }
        return pool.get(routeId);
    }

    /**
     * 逐出并关闭指定路由的客户端。
     *
     * @param routeId 路由 ID
     */
    public void evict(String routeId) {
        if (routeId == null) {
            return;
        }
        UpstreamClient client = pool.remove(routeId);
        if (client != null) {
            try {
                client.close();
                log.info("成功从连接池中逐出并关闭路由 [{}] 的客户端", routeId);
            } catch (Exception e) {
                log.warn("关闭路由 [{}] 客户端时发生异常: {}", routeId, e.getMessage());
            }
        }
    }

    /**
     * 关闭并清空连接池中所有客户端。
     */
    public void closeAll() {
        log.info("正在清空下游客户端连接池，总计 {} 个客户端待释放...", pool.size());
        for (Map.Entry<String, UpstreamClient> entry : pool.entrySet()) {
            try {
                entry.getValue().close();
                log.debug("已关闭客户端: {}", entry.getKey());
            } catch (Exception e) {
                log.warn("关闭客户端 [{}] 时发生异常: {}", entry.getKey(), e.getMessage());
            }
        }
        pool.clear();
    }

    /**
     * 当前连接池中活跃客户端数量。
     *
     * @return 活跃连接数
     */
    public int size() {
        return pool.size();
    }

    @Override
    public void destroy() {
        closeAll();
    }
}
