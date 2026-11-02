package io.github.chukui2005.mcpgovernance.gateway.upstream;

import io.github.chukui2005.mcpgovernance.gateway.config.RouteDefinition;
import io.github.chukui2005.mcpgovernance.gateway.config.UpstreamProtocol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link UpstreamClientPool} 单元测试。
 *
 * @author chukui2005
 * @since 0.1.0
 */
class UpstreamClientPoolTest {

    @Test
    @DisplayName("测试连接池正常缓存与复用客户端")
    void testPoolGetAndReuseClient() {
        UpstreamClientFactory factory = mock(UpstreamClientFactory.class);
        UpstreamClient mockClient = mock(UpstreamClient.class);
        when(mockClient.getRouteId()).thenReturn("route-1");
        when(factory.createClient(any(RouteDefinition.class))).thenReturn(mockClient);

        UpstreamClientPool pool = new UpstreamClientPool(factory);
        RouteDefinition route = new RouteDefinition("route-1", UpstreamProtocol.HTTP, "r1_", "http://test-server");

        UpstreamClient c1 = pool.getClient(route);
        UpstreamClient c2 = pool.getClient(route);

        assertThat(c1).isSameAs(c2);
        assertThat(pool.size()).isEqualTo(1);
        verify(factory, times(1)).createClient(route);
    }

    @Test
    @DisplayName("测试逐出单个客户端 evict")
    void testEvictClient() {
        UpstreamClientFactory factory = mock(UpstreamClientFactory.class);
        UpstreamClient mockClient = mock(UpstreamClient.class);
        when(mockClient.getRouteId()).thenReturn("route-1");
        when(factory.createClient(any(RouteDefinition.class))).thenReturn(mockClient);

        UpstreamClientPool pool = new UpstreamClientPool(factory);
        RouteDefinition route = new RouteDefinition("route-1", UpstreamProtocol.HTTP, "r1_", "http://test-server");

        pool.getClient(route);
        assertThat(pool.size()).isEqualTo(1);

        pool.evict("route-1");
        assertThat(pool.size()).isEqualTo(0);
        verify(mockClient, times(1)).close();

        // 再次获取应重新创建
        pool.getClient(route);
        assertThat(pool.size()).isEqualTo(1);
        verify(factory, times(2)).createClient(route);
    }

    @Test
    @DisplayName("测试清空连接池 closeAll 与 destroy")
    void testCloseAll() {
        UpstreamClientFactory factory = mock(UpstreamClientFactory.class);
        UpstreamClient mockClient1 = mock(UpstreamClient.class);
        UpstreamClient mockClient2 = mock(UpstreamClient.class);

        when(factory.createClient(any(RouteDefinition.class)))
                .thenReturn(mockClient1)
                .thenReturn(mockClient2);

        UpstreamClientPool pool = new UpstreamClientPool(factory);
        RouteDefinition r1 = new RouteDefinition("r1", UpstreamProtocol.HTTP, "r1_", "http://s1");
        RouteDefinition r2 = new RouteDefinition("r2", UpstreamProtocol.HTTP, "r2_", "http://s2");

        pool.getClient(r1);
        pool.getClient(r2);
        assertThat(pool.size()).isEqualTo(2);

        pool.destroy();
        assertThat(pool.size()).isEqualTo(0);
        verify(mockClient1, times(1)).close();
        verify(mockClient2, times(1)).close();
    }

    @Test
    @DisplayName("测试传入空参数时校验异常")
    void testInvalidArgs() {
        assertThatThrownBy(() -> new UpstreamClientPool(null))
                .isInstanceOf(IllegalArgumentException.class);

        UpstreamClientFactory factory = mock(UpstreamClientFactory.class);
        UpstreamClientPool pool = new UpstreamClientPool(factory);

        assertThatThrownBy(() -> pool.getClient(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
