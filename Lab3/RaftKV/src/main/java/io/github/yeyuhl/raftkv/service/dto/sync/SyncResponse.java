package io.github.yeyuhl.raftkv.service.dto.sync;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * leader-follower日志同步请求响应
 *
 * @author yeyuhl
 * @since 2023/8/19
 */
@Data
@Builder
public class SyncResponse implements Serializable {
    private static final long serialVersionUID = 11L;
    /**
     * 处理结果
     */
    private boolean status;

    public static SyncResponse fail() {
        return new SyncResponse(false);
    }

    public static SyncResponse success() {
        return new SyncResponse(true);
    }

    public boolean isSuccess() {
        return status;
    }
}
