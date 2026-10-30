package io.github.chukui2005.mcpgovernance.gateway.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

/**
 * 标准 JSON-RPC 2.0 响应模型。
 *
 * @author chukui2005
 * @since 0.1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JsonRpcResponse {

    /**
     * 协议版本，固定为 "2.0"。
     */
    private String jsonrpc = "2.0";

    /**
     * 请求唯一标识，与请求报文中的 id 保持一致。
     */
    private Object id;

    /**
     * 调用成功时的响应数据。
     */
    private Object result;

    /**
     * 调用失败时的错误对象。
     */
    private JsonRpcError error;

    /**
     * 默认构造函数。
     */
    public JsonRpcResponse() {
    }

    /**
     * 成功响应构造函数。
     *
     * @param id     请求标识
     * @param result 响应结果
     */
    public JsonRpcResponse(Object id, Object result) {
        this.jsonrpc = "2.0";
        this.id = id;
        this.result = result;
        this.error = null;
    }

    /**
     * 错误响应构造函数。
     *
     * @param id    请求标识
     * @param error 错误对象
     */
    public JsonRpcResponse(Object id, JsonRpcError error) {
        this.jsonrpc = "2.0";
        this.id = id;
        this.result = null;
        this.error = error;
    }

    /**
     * 快速构建成功响应。
     *
     * @param id     请求标识
     * @param result 响应结果
     * @return 响应对象
     */
    public static JsonRpcResponse success(Object id, Object result) {
        return new JsonRpcResponse(id, result);
    }

    /**
     * 快速构建失败响应。
     *
     * @param id    请求标识
     * @param error 错误对象
     * @return 响应对象
     */
    public static JsonRpcResponse error(Object id, JsonRpcError error) {
        return new JsonRpcResponse(id, error);
    }

    /**
     * 获取协议版本。
     *
     * @return 协议版本号
     */
    public String getJsonrpc() {
        return jsonrpc;
    }

    /**
     * 设置协议版本。
     *
     * @param jsonrpc 协议版本号
     */
    public void setJsonrpc(String jsonrpc) {
        this.jsonrpc = jsonrpc;
    }

    /**
     * 获取请求标识。
     *
     * @return 请求标识
     */
    public Object getId() {
        return id;
    }

    /**
     * 设置请求标识。
     *
     * @param id 请求标识
     */
    public void setId(Object id) {
        this.id = id;
    }

    /**
     * 获取响应结果。
     *
     * @return 响应结果
     */
    public Object getResult() {
        return result;
    }

    /**
     * 设置响应结果。
     *
     * @param result 响应结果
     */
    public void setResult(Object result) {
        this.result = result;
    }

    /**
     * 获取错误对象。
     *
     * @return 错误对象
     */
    public JsonRpcError getError() {
        return error;
    }

    /**
     * 设置错误对象。
     *
     * @param error 错误对象
     */
    public void setError(JsonRpcError error) {
        this.error = error;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JsonRpcResponse that = (JsonRpcResponse) o;
        return Objects.equals(jsonrpc, that.jsonrpc) &&
                Objects.equals(id, that.id) &&
                Objects.equals(result, that.result) &&
                Objects.equals(error, that.error);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jsonrpc, id, result, error);
    }
}
