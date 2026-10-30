package io.github.chukui2005.mcpgovernance.gateway.route;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.chukui2005.mcpgovernance.gateway.config.GatewayRoutesConfig;
import io.github.chukui2005.mcpgovernance.gateway.config.RouteDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * 外部路由配置文件加载器。
 * 支持从文件路径、Classpath 或 Spring 资源定位符（如 "file:./config/routes.json", "classpath:gateway-routes.json"）
 * 解析符合契约 gateway-routes.schema.json 的路由定义，并将其动态加载/热更新至 RouteManager。
 *
 * @author chukui2005
 * @since 0.1.0
 */
@Component
public class RouteConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(RouteConfigLoader.class);

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    /**
     * 默认构造函数，使用标准 ObjectMapper 与 DefaultResourceLoader。
     */
    public RouteConfigLoader() {
        this(new ObjectMapper(), new DefaultResourceLoader());
    }

    /**
     * 依赖注入构造函数。
     *
     * @param objectMapper   JSON 序列化解析器
     * @param resourceLoader Spring 资源加载器
     */
    public RouteConfigLoader(ObjectMapper objectMapper, ResourceLoader resourceLoader) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.resourceLoader = resourceLoader != null ? resourceLoader : new DefaultResourceLoader();
    }

    /**
     * 从 JSON 格式字符串解析路由配置对象。
     *
     * @param jsonContent JSON 文本内容
     * @return 解析得到的 GatewayRoutesConfig
     * @throws IllegalArgumentException 当 JSON 格式错误或缺失必要字段时抛出
     */
    public GatewayRoutesConfig parseConfig(String jsonContent) {
        if (jsonContent == null || jsonContent.trim().isEmpty()) {
            throw new IllegalArgumentException("路由配置 JSON 内容不能为空");
        }
        try {
            GatewayRoutesConfig config = objectMapper.readValue(jsonContent, GatewayRoutesConfig.class);
            validateConfig(config);
            return config;
        } catch (Exception e) {
            log.error("解析网关路由配置 JSON 失败: {}", e.getMessage());
            throw new IllegalArgumentException("解析网关路由配置失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从输入流中读取并解析路由配置。
     *
     * @param inputStream 输入流
     * @return 解析得到的 GatewayRoutesConfig
     * @throws IOException 当读取流发生 I/O 错误时抛出
     */
    public GatewayRoutesConfig loadFromStream(InputStream inputStream) throws IOException {
        Objects.requireNonNull(inputStream, "inputStream 不能为 null");
        try (inputStream) {
            String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return parseConfig(content);
        }
    }

    /**
     * 从本地文件路径读取并解析路由配置。
     *
     * @param file 配置文件
     * @return 解析得到的 GatewayRoutesConfig
     * @throws IOException 当文件读取失败时抛出
     */
    public GatewayRoutesConfig loadFromFile(File file) throws IOException {
        Objects.requireNonNull(file, "file 不能为 null");
        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("配置文件不存在或不是普通文件: " + file.getAbsolutePath());
        }
        try (InputStream fis = new FileInputStream(file)) {
            return loadFromStream(fis);
        }
    }

    /**
     * 从 Spring 资源定位符（例如 classpath:routes.json 或 file:/path/to/routes.json）读取并解析路由配置。
     *
     * @param location 资源路径
     * @return 解析得到的 GatewayRoutesConfig
     * @throws IOException 当资源加载失败时抛出
     */
    public GatewayRoutesConfig loadFromResource(String location) throws IOException {
        if (location == null || location.trim().isEmpty()) {
            throw new IllegalArgumentException("资源定位路径 location 不能为空");
        }
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalArgumentException("指定的路由资源不存在: " + location);
        }
        try (InputStream is = resource.getInputStream()) {
            return loadFromStream(is);
        }
    }

    /**
     * 从资源定位符加载路由配置并热更新至 RouteManager。
     * 若 resetExisting 为 true，则会先清空 RouteManager 中现存的全部路由再执行注入。
     *
     * @param location      资源定位路径
     * @param routeManager  待更新的路由管理器
     * @param resetExisting 是否重置清空现存路由
     * @return 加载生效的路由定义列表
     * @throws IOException 当读取或解析失败时抛出
     */
    public List<RouteDefinition> loadAndApply(String location, RouteManager routeManager, boolean resetExisting) throws IOException {
        Objects.requireNonNull(routeManager, "routeManager 不能为 null");
        GatewayRoutesConfig config = loadFromResource(location);
        List<RouteDefinition> upstreams = config.getUpstreams();

        if (resetExisting) {
            routeManager.clear();
        }
        routeManager.registerRoutes(upstreams);
        log.info("成功从 [{}] 动态加载并应用了 {} 条下游路由规则", location, upstreams.size());
        return upstreams;
    }

    /**
     * 针对配置模型进行防御性合规校验。
     *
     * @param config 路由配置对象
     */
    private void validateConfig(GatewayRoutesConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("GatewayRoutesConfig 不能为 null");
        }
        if (config.getUpstreams() == null) {
            throw new IllegalArgumentException("upstreams 列表不能为 null");
        }
        for (RouteDefinition route : config.getUpstreams()) {
            if (route.getId() == null || route.getId().trim().isEmpty()) {
                throw new IllegalArgumentException("每个 upstream 必须包含有效的 id");
            }
            if (route.getProtocol() == null) {
                throw new IllegalArgumentException("upstream [" + route.getId() + "] 必须声明 protocol (sse/http/stdio)");
            }
            if (route.getPrefix() == null || route.getPrefix().trim().isEmpty()) {
                throw new IllegalArgumentException("upstream [" + route.getId() + "] 必须声明 prefix");
            }
        }
    }
}
