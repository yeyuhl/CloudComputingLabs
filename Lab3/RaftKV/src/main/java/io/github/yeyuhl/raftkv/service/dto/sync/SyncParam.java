package io.github.yeyuhl.raftkv.service.dto.sync;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * leader-follower日志同步请求参数
 *
 * @author yeyuhl
 * @since 2023/8/19
 */
@Data
@Builder
public class SyncParam implements Serializable {
    private static final long serialVersionUID = 9L;

    /**
     * 请求者Id(ip:selfPort)
     */
    private String followerId;

    private long followerIndex;
}
