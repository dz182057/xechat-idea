package cn.xeblog.plugin.action.handler.command;

import cn.xeblog.plugin.action.ConnectionAction;
import cn.xeblog.plugin.action.ReconnectAction;
import cn.xeblog.plugin.cache.DataCache;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class LogoutCommandHandlerTest {

    @Before
    public void setUp() {
        resetDataCache();
    }

    @After
    public void tearDown() {
        resetDataCache();
    }

    @Test
    public void logoutCommandCanRunWhileOfflineToCancelReconnect() {
        DataCache.isOnline = false;
        DataCache.autoReconnectEnabled = true;
        DataCache.connectionAction = new ConnectionAction();

        Assert.assertTrue(new LogoutCommandHandler().check(new String[0]));
    }

    private static void resetDataCache() {
        DataCache.isOnline = false;
        DataCache.autoReconnectEnabled = false;
        DataCache.connectionAction = null;
        DataCache.reconnected = false;
        DataCache.loginFromReconnect = false;
        ReconnectAction.reset();
    }
}
