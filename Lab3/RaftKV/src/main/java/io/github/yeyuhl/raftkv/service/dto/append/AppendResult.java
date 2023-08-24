package io.github.yeyuhl.raftkv.service.dto.append;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * AppendEntries RPC结果
 *
 * @author yeyuhl
 * @since 2023/8/19
 */
@Data
@Builder
public class AppendResult implements Serializable {
    private static final long serialVersionUID = 4L;
    /**
     * 被请求者的任期号，用于领导人去更新自己
     */
    long term;
    /**
     * 跟随者包含了匹配上prevLogIndex和preLogTerm的日志时为真
     */
    boolean success;

    public AppendResult(long term) {
        this.term = term;
    }

    public AppendResult(boolean success) {
        this.success = success;
    }

    public AppendResult(long term, boolean success) {
        this.term = term;
        this.success = success;
    }

    public static AppendResult fail() {
        return new AppendResult(false);
    }

    public static AppendResult success() {
        return new AppendResult(true);
    }





}
