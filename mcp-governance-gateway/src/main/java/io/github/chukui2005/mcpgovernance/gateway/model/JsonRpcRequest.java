package io.github.chukui2005.mcpgovernance.gateway.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

/**
 * 标准 JSON-RPC 2.0 请求模型。
 *
 * @author chukui2005
 * @since 0.1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JsonRpcRequest {

    /**
     * 协议版本，固定为 "2.0"。
     */
    private String jsonrpc = "2.0";

    /**
     * 请求唯一标识，字符串或数字，通知请求时为 null。
     */
    private Object id;

    /**
     * 请求调用的方法名，如 "initialize", "tools/list", "tools/call"。
     */
    private String method;

    /**
     * 方法参数对象或数组。
     */
    private Object params;

    /**
     * 默认构造函数。
     */
    public JsonRpcRequest() {
    }

    /**
     * 完整构造函数。
     *
     * @param id     请求标识
     * @param method 方法名称
     * @param params 请求参数
     */
    public JsonRpcRequest(Object id, String method, Object params) {
        this.jsonrpc = "2.0";
        this.id = id;
        this.method = method;
        this.params = params;
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
     * 获取调用方法名。
     *
     * @return 方法名
     */
    public String getMethod() {
        return method;
    }

    /**
     * 设置调用方法名。
     *
     * @param method 方法名
     */
    public void setMethod(String method) {
        this.method = method;
    }

    /**
     * 获取调用入参。
     *
     * @return 调用参数
     */
    public Object getParams() {
        return params;
    }

    /**
     * 设置调用入参。
     *
     * @param params 调用参数
     */
    public void setParams(Object params) {
        this.params = params;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JsonRpcRequest that = (JsonRpcRequest) o;
        return Objects.equals(jsonrpc, that.jsonrpc) &&
                Objects.equals(id, that.id) &&
                Objects.equals(method, that.method) &&
                Objects.equals(params, that.params);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jsonrpc, id, method, params);
    }
}
