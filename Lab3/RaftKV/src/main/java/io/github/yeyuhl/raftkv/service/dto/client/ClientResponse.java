package io.github.yeyuhl.raftkv.service.dto.client;

import lombok.Data;

import java.io.Serializable;

/**
 * 客户端响应
 *
 * @author yeyuhl
 * @since 2023/8/19
 */
@Data
public class ClientResponse implements Serializable {
    private static final long serialVersionUID = 6L;

    /**
     * 响应码
     * success 0
     * fail -1
     * redirect 1
     */
    int code;

    /**
     * 响应携带的数据
     */
    Object result;

    private ClientResponse(int code, Object result) {
        this.code = code;
        this.result = result;
    }

    public static ClientResponse success() {
        return new ClientResponse(0, null);
    }

    public static ClientResponse success(String value) {
        return new ClientResponse(0, value);
    }

    public static ClientResponse fail() {
        return new ClientResponse(-1, null);
    }

    public static ClientResponse redirect(String address) {
        return new ClientResponse(1, address);
    }
}
