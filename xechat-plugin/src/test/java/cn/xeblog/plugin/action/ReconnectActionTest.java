package cn.xeblog.plugin.action;

import cn.xeblog.plugin.cache.DataCache;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ReconnectActionTest {

    @Before
    public void setUp() {
        resetDataCache();
    }

    @After
    public void tearDown() {
        resetDataCache();
    }

    @Test
    public void nextDelayUsesExponentialBackoffWithMaxDelay() {
        ReconnectAction.State state = new ReconnectAction.State();

        Assert.assertEquals(1000L, state.nextDelayMillis());
        Assert.assertEquals(2000L, state.nextDelayMillis());
        Assert.assertEquals(4000L, state.nextDelayMillis());
        Assert.assertEquals(8000L, state.nextDelayMillis());
        Assert.assertEquals(16000L, state.nextDelayMillis());
        Assert.assertEquals(30000L, state.nextDelayMillis());
        Assert.assertEquals(30000L, state.nextDelayMillis());
    }

    @Test
    public void onlyOneReconnectCanBeScheduledAtATime() {
        ReconnectAction.State state = new ReconnectAction.State();

        Assert.assertTrue(state.markScheduling());
        Assert.assertFalse(state.markScheduling());

        state.markIdle();

        Assert.assertTrue(state.markScheduling());
    }

    @Test
    public void shouldReconnectRequiresEnabledAndConnectionAction() {
        DataCache.autoReconnectEnabled = true;
        DataCache.connectionAction = null;

        Assert.assertFalse(ReconnectAction.shouldReconnect());

        DataCache.connectionAction = new ConnectionAction();

        Assert.assertTrue(ReconnectAction.shouldReconnect());
    }

    private static void resetDataCache() {
        DataCache.autoReconnectEnabled = false;
        DataCache.connectionAction = null;
        DataCache.reconnected = false;
        DataCache.loginFromReconnect = false;
        ReconnectAction.reset();
    }
}
