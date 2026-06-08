package cn.xeblog.server.action.handler.account;

import org.junit.Test;

import static org.junit.Assert.assertFalse;

public class StealthSessionScopeTest {

    @Test
    public void loginSessionStartsVisible() {
        assertFalse("每次登录会话默认应关闭隐身", AccountLoginHelper.initialSessionStealth());
    }

}
