package cn.xeblog.plugin.action.handler.message;

import cn.xeblog.plugin.cache.DataCache;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class OnlineUsersMessageHandlerTest {

    @After
    public void tearDown() {
        DataCache.welcomeNoticeShown = false;
    }

    @Test
    public void welcomeNoticeOnlyShowsOncePerPluginSession() {
        Assert.assertTrue(OnlineUsersMessageHandler.shouldShowWelcomeNotice());
        Assert.assertFalse(OnlineUsersMessageHandler.shouldShowWelcomeNotice());
    }
}
