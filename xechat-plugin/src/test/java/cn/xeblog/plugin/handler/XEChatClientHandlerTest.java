package cn.xeblog.plugin.handler;

import cn.xeblog.plugin.cache.DataCache;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.ConcurrentHashMap;

public class XEChatClientHandlerTest {

    @After
    public void tearDown() {
        DataCache.isOnline = false;
        DataCache.channel = null;
        DataCache.account = null;
        DataCache.password = null;
        DataCache.username = null;
        DataCache.uuid = null;
        DataCache.reconnected = false;
        DataCache.userMap = new ConcurrentHashMap<>();
    }

    @Test
    public void tcpConnectDoesNotMarkAccountOnlineBeforeLoginResult() {
        DataCache.isOnline = false;
        DataCache.account = "test_account";
        DataCache.password = "password123";
        DataCache.uuid = "uuid";
        DataCache.reconnected = true;

        EmbeddedChannel channel = new EmbeddedChannel(new XEChatClientHandler());

        Assert.assertFalse("收到 LOGIN_RESULT 前不应进入已登录状态", DataCache.isOnline);

        channel.finishAndReleaseAll();
    }
}
