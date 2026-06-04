package cn.xeblog.plugin.action;

import cn.xeblog.plugin.cache.DataCache;
import io.netty.channel.Channel;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;

public class LoginServiceTest {

    @Before
    public void setUp() throws Exception {
        resetState();
    }

    @After
    public void tearDown() throws Exception {
        resetState();
    }

    @Test
    public void shortPasswordDoesNotLeaveConnectingState() {
        RecordingCallback cb = new RecordingCallback();

        LoginService.loginByPassword("test_account", "short", "127.0.0.1", 1025, cb);

        Assert.assertEquals("密码至少 8 位", cb.failedReason);
        Assert.assertFalse("本地校验失败后不应继续占用连接状态", LoginService.isConnecting());
    }

    private static void resetState() throws Exception {
        DataCache.isOnline = false;
        DataCache.account = null;
        DataCache.password = null;
        DataCache.username = null;
        setConnecting(false);
    }

    private static void setConnecting(boolean value) throws Exception {
        Field field = LoginService.class.getDeclaredField("CONNECTING");
        field.setAccessible(true);
        field.set(null, value);
    }

    private static class RecordingCallback implements LoginService.Callback {
        private String failedReason;

        @Override
        public void onConnecting() {
        }

        @Override
        public void onConnected(Channel channel) {
        }

        @Override
        public void onFailed(String reason) {
            failedReason = reason;
        }
    }
}
