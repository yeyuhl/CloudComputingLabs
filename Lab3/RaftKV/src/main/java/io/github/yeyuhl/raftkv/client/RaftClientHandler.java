package io.github.yeyuhl.raftkv.client;

import io.github.yeyuhl.raftkv.service.dto.client.ClientRequest;
import io.github.yeyuhl.raftkv.service.dto.client.ClientResponse;
import io.github.yeyuhl.raftkv.service.dto.rpc.Request;
import io.github.yeyuhl.raftkv.service.rpc.RpcClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Raft客户端处理器
 *
 * @author yeyuhl
 * @since 2023/8/22
 */
public class RaftClientHandler {
    private static List<String> list;

    private RpcClient client;

    private int index;

    private int size;

    private String address;

    public RaftClientHandler() {
        client = new RpcClient();
        String[] str = new String[]{"localhost:8775", "localhost:8776", "localhost:8777", "localhost:8778", "localhost:8779"};
        list = new ArrayList<>();
        Collections.addAll(list, str);
        index = 0;
        size = list.size();
        address = list.get(index);
    }

    public String get(String key, String requestId) throws InterruptedException {
        // raft客户端请求
        ClientRequest clientRequest = ClientRequest.builder()
                .key(key)
                .type(ClientRequest.GET)
                .requestId(requestId)
                .build();

        // rpc请求
        Request request = Request.builder()
                .cmd(Request.CLIENT_REQUEST)
                .obj(clientRequest)
                .url(address)
                .build();

        ClientResponse response = null;
        // 不断重试，直到响应成功
        while (response == null || response.getCode() != 0) {
            try {
                response = client.send(request, 8000);
            } catch (Exception e) {
                // 请求超时，连接下一个节点
                index = (index + 1) % size;
                address = list.get(index);
                request.setUrl(address);
                Thread.sleep(500);
                continue;
            }
            // 解析响应数据
            if (response.getCode() == -1) {
                // 响应码为-1即fail，尝试连接下一个节点
                index = (index + 1) % size;
                request.setUrl(list.get(index));
                Thread.sleep(500);
            } else if (response.getCode() == 1) {
                // 响应码为1即redirect，重定向到新的节点
                address = response.getResult().toString();
                request.setUrl(address);
            }
        }
        return response.getResult().toString();
    }

    public String put(String key, String value, String requestId) throws InterruptedException {
        ClientRequest clientRequest = ClientRequest.builder()
                .key(key)
                .value(value)
                .type(ClientRequest.PUT)
                .requestId(requestId)
                .build();

        Request request = Request.builder()
                .obj(clientRequest)
                .url(address)
                .cmd(Request.CLIENT_REQUEST)
                .build();

        ClientResponse response = null;
        // 不断重试，直到响应成功
        while (response == null || response.getCode() != 0) {
            try {
                response = client.send(request, 8000);
            } catch (Exception e) {
                // 请求超时，连接下一个节点
                index = (index + 1) % size;
                address = list.get(index);
                request.setUrl(address);
                Thread.sleep(500);
                continue;
            }
            // 解析响应数据
            if (response.getCode() == -1) {
                // 响应码为-1即fail，尝试连接下一个节点
                index = (index + 1) % size;
                address = list.get(index);
                request.setUrl(address);
                Thread.sleep(500);
            } else if (response.getCode() == 1) {
                // 响应码为1即redirect，重定向到新的节点
                address = response.getResult().toString();
                request.setUrl(address);
            }
        }
        return "success";
    }

    public String del(String key, String value, String requestId) throws InterruptedException {
        ClientRequest clientRequest = ClientRequest.builder()
                .key(key)
                .value(value)
                .type(ClientRequest.DEL)
                .requestId(requestId)
                .build();

        Request request = Request.builder()
                .obj(clientRequest)
                .url(address)
                .cmd(Request.CLIENT_REQUEST).build();

        ClientResponse response = null;
        // 不断重试，直到响应成功
        while (response == null || response.getCode() != 0) {
            try {
                response = client.send(request, 500);
            } catch (Exception e) {
                // 请求超时，连接下一个节点
                index = (index + 1) % size;
                address = list.get(index);
                request.setUrl(address);
                Thread.sleep(500);
                continue;
            }
            // 解析响应数据
            if (response.getCode() == -1) {
                // 响应码为-1即fail，尝试连接下一个节点
                index = (index + 1) % size;
                address = list.get(index);
                request.setUrl(address);
                Thread.sleep(500);
            } else if (response.getCode() == 1) {
                // 响应码为1即redirect，重定向到新的节点
                address = response.getResult().toString();
                request.setUrl(address);
            }
        }
        return "success";
    }

}
