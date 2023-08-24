package io.github.yeyuhl.raftkv.service.constant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 客户端命令
 *
 * @author yeyuhl
 * @since 2023/8/18
 */

@Data
@Builder
@AllArgsConstructor
public class Command implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 操作类型
     */
    CommandType type;
    String key;
    String value;
}
