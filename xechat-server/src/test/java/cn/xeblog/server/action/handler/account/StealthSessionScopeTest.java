package cn.xeblog.server.action.handler.account;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.enums.Platform;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StealthSessionScopeTest {

    @Test
    public void loginSessionStartsVisible() {
        assertFalse("每次登录会话默认应关闭隐身", AccountLoginHelper.initialSessionStealth());
    }

    @Test
    public void sameAccountSamePlatformReplacementDoesNotNotifyOnlineState() {
        User user = new User();
        user.setAccountId(1001L);
        user.setPlatform(Platform.WEB);

        assertFalse("同账号同平台重连接管旧连接时不应重复广播上线",
                AccountLoginHelper.shouldNotifyOnlineState(user, true));
    }

    @Test
    public void newAccountPlatformNotifiesOnlineState() {
        User user = new User();
        user.setAccountId(1001L);
        user.setPlatform(Platform.DESKTOP);

        assertTrue("账号新增在线端时应广播上线",
                AccountLoginHelper.shouldNotifyOnlineState(user, false));
    }

}
