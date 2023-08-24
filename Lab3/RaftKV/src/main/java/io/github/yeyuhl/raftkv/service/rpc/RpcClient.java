package io.github.yeyuhl.raftkv.service.rpc;

import com.alipay.remoting.exception.RemotingException;
import io.github.yeyuhl.raftkv.service.dto.rpc.Request;
import io.github.yeyuhl.raftkv.service.dto.rpc.Response;
import lombok.extern.slf4j.Slf4j;



/**
 * Rpc客户端
 *
 * @author yeyuhl
 * @since 2023/8/19
 */
@Slf4j
public class RpcClient {
    private com.alipay.remoting.rpc.RpcClient client;

    public RpcClient() {
        client = new com.alipay.remoting.rpc.RpcClient();
        client.startup();
    }

    public <R> R send(Request request) throws RemotingException, InterruptedException {
        return send(request, 100);
    }

    public <R> R send(Request request, int timeout) throws RemotingException, InterruptedException {
        Response<R> result;
        result = (Response<R>) client.invokeSync(request.getUrl(), request, timeout);
        return result.getResult();
    }

    public void destroy() {
        client.shutdown();
        log.info("destroy success");
    }
}
