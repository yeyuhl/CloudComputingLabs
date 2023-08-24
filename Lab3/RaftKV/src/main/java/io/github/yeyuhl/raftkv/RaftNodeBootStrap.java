package io.github.yeyuhl.raftkv;

import io.github.yeyuhl.raftkv.service.RaftNode;
import io.github.yeyuhl.raftkv.service.config.RaftConfig;

import java.util.Collections;
import java.util.List;

/**
 * RaftNodeBootStrap是Raft集群的启动器，它负责初始化集群，并确保所有节点都处于正确的状态。
 *
 * @author yeyuhl
 * @since 2023/8/18
 */
public class RaftNodeBootStrap {
    public static void main(String[] args) {
        String[] arr = new String[]{"localhost:8775", "localhost:8776", "localhost:8777", "localhost:8778", "localhost:8779"};
        List<String> addressList = RaftConfig.getAddressList();
        Collections.addAll(addressList, arr);
        RaftNode node = new RaftNode();
    }
}
