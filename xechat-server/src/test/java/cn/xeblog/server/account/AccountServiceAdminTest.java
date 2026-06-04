package cn.xeblog.server.account;

import cn.xeblog.server.account.entity.Account;
import cn.xeblog.server.account.entity.SessionEntity;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class AccountServiceAdminTest {

    private static final String PASSWORD = "abc12345";

    @BeforeClass
    public static void setUpDataDir() throws Exception {
        Path home = Files.createTempDirectory("xechat-account-admin-test");
        System.setProperty("user.home", home.toString());
    }

    @Test
    public void adminDeleteShouldReleaseOriginalAccountAndNickname() {
        String suffix = uniqueSuffix();
        String account = "del_" + suffix;
        String nickname = "删_" + suffix;
        Account original = AccountService.register(account, PASSWORD, nickname,
                Account.ROLE_USER, "127.0.0.1", null, null, null);

        AccountService.deleteByAdmin(original.getAccountId());

        Account deleted = AccountService.findById(original.getAccountId());
        assertNotNull(deleted);
        assertEquals(Account.STATUS_DELETED, deleted.getStatus());
        assertEquals("deleted_" + original.getAccountId(), deleted.getAccount());
        assertEquals("已删除用户_" + original.getAccountId(), deleted.getNickname());

        Account reused = AccountService.register(account, PASSWORD, nickname,
                Account.ROLE_USER, "127.0.0.1", null, null, null);
        assertNotNull(reused);
        assertEquals(account, reused.getAccount());
        assertEquals(nickname, reused.getNickname());
    }

    @Test
    public void selfSoftDeleteShouldReleaseOriginalAccountAndNickname() {
        String suffix = uniqueSuffix();
        String account = "self_" + suffix;
        String nickname = "自_" + suffix;
        Account original = AccountService.register(account, PASSWORD, nickname,
                Account.ROLE_USER, "127.0.0.1", null, null, null);

        AccountService.softDelete(original.getAccountId());

        Account deleted = AccountService.findById(original.getAccountId());
        assertNotNull(deleted);
        assertEquals(Account.STATUS_DELETED, deleted.getStatus());
        assertEquals("deleted_" + original.getAccountId(), deleted.getAccount());
        assertEquals("已删除用户_" + original.getAccountId(), deleted.getNickname());

        Account reused = AccountService.register(account, PASSWORD, nickname,
                Account.ROLE_USER, "127.0.0.1", null, null, null);
        assertNotNull(reused);
        assertEquals(account, reused.getAccount());
        assertEquals(nickname, reused.getNickname());
    }

    @Test
    public void adminDeleteShouldReleaseLegacyDeletedAccount() {
        String suffix = uniqueSuffix();
        String account = "old_" + suffix;
        String nickname = "旧_" + suffix;
        Account original = AccountService.register(account, PASSWORD, nickname,
                Account.ROLE_USER, "127.0.0.1", null, null, null);
        legacySoftDelete(original.getAccountId());

        AccountService.deleteByAdmin(original.getAccountId());

        Account deleted = AccountService.findById(original.getAccountId());
        assertNotNull(deleted);
        assertEquals(Account.STATUS_DELETED, deleted.getStatus());
        assertEquals("deleted_" + original.getAccountId(), deleted.getAccount());
        assertEquals("已删除用户_" + original.getAccountId(), deleted.getNickname());

        Account reused = AccountService.register(account, PASSWORD, nickname,
                Account.ROLE_USER, "127.0.0.1", null, null, null);
        assertNotNull(reused);
    }

    @Test
    public void frozenAccountShouldRejectLoginAndRevokeSessions() {
        String suffix = uniqueSuffix();
        String account = "fro_" + suffix;
        Account target = AccountService.register(account, PASSWORD, "冻_" + suffix,
                Account.ROLE_USER, "127.0.0.1", null, null, null);
        SessionEntity session = SessionService.createToken(target.getAccountId(),
                "DESKTOP", "client-" + suffix, "127.0.0.1");

        AccountService.setStatusByAdmin(target.getAccountId(), Account.STATUS_FROZEN);

        Account frozen = AccountService.findById(target.getAccountId());
        assertEquals(Account.STATUS_FROZEN, frozen.getStatus());
        assertNull(SessionService.validateAndTouch(session.getToken()));

        try {
            AccountService.login(account, PASSWORD, "127.0.0.1");
        } catch (AccountException e) {
            assertEquals("账号已被冻结", e.getMessage());
            return;
        }
        throw new AssertionError("被冻结账号不应允许登录");
    }

    @Test
    public void activeStatusShouldAllowFrozenAccountToLoginAgain() {
        String suffix = uniqueSuffix();
        String account = "act_" + suffix;
        Account target = AccountService.register(account, PASSWORD, "启_" + suffix,
                Account.ROLE_USER, "127.0.0.1", null, null, null);

        AccountService.setStatusByAdmin(target.getAccountId(), Account.STATUS_FROZEN);
        AccountService.setStatusByAdmin(target.getAccountId(), Account.STATUS_ACTIVE);

        Account loggedIn = AccountService.login(account, PASSWORD, "127.0.0.1");
        assertEquals(target.getAccountId(), loggedIn.getAccountId());
        assertEquals(Account.STATUS_ACTIVE, loggedIn.getStatus());
    }

    @Test
    public void deletedAccountShouldNotBeReactivated() {
        String suffix = uniqueSuffix();
        Account target = AccountService.register("gone_" + suffix, PASSWORD, "没_" + suffix,
                Account.ROLE_USER, "127.0.0.1", null, null, null);
        AccountService.deleteByAdmin(target.getAccountId());

        try {
            AccountService.setStatusByAdmin(target.getAccountId(), Account.STATUS_ACTIVE);
        } catch (AccountException e) {
            assertEquals("已删除账号不能变更状态", e.getMessage());
            return;
        }
        throw new AssertionError("已删除账号不应允许恢复状态");
    }

    private static String uniqueSuffix() {
        return Long.toString(System.nanoTime(), 36);
    }

    private static void legacySoftDelete(long accountId) {
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true)) {
            session.getMapper(cn.xeblog.server.account.mapper.AccountMapper.class)
                    .softDelete(accountId, System.currentTimeMillis());
        }
    }
}
