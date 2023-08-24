package io.github.yeyuhl.raftkv.service.dto.rpc;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * RPC调用响应
 *
 * @author yeyuhl
 * @since 2023/8/19
 */
@Data
@Builder
public class Response<T> implements Serializable {
    private static final long serialVersionUID = 8L;
    private T result;

    public Response(T result) {
        this.result = result;
    }

    public static Response<String> success() {
        return new Response<>("success");
    }

    public static Response<String> fail() {
        return new Response<>("fail");
    }


    @Override
    public String toString() {
        return "Response{" +
                "result=" + result +
                '}';
    }
}
