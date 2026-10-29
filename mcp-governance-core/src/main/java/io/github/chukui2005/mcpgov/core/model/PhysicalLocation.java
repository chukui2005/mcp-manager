package io.github.chukui2005.mcpgov.core.model;

import java.util.Objects;

/**
 * 扫描发现的源码或物理位置标识。
 *
 * @param uri 相对工程根路径或文件的 URI
 * @param startLine 起始行号 (1-based)
 * @param startColumn 起始列号 (1-based)
 * @param endLine 结束行号 (1-based)
 * @param endColumn 结束列号 (1-based)
 */
public record PhysicalLocation(
        String uri,
        Integer startLine,
        Integer startColumn,
        Integer endLine,
        Integer endColumn
) {
    /**
     * 紧凑构造器，执行非空校验
     */
    public PhysicalLocation {
        Objects.requireNonNull(uri, "uri 不能为空");
    }

    /**
     * 快速构建仅包含文件路径的物理位置
     *
     * @param uri 文件 URI
     * @return 物理位置实例
     */
    public static PhysicalLocation of(String uri) {
        return new PhysicalLocation(uri, null, null, null, null);
    }

    /**
     * 构建包含行号的物理位置
     *
     * @param uri 文件 URI
     * @param line 行号
     * @return 物理位置实例
     */
    public static PhysicalLocation of(String uri, int line) {
        return new PhysicalLocation(uri, line, 1, line, 1);
    }
}
