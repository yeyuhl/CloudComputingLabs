package io.github.yeyuhl.raftkv.service.rpc;

import com.alipay.remoting.BizContext;
import com.alipay.remoting.rpc.protocol.SyncUserProcessor;
import io.github.yeyuhl.raftkv.service.RaftNode;
import io.github.yeyuhl.raftkv.service.dto.append.AppendParam;
import io.github.yeyuhl.raftkv.service.dto.client.ClientRequest;
import io.github.yeyuhl.raftkv.service.dto.rpc.Request;
import io.github.yeyuhl.raftkv.service.dto.rpc.Response;
import io.github.yeyuhl.raftkv.service.dto.sync.SyncParam;
import io.github.yeyuhl.raftkv.service.dto.vote.VoteParam;
import lombok.extern.slf4j.Slf4j;

/**
 * RPC服务器
 *
 * @author yeyuhl
 * @since 2023/8/19
 */
@Slf4j
public class RpcServer {
    private final RaftNode node;

    private final com.alipay.remoting.rpc.RpcServer server;

    public RpcServer(int port, RaftNode node) {

        // 初始化rpc服务端
        server = new com.alipay.remoting.rpc.RpcServer(port, false, false);

        // 实现用户请求处理器
        server.registerUserProcessor(new SyncUserProcessor<Request>() {

            @Override
            public Object handleRequest(BizContext bizContext, Request request) throws Exception {
                return handlerRequest(request);
            }

            @Override
            public String interest() {
                return Request.class.getName();
            }
        });
        this.node = node;
        server.startup();
    }

    /**
     * 判断请求类型,调用node的handler进行响应
     *
     * @param request 请求参数
     */
    public Response<?> handlerRequest(Request request) {
        if (request.getCmd() == Request.RAFT_VOTE) {
            // 处理来自其它节点的投票请求，决定是否投票
            return new Response<>(node.requestVote((VoteParam) request.getObj()));
        } else if (request.getCmd() == Request.APPEND_ENTRIES) {
            return new Response<>(node.appendEntries((AppendParam) request.getObj()));
        } else if (request.getCmd() == Request.CLIENT_REQUEST) {
            return new Response<>(node.propose((ClientRequest) request.getObj()));
        } else if (request.getCmd() == Request.FOLLOWER_SYNC) {
            return new Response<>(node.handleSyncRequest((SyncParam) request.getObj()));
        }
        return null;
    }

    public void destroy() {
        log.info("destroy success");
    }
}
