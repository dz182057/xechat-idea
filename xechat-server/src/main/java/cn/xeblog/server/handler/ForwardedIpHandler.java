package cn.xeblog.server.handler;

import cn.xeblog.server.util.IpUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;

public class ForwardedIpHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpRequest) {
            IpUtil.rememberForwardedIp(ctx.channel(), ((FullHttpRequest) msg).headers());
        }
        super.channelRead(ctx, msg);
    }

}
