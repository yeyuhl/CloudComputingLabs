package com.httpserver;


import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.multipart.MemoryAttribute;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yeyuhl
 * @date 2023/4/24
 */
public class MyResponse {

    private static final MyResponse INSTANCE = new MyResponse();

    private MyResponse() {
    }

    public static MyResponse getInstance() {
        return INSTANCE;
    }

    /**
     * 解析url
     */
    public String[] parsePath(String path) {
        if (path.startsWith("/") && path.contains("api")) {
            if (path.equals("/api/check")) return new String[]{"data/data.txt", "text/plain"};
        } else if (path.startsWith("/")) {
            // 判断是否根目录
            path = path.equals("/") ? "index.html" : path.substring(1);
            switch (path) {
                case "index.html":
                    return new String[]{"static/index.html", "text/html"};
                case "403.html":
                    return new String[]{"static/403.html", "text/html"};
                case "404.html":
                    return new String[]{"static/404.html", "text/html"};
                case "501.html":
                    return new String[]{"static/501.html", "text/html"};
                case "502.html":
                    return new String[]{"static/502.html", "text/html"};
                case "test/test.html":
                    return new String[]{"static/test/test.html", "text/html"};
            }
        }
        return new String[]{"static/404.html", "text/html"};
    }

    /**
     * 解析请求并作出处理，然后转换成响应数据包发回去
     */
    public FullHttpResponse resolveResource(FullHttpRequest request) {
        String path = String.valueOf(request.uri());
        String method = String.valueOf(request.method());
        String[] headers = {request.headers().get("Content-Type"), request.headers().get("content-length")};
        Map<String, Object> map = getRequestParams(request);
        if (method.equals("GET")) {
            String[] urlPath = parsePath(path);
            byte[] bytes = getBytes(urlPath);
            String type = urlPath[1];
            assert bytes != null;
            switch (urlPath[0]) {
                case "static/index.html":
                case "static/test/test.html":
                case "data/data.txt":
                    return this.packetGet(HttpResponseStatus.OK, bytes, type, bytes.length);
                case "static/403.html":
                    return this.packetGet(HttpResponseStatus.FORBIDDEN, bytes, type, bytes.length);
                case "static/501.html":
                    return this.packetGet(HttpResponseStatus.NOT_IMPLEMENTED, bytes, type, bytes.length);
                case "static/502.html":
                    return this.packetGet(HttpResponseStatus.BAD_GATEWAY, bytes, type, bytes.length);
                case "static/404.html":
                default:
                    return this.packetGet(HttpResponseStatus.NOT_FOUND, bytes, type, bytes.length);
            }
        } else if (method.equals("POST")) {
            if (path.equals("/api/echo")) {
                if (map.containsKey("id") && map.containsKey("name")) {
                    // 这里用request.content()会报错，没想明白为什么，于是曲线救国
                    return this.packetPost(HttpResponseStatus.OK, "id=" + map.get("id") + "&" + "name=" + map.get("name"), headers[0], headers[1]);
                } else {
                    byte[] bytes = getBytes(new String[]{"data/error.txt", "text/plain"});
                    assert bytes != null;
                    return this.packetGet(HttpResponseStatus.NOT_FOUND, bytes, "text/plain", bytes.length);
                }
            } else {
                byte[] bytes = getBytes(new String[]{"static/404.html", "text/html"});
                assert bytes != null;
                return this.packetGet(HttpResponseStatus.NOT_FOUND, bytes, "text/html", bytes.length);
            }
        }
        byte[] bytes = getBytes(new String[]{"static/501.html", "text/html"});
        assert bytes != null;
        return this.packetGet(HttpResponseStatus.NOT_IMPLEMENTED, bytes, "text/html", bytes.length);
    }

    /**
     * 读取文件
     */
    private byte[] getBytes(String[] urlPath) {
        try (InputStream stream = this.getClass().getClassLoader().getResourceAsStream(urlPath[0])) {
            if (stream != null) {   //拿到文件输入流之后，才可以返回页面
                byte[] bytes = new byte[stream.available()];
                stream.read(bytes);
                return bytes;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 获取Request的参数
     */
    private Map<String, Object> getRequestParams(FullHttpRequest request) {
        HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(new DefaultHttpDataFactory(false), request);
        List<InterfaceHttpData> httpPostData = decoder.getBodyHttpDatas();
        Map<String, Object> params = new HashMap<>();
        for (InterfaceHttpData data : httpPostData) {
            if (data.getHttpDataType() == InterfaceHttpData.HttpDataType.Attribute) {
                MemoryAttribute attribute = (MemoryAttribute) data;
                params.put(attribute.getName(), attribute.getValue());
            }
        }
        return params;
    }

    /**
     * 封装GET方法的Response
     */
    private FullHttpResponse packetGet(HttpResponseStatus status, byte[] data, String type, int length) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        response.headers().set("Content-Type", type);
        response.headers().set("Content-Length", length);
        response.content().writeBytes(data);
        return response;
    }

    /**
     * 封装POST方法的Response
     */
    private FullHttpResponse packetPost(HttpResponseStatus status, String data, String type, String length) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        response.headers().set("Content-Type", type);
        response.headers().set("Content-Length", length);
        response.content().writeCharSequence(data, StandardCharsets.UTF_8);
        return response;
    }
}
