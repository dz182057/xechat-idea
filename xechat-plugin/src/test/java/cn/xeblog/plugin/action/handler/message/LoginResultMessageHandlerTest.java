package cn.xeblog.plugin.action.handler.message;

import cn.xeblog.plugin.cache.DataCache;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class LoginResultMessageHandlerTest {

    @After
    public void tearDown() {
        DataCache.loginFromReconnect = false;
    }

    @Test
    public void normalLoginPullsHistory() {
        DataCache.loginFromReconnect = false;

        Assert.assertTrue(LoginResultMessageHandler.shouldPullHistory());
    }

    @Test
    public void reconnectLoginDoesNotPullHistoryAgain() {
        DataCache.loginFromReconnect = true;

        Assert.assertFalse(LoginResultMessageHandler.shouldPullHistory());
    }
}
