package io.github.yeyuhl.raftkv.service.dto.vote;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 投票请求
 *
 * @author yeyuhl
 * @since 2023/8/19
 */
@Data
@Builder
public class VoteParam implements Serializable {
    private static final long serialVersionUID = 12L;
    /**
     * 候选人的任期号
     */
    private long term;

    /**
     * 选民Id(ip:selfPort)
     */
    private String peerAddress;

    /**
     * 候选人Id(ip:selfPort)
     */
    private String candidateAddress;

    /**
     * 候选人最新的日志条目的索引值
     */
    private long lastLogIndex;

    /**
     * 候选人最新的日志条目的任期号
     */
    private long lastLogTerm;
}
