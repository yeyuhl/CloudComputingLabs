package io.github.yeyuhl.raftkv.service.constant;

/**
 * 节点状态，即节点目前是跟随者、候选人还是领导者
 *
 * @author yeyuhl
 * @since 2023/8/19
 */
public enum NodeStatus {
    /**
     * 跟随者
     */
    FOLLOWER,
    /**
     * 候选人
     */
    CANDIDATE,
    /**
     * 领导者
     */
    LEADER;
}
