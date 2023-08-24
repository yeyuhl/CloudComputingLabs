package io.github.yeyuhl.raftkv.service.dto.client;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 客户端请求
 *
 * @author yeyuhl
 * @since 2023/8/19
 */
@Data
@Builder
public class ClientRequest implements Serializable {
    private static final long serialVersionUID = 5L;
    /**
     * 操作类型
     */
    public static int PUT = 0;
    public static int GET = 1;
    public static int DEL = 2;

    int type;

    /**
     * 键
     */
    String key;

    /**
     * 值
     */
    String value;

    /**
     * 请求id
     */
    String requestId;

    public enum Type {
        PUT(0), GET(1), DEL(2);
        int code;

        Type(int code) {
            this.code = code;
        }

        public static Type value(int code) {
            for (Type type : values()) {
                if (type.code == code) {
                    return type;
                }
            }
            return null;
        }
    }
}
