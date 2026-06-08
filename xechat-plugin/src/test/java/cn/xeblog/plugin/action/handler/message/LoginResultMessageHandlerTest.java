package cn.xeblog.plugin.action.handler.message;

import cn.xeblog.plugin.cache.DataCache;
import cn.xeblog.plugin.action.ConnectionAction;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class LoginResultMessageHandlerTest {

    @After
    public void tearDown() {
        DataCache.loginFromReconnect = false;
        DataCache.connectionAction = null;
        DataCache.publicHistoryServerKey = null;
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

    @Test
    public void buildsStableServerKeyFromCurrentConnection() {
        DataCache.connectionAction = new ConnectionAction("LOCALHOST", 7777, null);

        Assert.assertEquals("localhost:7777", LoginResultMessageHandler.currentServerKey());
    }

    @Test
    public void resetsPublicConsoleWhenServerChanges() {
        DataCache.publicHistoryServerKey = "localhost:7777";

        Assert.assertTrue(LoginResultMessageHandler.shouldResetPublicConsole("remote:8888"));
        Assert.assertFalse(LoginResultMessageHandler.shouldResetPublicConsole("localhost:7777"));
    }
}
