package cn.xeblog.server.handler;

import cn.xeblog.server.util.IpUtil;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ForwardedIpHandlerTest {

    @Test
    public void shouldRememberFirstForwardedIp() {
        EmbeddedChannel channel = new EmbeddedChannel(new ForwardedIpHandler());
        FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "/xechat");
        request.headers().set("X-Forwarded-For",
                "203.0.113.7, 127.0.0.1");

        assertTrue(channel.writeInbound(request));

        assertEquals("203.0.113.7", channel.attr(IpUtil.CLIENT_IP_ATTRIBUTE).get());
        channel.finishAndReleaseAll();
    }

    @Test
    public void shouldIgnoreBlankForwardedIp() {
        EmbeddedChannel channel = new EmbeddedChannel(new ForwardedIpHandler());
        FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "/xechat");
        request.headers().set("X-Forwarded-For", " , ");

        assertTrue(channel.writeInbound(request));

        assertFalse(channel.hasAttr(IpUtil.CLIENT_IP_ATTRIBUTE));
        channel.finishAndReleaseAll();
    }

}
