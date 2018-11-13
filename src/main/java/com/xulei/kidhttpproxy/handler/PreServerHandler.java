package com.xulei.kidhttpproxy.handler;

import com.xulei.kidhttpproxy.util.ProxyUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author lei.X
 * @date 2018/11/11
 */
@Slf4j
public class PreServerHandler extends ChannelInboundHandlerAdapter {

    private static final String LOG_PRE = "【测试服务器handler】通道id:{}";

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws InterruptedException {
//        String channelId = ProxyUtil.getChannelId(ctx);
        ByteBuf bf = (ByteBuf)msg;
        byte[] byteArray = new byte[bf.readableBytes()];
        bf.readBytes(byteArray);
        log.info("请求的内容:{}" + new String(byteArray));
    }


}
