package io.github.chukui2005.mcpgovernance.gateway.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

/**
 * JSON-RPC 2.0 错误体模型。
 *
 * @author chukui2005
 * @since 0.1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JsonRpcError {

    /**
     * 错误码。标准错误码如：-32700 (Parse error), -32600 (Invalid Request), -32601 (Method not found), -32602 (Invalid params), -32603 (Internal error)。
     */
    private int code;

    /**
     * 错误简短描述信息。
     */
    private String message;

    /**
     * 附加错误详情或上下文数据。
     */
    private Object data;

    /**
     * 默认构造函数。
     */
    public JsonRpcError() {
    }

    /**
     * 完整构造函数。
     *
     * @param code    错误码
     * @param message 错误信息
     * @param data    附加错误数据
     */
    public JsonRpcError(int code, String message, Object data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 便捷构造函数（无 data）。
     *
     * @param code    错误码
     * @param message 错误信息
     */
    public JsonRpcError(int code, String message) {
        this(code, message, null);
    }

    /**
     * 构造解析错误 (Parse error, -32700)。
     *
     * @param message 错误描述
     * @return 错误对象
     */
    public static JsonRpcError parseError(String message) {
        return new JsonRpcError(-32700, message != null ? message : "Parse error");
    }

    /**
     * 构造非法请求错误 (Invalid Request, -32600)。
     *
     * @param message 错误描述
     * @return 错误对象
     */
    public static JsonRpcError invalidRequest(String message) {
        return new JsonRpcError(-32600, message != null ? message : "Invalid Request");
    }

    /**
     * 构造方法未找到错误 (Method not found, -32601)。
     *
     * @param message 错误描述
     * @return 错误对象
     */
    public static JsonRpcError methodNotFound(String message) {
        return new JsonRpcError(-32601, message != null ? message : "Method not found");
    }

    /**
     * 构造非法参数错误 (Invalid params, -32602)。
     *
     * @param message 错误描述
     * @return 错误对象
     */
    public static JsonRpcError invalidParams(String message) {
        return new JsonRpcError(-32602, message != null ? message : "Invalid params");
    }

    /**
     * 构造内部错误 (Internal error, -32603)。
     *
     * @param message 错误描述
     * @return 错误对象
     */
    public static JsonRpcError internalError(String message) {
        return new JsonRpcError(-32603, message != null ? message : "Internal error");
    }

    /**
     * 构造治理访问拒绝错误 (Access Denied, -32003)。
     *
     * @param message 拒绝原因
     * @return 错误对象
     */
    public static JsonRpcError accessDenied(String message) {
        return new JsonRpcError(-32003, message != null ? message : "Access Denied");
    }

    /**
     * 获取错误码。
     *
     * @return 错误码
     */
    public int getCode() {
        return code;
    }

    /**
     * 设置错误码。
     *
     * @param code 错误码
     */
    public void setCode(int code) {
        this.code = code;
    }

    /**
     * 获取错误信息。
     *
     * @return 错误信息
     */
    public String getMessage() {
        return message;
    }

    /**
     * 设置错误信息。
     *
     * @param message 错误信息
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * 获取附加错误数据。
     *
     * @return 附加错误数据
     */
    public Object getData() {
        return data;
    }

    /**
     * 设置附加错误数据。
     *
     * @param data 附加错误数据
     */
    public void setData(Object data) {
        this.data = data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JsonRpcError that = (JsonRpcError) o;
        return code == that.code &&
                Objects.equals(message, that.message) &&
                Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, message, data);
    }
}
