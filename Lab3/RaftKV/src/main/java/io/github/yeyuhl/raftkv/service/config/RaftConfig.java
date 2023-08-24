package io.github.yeyuhl.raftkv.service.config;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * RaftConfig顾名思义是Raft的配置类
 *
 * @author yeyuhl
 * @since 2023/8/18
 */
@Data
public class RaftConfig {
    /**
     * 心跳时间间隔
     */
    static public final int HEARTBEAT_INTERVAL = 300;

    /**
     * 选举超时时间
     * 在该时间内没收到心跳信号则发起选举
     */
    static public final int ELECTION_TIMEOUT = 3000;

    static private List<String> addressList = new ArrayList<>();

    public static List<String> getAddressList() {
        return addressList;
    }
}
