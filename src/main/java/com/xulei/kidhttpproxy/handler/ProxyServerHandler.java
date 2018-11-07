package com.xulei.kidhttpproxy.handler;

import com.xulei.kidhttpproxy.config.ProxyConfig;
import com.xulei.kidhttpproxy.factory.BootstrapFactory;
import com.xulei.kidhttpproxy.listener.HttpChannelFutureListener;
import com.xulei.kidhttpproxy.listener.HttpsChannelFutureListener;
import com.xulei.kidhttpproxy.main.ProxyServer;
import com.xulei.kidhttpproxy.util.ChannelCacheUtil;
import com.xulei.kidhttpproxy.util.ProxyUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.Objects;

/**
 * @author lei.X
 * @date 2018/11/7
 */
@Slf4j
@Component
@ChannelHandler.Sharable
public class ProxyServerHandler extends ChannelInboundHandlerAdapter{

    private static final String LOG_PRE = "【代理服务器handler】通道id:{}";

    @Autowired
    private final ProxyConfig proxyConfig;

    @Autowired
    private final BootstrapFactory bootstrapFactory;

    public ProxyServerHandler(ProxyConfig proxyConfig,BootstrapFactory bootstrapFactory){
        this.proxyConfig = proxyConfig;
        this.bootstrapFactory = bootstrapFactory;
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        log.info(LOG_PRE + ",客户端关闭连接.", ProxyUtil.getChannelId(ctx));
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info(LOG_PRE + ",通道未激活.", ProxyUtil.getChannelId(ctx));
        ctx.close();
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        log.info(LOG_PRE + "读取完成.", ProxyUtil.getChannelId(ctx));
    }

    /**
     * 异常处理
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error(LOG_PRE + ",发生异常:{}", ProxyUtil.getChannelId(ctx), cause.getMessage(), cause);
        //关闭
        ctx.close();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx,Object msg) throws InterruptedException {
        String channelId = ProxyUtil.getChannelId(ctx);

        try {

            //HTTP/HTTPS : 如果是 http报文格式的,此时已经被编码解码器转为了该类,如果不是,则表示是https协议建立第一次连接后后续的请求等.
            if (msg instanceof FullHttpRequest){
               final FullHttpRequest request = (FullHttpRequest)msg;

               InetSocketAddress address = ProxyUtil.getAddressByRequest(request);

               //method 为CONNECT则说明为https
               if (HttpMethod.CONNECT.equals(request.method())){
                   log.info(LOG_PRE+",https requests，target url:{}",channelId,request.uri());

                   //存入缓存
                   ChannelCacheUtil.put(channelId,new ChannelCache(address,connect(false,address,ctx,msg)));
                   //给客户端响应成功信息 HTTP/1.1 200 Connection Established  .失败时直接退出
                   //此处没有添加Connection Established,似乎也没问题
                   if (!ProxyUtil.writeAndFlush(ctx, new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), true))
                       return;

                   //此处将用于报文编码解码的处理器去除,因为后面上方的信息都是加密过的,不符合一般报文格式,我们直接转发即可
                   ctx.pipeline().remove(ProxyServer.NAME_HTTP_ENCODE_HANDLER1);
                   ctx.pipeline().remove(ProxyServer.NAME_HTTP_DECODE_HANDLER);
                   ctx.pipeline().remove(ProxyServer.NAME_HTTP_AGGREGATOR_HANDLER);

                   //此时 客户端已经和目标服务器 建立连接(其实是 客户端 -> 代理服务器 -> 目标服务器),
                   //直接退出等待下一次双方连接即可.
                   return;
               }

               //Http
                log.info(LOG_PRE,",http request, target url:{}",channelId,request.uri());
                HttpHeaders headers = request.headers();
                headers.add("Connection",headers.get("Proxy-Connection"));
                headers.remove("Proxy-Connection");

                connect(true,address,ctx,msg);
                return;

            }

            //其他格式数据(建立https connect后的客户端再次发送的加密数据):
            //从缓存获取到数据
            ChannelCache cache = ChannelCacheUtil.get(ProxyUtil.getChannelId(ctx));
            //如果缓存为空,应该是缓存已经过期,直接返回客户端请求超时,并关闭连接
            if (Objects.isNull(cache)) {
                log.info(LOG_PRE + ",缓存过期", channelId);
                ProxyUtil.writeAndFlush(ctx, new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.REQUEST_TIMEOUT), false);
                ctx.close();
                return;
            }

            //此处,表示https协议的请求第x次访问(x > 2; 第一次我们响应200,第二次同目标主机建立连接, 此处直接发送消息即可)
            //如果此时通道是可写的,写入消息
            boolean flag = false;
            log.info(LOG_PRE+",https,正在向目标发送后续消息，",channelId);
            for (int i = 0; i < 100; i++) {
                if ((flag = cache.getChannelFuture().channel().isActive()))
                    break;
                Thread.sleep(10);
            }

            if (flag){
                cache.getChannelFuture().channel().writeAndFlush(msg).addListener((ChannelFutureListener)future ->{
                    if (future.isSuccess())
                        log.info("通道id:{},https,向目标发送后续消息成功.", channelId);
                    else
                        log.info("通道id:{},https,向目标发送后续消息失败.e:{}", channelId, future.cause());

                });

                return;

            }

            log.info(LOG_PRE + ",https,与目标通道不可写,关闭与客户端连接", channelId);
            ProxyUtil.responseFailedToClient(ctx);

        }catch (Exception e){
            log.info(LOG_PRE + "error:{}", channelId, e.getMessage(), e);
        }
    }




    /**
     * 和 目标主机 建立连接
     */
    private ChannelFuture connect(boolean isHttp,InetSocketAddress address,ChannelHandlerContext ctx,Object msg){
        Bootstrap bootstrap = bootstrapFactory.build();
        if (isHttp){
            return bootstrap.handler(new HttpConnectHandler(ctx))
                    .connect(address)
                    .addListener(new HttpChannelFutureListener(msg,ctx));
        }
        //如果为https请求
        return bootstrap.handler(new HttpConnectHandler(ctx))
                .connect(address)
                .addListener(new HttpsChannelFutureListener(msg,ctx));
    }




    /**
     * 用于存储每个通道各自信息的缓存类
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Accessors(chain = true)
    public class ChannelCache {
        //目标服务器的地址
        private InetSocketAddress address;
        //当前请求与目标主机建立的连接通道
        private ChannelFuture channelFuture;
    }
}
