package com.xulei.kidhttpproxy.initializer;

import com.xulei.kidhttpproxy.config.ProxyConfig;
import com.xulei.kidhttpproxy.handler.HttpConnectHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


/**
 * @author lei.X
 * @date 2018/11/7
 */
@Component
@NoArgsConstructor
@Slf4j
public class HttpConnectChannelInitializer extends ChannelInitializer<SocketChannel> {

    /**
     * 与客户端连接的处理器(ProxyServerHandler)中的ctx,
     * 用于将目标主机响应的消息 发送回 客户端
     *
     * 此处将其传给http连接对应的处理器类
     */
    private ChannelHandlerContext ctx;

    private static ProxyConfig proxyConfig;

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ch.pipeline()
                .addLast(new HttpClientCodec())
                .addLast(new HttpObjectAggregator(proxyConfig.getSocket().getMaxContentLength()))
                .addLast(new HttpConnectHandler(ctx));
    }

    @Autowired
    public void init(ProxyConfig proxyConfig){
        HttpConnectChannelInitializer.proxyConfig = proxyConfig;
    }
}
