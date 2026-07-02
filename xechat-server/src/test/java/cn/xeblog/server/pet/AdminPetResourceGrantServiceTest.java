package cn.xeblog.server.pet;

import cn.xeblog.commons.entity.pet.AdminGrantPetResourceDTO;
import cn.xeblog.commons.entity.pet.AdminPetResourceGrantResultDTO;
import cn.xeblog.server.account.AccountException;
import cn.xeblog.server.account.AccountService;
import cn.xeblog.server.account.DbInitializer;
import cn.xeblog.server.account.entity.Account;
import cn.xeblog.server.config.GlobalConfig;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class AdminPetResourceGrantServiceTest {

    private static final String PASSWORD = "abc12345";

    @Before
    public void setUp() throws Exception {
        Path root = Files.createTempDirectory("xechat-admin-pet-grant-test");
        System.setProperty(GlobalConfig.DATA_PATH_PROPERTY, root.toString());
        GlobalConfig.initDataPath(root.toString());
        resetDbFactory();
        DbInitializer.initIfNeeded();
    }

    @After
    public void tearDown() throws Exception {
        resetDbFactory();
        System.clearProperty(GlobalConfig.DATA_PATH_PROPERTY);
        GlobalConfig.initDataPath(null);
    }

    @Test
    public void grantBonesShouldCreateAssetsAndAddAmount() {
        Account target = registerUser("bones");

        AdminPetResourceGrantResultDTO result = AdminPetResourceGrantService.grant(1L,
                new AdminGrantPetResourceDTO(target.getAccountId(),
                        AdminPetResourceGrantService.TYPE_BONES, null, 120, "补偿"));

        Assert.assertEquals(Integer.valueOf(300), result.getBeforeAmount());
        Assert.assertEquals(Integer.valueOf(420), result.getAfterAmount());
        Assert.assertEquals(420, PetProfileService.profile(target.getAccountId()).getAssets().getBones());
    }

    @Test
    public void grantItemShouldBypassNormalItemLimitAndRecordLedger() throws Exception {
        Account target = registerUser("item");
        insertPetItem(target.getAccountId(), "item_gomoku_guard", 9);

        AdminPetResourceGrantResultDTO result = AdminPetResourceGrantService.grant(99L,
                new AdminGrantPetResourceDTO(target.getAccountId(),
                        AdminPetResourceGrantService.TYPE_ITEM, "item_gomoku_guard", 10, "压测"));

        Assert.assertEquals(Integer.valueOf(9), result.getBeforeAmount());
        Assert.assertEquals(Integer.valueOf(19), result.getAfterAmount());
        Assert.assertEquals(19, countItem(target.getAccountId(), "item_gomoku_guard"));
        Assert.assertEquals(1, countItemLedger(target.getAccountId(), "item_gomoku_guard",
                "gain", "admin_manual_grant"));
    }

    @Test
    public void grantSkinShouldOnlyAllowSkinAndKeepSingleOwnership() throws Exception {
        Account target = registerUser("skin");

        AdminPetResourceGrantResultDTO result = AdminPetResourceGrantService.grant(99L,
                new AdminGrantPetResourceDTO(target.getAccountId(),
                        AdminPetResourceGrantService.TYPE_SKIN, "item_gomoku_skin_magic", 1, "补皮肤"));

        Assert.assertEquals(Integer.valueOf(0), result.getBeforeAmount());
        Assert.assertEquals(Integer.valueOf(1), result.getAfterAmount());
        Assert.assertEquals(1, countItem(target.getAccountId(), "item_gomoku_skin_magic"));
        Assert.assertEquals(1, countItemLedger(target.getAccountId(), "item_gomoku_skin_magic",
                "gain", "admin_manual_grant"));

        try {
            AdminPetResourceGrantService.grant(99L,
                    new AdminGrantPetResourceDTO(target.getAccountId(),
                            AdminPetResourceGrantService.TYPE_SKIN, "item_gomoku_skin_magic", 1, null));
        } catch (AccountException e) {
            Assert.assertEquals("该皮肤已拥有", e.getMessage());
            try {
                AdminPetResourceGrantService.grant(99L,
                        new AdminGrantPetResourceDTO(target.getAccountId(),
                                AdminPetResourceGrantService.TYPE_SKIN, "item_gomoku_guard", 1, null));
            } catch (AccountException invalidItemError) {
                Assert.assertEquals("皮肤 ID 不存在", invalidItemError.getMessage());
                return;
            }
            throw new AssertionError("非皮肤道具不应按皮肤发放成功");
        }
        throw new AssertionError("已拥有的皮肤不应重复发放成功");
    }

    @Test
    public void grantCollectionShouldDiscoverAndIncreaseCount() throws Exception {
        Account target = registerUser("collection");

        AdminPetResourceGrantService.grant(1L,
                new AdminGrantPetResourceDTO(target.getAccountId(),
                        AdminPetResourceGrantService.TYPE_COLLECTION, "old_library_key", 2, null));

        Assert.assertEquals(2, countCollection(target.getAccountId(), "old_library_key"));
    }

    @Test
    public void grantUnknownItemShouldFail() {
        Account target = registerUser("baditem");

        try {
            AdminPetResourceGrantService.grant(1L,
                    new AdminGrantPetResourceDTO(target.getAccountId(),
                            AdminPetResourceGrantService.TYPE_ITEM, "item_missing", 1, null));
        } catch (AccountException e) {
            Assert.assertEquals("道具 ID 不存在", e.getMessage());
            return;
        }
        throw new AssertionError("不存在的道具不应发放成功");
    }

    @Test
    public void grantDeletedAccountShouldFail() {
        Account target = registerUser("deleted");
        AccountService.deleteByAdmin(target.getAccountId());

        try {
            AdminPetResourceGrantService.grant(1L,
                    new AdminGrantPetResourceDTO(target.getAccountId(),
                            AdminPetResourceGrantService.TYPE_FOOD, null, 1, null));
        } catch (AccountException e) {
            Assert.assertEquals("已删除账号不能发放资源", e.getMessage());
            return;
        }
        throw new AssertionError("已删除账号不应发放成功");
    }

    private static Account registerUser(String prefix) {
        String suffix = Long.toString(System.nanoTime(), 36);
        String accountPrefix = prefix.length() > 6 ? prefix.substring(0, 6) : prefix;
        String nickname = "发" + suffix.substring(0, Math.min(8, suffix.length()));
        return AccountService.register(accountPrefix + "_" + suffix, PASSWORD, nickname,
                Account.ROLE_USER, "127.0.0.1", null, null, null);
    }

    private static void insertPetItem(long accountId, String itemId, int count) throws Exception {
        long now = System.currentTimeMillis();
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             PreparedStatement statement = session.getConnection().prepareStatement(
                     "INSERT INTO pet_items (account_id, item_id, count, updated_at) VALUES (?, ?, ?, ?)")) {
            statement.setLong(1, accountId);
            statement.setString(2, itemId);
            statement.setInt(3, count);
            statement.setLong(4, now);
            statement.executeUpdate();
        }
    }

    private static int countItem(long accountId, String itemId) throws Exception {
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             PreparedStatement statement = session.getConnection().prepareStatement(
                     "SELECT count FROM pet_items WHERE account_id = ? AND item_id = ?")) {
            statement.setLong(1, accountId);
            statement.setString(2, itemId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    private static int countCollection(long accountId, String itemId) throws Exception {
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             PreparedStatement statement = session.getConnection().prepareStatement(
                     "SELECT count FROM pet_collections WHERE account_id = ? AND item_id = ?")) {
            statement.setLong(1, accountId);
            statement.setString(2, itemId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    private static int countItemLedger(long accountId, String itemId, String direction, String source) throws Exception {
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             PreparedStatement statement = session.getConnection().prepareStatement(
                     "SELECT COUNT(1) FROM pet_item_ledger " +
                             "WHERE account_id = ? AND item_id = ? AND direction = ? AND source = ?")) {
            statement.setLong(1, accountId);
            statement.setString(2, itemId);
            statement.setString(3, direction);
            statement.setString(4, source);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static void resetDbFactory() throws Exception {
        Field factory = DbInitializer.class.getDeclaredField("FACTORY");
        factory.setAccessible(true);
        factory.set(null, null);
    }

}
