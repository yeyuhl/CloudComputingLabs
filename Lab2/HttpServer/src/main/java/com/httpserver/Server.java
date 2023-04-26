package com.httpserver;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;

/**
 * @author yeyuhl
 * @date 2023/4/24
 */
public class Server {
    public static void main(String[] args) {
        String ip=args[1],port=args[3],threads=args[5];
        int threadNum=Integer.parseInt(threads);
        // 使用NioEventLoopGroup实现BossGroup和WorkerGroup
        EventLoopGroup bossGroup = new NioEventLoopGroup(threadNum/4), workGroup = new NioEventLoopGroup(threadNum/2);
        // 创建服务端启动引导类
        ServerBootstrap bootstrap = new ServerBootstrap();
        // 指定事件循环组
        bootstrap.group(bossGroup, workGroup)
                // 指定为NIO的ServerSocketChannel
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        // 获取流水线，当我们需要处理客户端的数据时，实际上是像流水线一样在处理，这个流水线上可以有很多Handler
                        socketChannel.pipeline()
                                .addLast(new HttpRequestDecoder())
                                // 聚合器，将内容聚合为一个FullHttpRequest，参数是最大内容长度
                                .addLast(new HttpObjectAggregator(Integer.MAX_VALUE))
                                .addLast(new ChannelInboundHandlerAdapter() {
                                    // channelHandlerContext是上下文，msg是收到的消息，默认以ByteBuf形式
                                    @Override
                                    public void channelRead(ChannelHandlerContext channelHandlerContext, Object msg) throws Exception {
                                        FullHttpRequest request = (FullHttpRequest) msg;
                                        MyResponse myResponse = MyResponse.getInstance();
                                        channelHandlerContext.channel().writeAndFlush(myResponse.resolveResource(request));
                                        channelHandlerContext.channel().close();
                                    }
                                })
                                .addLast(new HttpResponseEncoder());
                    }
                });
        // 绑定8080端口
        bootstrap.bind(Integer.parseInt(port));
    }

}
