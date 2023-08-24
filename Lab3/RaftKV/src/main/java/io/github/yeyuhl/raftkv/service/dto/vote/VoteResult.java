package io.github.yeyuhl.raftkv.service.dto.vote;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@Builder
public class VoteResult implements Serializable {
    private static final long serialVersionUID = 13L;

    /**
     * 当前任期号，以便于候选人去更新自己的任期号
     */
    private long term;

    /**
     * 候选人赢得了此张选票时为真
     */
    private boolean voteGranted;

    public VoteResult(long term) {
        this.term = term;
    }

    public VoteResult(boolean voteGranted) {
        this.voteGranted = voteGranted;
    }

    public VoteResult(long term, boolean voteGranted) {
        this.term = term;
        this.voteGranted = voteGranted;
    }

    public static VoteResult fail() {
        return new VoteResult(false);
    }

    public static VoteResult success() {
        return new VoteResult(true);
    }


}
