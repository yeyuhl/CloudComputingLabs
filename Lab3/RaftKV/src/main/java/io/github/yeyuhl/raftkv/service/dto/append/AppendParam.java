package io.github.yeyuhl.raftkv.service.dto.append;

import io.github.yeyuhl.raftkv.service.log.LogEntry;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * AppendEntries RPC参数
 *
 * @author yeyuhl
 * @since 2023/8/19
 */
@Data
@Builder
public class AppendParam implements Serializable {
    private static final long serialVersionUID = 3L;
    /**
     * 候选人的任期号
     */
    private long term;
    /**
     * 被请求者Id
     */
    private String serverId;
    /**
     * 领导人的Id，以便于跟随者重定向请求
     */
    private String leaderId;
    /**
     * 新的日志条目紧随之前的索引值
     */
    private long prevLogIndex;
    /**
     * prevLogIndex的任期号
     */
    private long preLogTerm;
    /**
     * 需要存储的日志条目（表示心跳时为空；一次性发送多个是为了提高效率）
     */
    private LogEntry[] entries;
    /**
     * 领导人已经提交的日志的索引值
     */
    private long leaderCommit;
}
