package com.xulei.kidhttpproxy.main;

import com.xulei.kidhttpproxy.config.ProxyConfig;
import com.xulei.kidhttpproxy.handler.ProxyServerHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author lei.X
 * @date 2018/11/7
 */

@Component
@Slf4j
public class ProxyServer {

    //静态参数-netty处理器的名字,用于在https请求时,剔除channel中绑定的编解码相关处理类,因为https请求无法解析其加密的数据
    public static final String NAME_HTTP_DECODE_HANDLER = "httpCode_encode";
    public static final String NAME_HTTP_ENCODE_HANDLER1 = "httpCode_decode";
    public static final String NAME_HTTP_AGGREGATOR_HANDLER = "httpAggregator";
    public static final String NAME_PROXY_SERVER_HANDLER = "proxyServerHandler";

    @Autowired
    private final ProxyConfig proxyConfig;

    @Autowired
    private final ProxyServerHandler proxyServerHandler;

    public ProxyServer(ProxyConfig proxyConfig,ProxyServerHandler proxyServerHandler) {
        this.proxyConfig = proxyConfig;
        this.proxyServerHandler = proxyServerHandler;

    }

    @SneakyThrows
    public void start(){
        //1 用于接收Client的连接 的线程组
        EventLoopGroup bossGroup = new NioEventLoopGroup(proxyConfig.getSocket().getClientThreadNum());
        // 实际操作业务的线程组
        EventLoopGroup workerGroup = new NioEventLoopGroup(proxyConfig.getSocket().getEventThreadNum());
        //辅助类BootStrap
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(bossGroup,workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline()
                                .addLast(NAME_HTTP_DECODE_HANDLER,new HttpRequestDecoder())
                                .addLast(NAME_HTTP_ENCODE_HANDLER1,new HttpResponseEncoder())
                                .addLast(NAME_HTTP_AGGREGATOR_HANDLER,new HttpObjectAggregator(65536))
                                .addLast(NAME_PROXY_SERVER_HANDLER, proxyServerHandler);

                    }
                })
                //服务器端接受的队列长度
                .option(ChannelOption.SO_BACKLOG,2048)
        //保持连接,类似心跳检测,超过2小时空闲才激活
//                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,proxyConfig.getSocket().getConnectTimeoutMillis())
                .option(ChannelOption.SO_RCVBUF,128*1024);
        log.info("代理服务器启动，在{}端口",proxyConfig.getSocket().getProxyPort());
        //绑定端口，进行监听，这里可以开启多个端口监听
        ChannelFuture future = serverBootstrap.bind(proxyConfig.getSocket().getProxyPort()).sync();
        //关闭前阻塞
        future.channel().closeFuture().sync();
        //关闭线程组
        bossGroup.shutdownGracefully().sync();
        workerGroup.shutdownGracefully().sync();

    }


}
