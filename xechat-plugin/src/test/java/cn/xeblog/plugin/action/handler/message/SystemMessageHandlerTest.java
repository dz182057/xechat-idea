package cn.xeblog.plugin.action.handler.message;

import cn.xeblog.plugin.action.ConnectionAction;
import cn.xeblog.plugin.cache.DataCache;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class SystemMessageHandlerTest {

    @After
    public void tearDown() {
        DataCache.autoReconnectEnabled = false;
        DataCache.connectionAction = null;
        DataCache.reconnected = false;
        DataCache.loginFromReconnect = false;
    }

    @Test
    public void samePlatformReplacementMessageStopsAutoReconnect() {
        DataCache.autoReconnectEnabled = true;
        DataCache.connectionAction = new ConnectionAction();

        boolean handled = SystemMessageHandler.stopReconnectIfSamePlatformReplacement(
                "该账号已在当前端其他位置登录,当前连接已下线");

        Assert.assertTrue(handled);
        Assert.assertFalse(DataCache.autoReconnectEnabled);
        Assert.assertFalse(DataCache.reconnected);
        Assert.assertFalse(DataCache.loginFromReconnect);
    }

    @Test
    public void normalSystemMessageKeepsAutoReconnectSetting() {
        DataCache.autoReconnectEnabled = true;
        DataCache.connectionAction = new ConnectionAction();

        boolean handled = SystemMessageHandler.stopReconnectIfSamePlatformReplacement("服务器维护提示");

        Assert.assertFalse(handled);
        Assert.assertTrue(DataCache.autoReconnectEnabled);
    }
}
