package io.github.yeyuhl.raftkv.service.log;

import io.github.yeyuhl.raftkv.service.constant.Command;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 日志条目
 *
 * @author yeyuhl
 * @since 2023/8/19
 */
@Data
@Builder
@AllArgsConstructor
public class LogEntry implements Serializable {
    private static final long serialVersionUID = 2L;
    /**
     * log index
     */
    private Long index;

    /**
     * log term
     */
    private long term;

    /**
     * 客户端命令
     */
    private Command command;

    /**
     * 请求ID
     */
    private String requestId;
}
