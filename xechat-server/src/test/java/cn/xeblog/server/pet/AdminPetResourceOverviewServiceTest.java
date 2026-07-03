package cn.xeblog.server.pet;

import cn.xeblog.commons.entity.react.request.AdminReact;
import cn.xeblog.commons.entity.react.result.AdminPetResourceOverviewDTO;
import cn.xeblog.commons.entity.react.result.AdminReactResult;
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

public class AdminPetResourceOverviewServiceTest {

    private static final String PASSWORD = "abc12345";

    @Before
    public void setUp() throws Exception {
        Path root = Files.createTempDirectory("xechat-admin-pet-overview-test");
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
    public void queryShouldReturnPetResourceOverview() throws Exception {
        Account target = registerUser("overview");
        long now = System.currentTimeMillis();
        insertAssets(target.getAccountId(), now);
        insertDog(target.getAccountId(), "dog-1", "小灰", "shiba", "adult", "exploring", now);
        insertDog(target.getAccountId(), "dog-2", "小白", "samoyed", "puppy", "idle", now);
        insertItem(target.getAccountId(), "item_gomoku_guard", 3, now);
        insertItem(target.getAccountId(), "item_gomoku_skin_magic", 1, now);
        insertCollection(target.getAccountId(), "old_library_key", 2, now);

        AdminReact query = new AdminReact();
        query.setAccount("overview");
        query.setPage(1);
        query.setPageSize(10);

        AdminReactResult result = AdminPetResourceOverviewService.query(query);

        Assert.assertEquals(1L, result.getTotal());
        Assert.assertEquals(1, result.getRecords().size());
        AdminPetResourceOverviewDTO row = (AdminPetResourceOverviewDTO) result.getRecords().get(0);
        Assert.assertEquals(Long.valueOf(target.getAccountId()), row.getAccountId());
        Assert.assertTrue(row.isHasPetHome());
        Assert.assertEquals(Integer.valueOf(888), row.getBones());
        Assert.assertEquals(Integer.valueOf(12), row.getFood());
        Assert.assertEquals(Integer.valueOf(2), row.getDogCount());
        Assert.assertEquals(Integer.valueOf(1), row.getExploringDogCount());
        Assert.assertTrue(row.getDogSummary().contains("小灰:shiba:adult:exploring"));
        Assert.assertEquals(Integer.valueOf(1), row.getItemKindCount());
        Assert.assertEquals(Integer.valueOf(3), row.getItemTotalCount());
        Assert.assertTrue(row.getItemSummary().contains("item_gomoku_guard:3"));
        Assert.assertEquals(Integer.valueOf(1), row.getSkinKindCount());
        Assert.assertEquals(Integer.valueOf(1), row.getSkinTotalCount());
        Assert.assertTrue(row.getSkinSummary().contains("item_gomoku_skin_magic:1"));
        Assert.assertEquals(Integer.valueOf(1), row.getCollectionKindCount());
        Assert.assertEquals(Integer.valueOf(2), row.getCollectionTotalCount());
        Assert.assertTrue(row.getCollectionSummary().contains("old_library_key:2"));
    }

    private static Account registerUser(String prefix) {
        String suffix = Long.toString(System.nanoTime(), 36);
        String nickname = "资" + suffix.substring(0, Math.min(8, suffix.length()));
        return AccountService.register(prefix + "_" + suffix, PASSWORD, nickname,
                Account.ROLE_USER, "127.0.0.1", null, null, null);
    }

    private static void insertAssets(long accountId, long now) throws Exception {
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             PreparedStatement statement = session.getConnection().prepareStatement(
                     "INSERT INTO pet_assets (account_id, bones, food, makeup_cards, dog_slots, energy, " +
                             "energy_date, energy_limit, companion_dog_id, created_at, updated_at) " +
                             "VALUES (?, 888, 12, 4, 3, 7, '2026-07-03', 15, 'dog-1', ?, ?)")) {
            statement.setLong(1, accountId);
            statement.setLong(2, now);
            statement.setLong(3, now);
            statement.executeUpdate();
        }
    }

    private static void insertDog(long accountId, String id, String name, String breed,
                                  String stage, String status, long now) throws Exception {
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             PreparedStatement statement = session.getConnection().prepareStatement(
                     "INSERT INTO dogs (id, owner_id, name, breed, stage, bond, status, race_count, " +
                             "race_first_count, weekly_points, created_at, updated_at) " +
                             "VALUES (?, ?, ?, ?, ?, 50, ?, 0, 0, 0, ?, ?)")) {
            statement.setString(1, id);
            statement.setLong(2, accountId);
            statement.setString(3, name);
            statement.setString(4, breed);
            statement.setString(5, stage);
            statement.setString(6, status);
            statement.setLong(7, now);
            statement.setLong(8, now);
            statement.executeUpdate();
        }
    }

    private static void insertItem(long accountId, String itemId, int count, long now) throws Exception {
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

    private static void insertCollection(long accountId, String itemId, int count, long now) throws Exception {
        try (org.apache.ibatis.session.SqlSession session = DbInitializer.factory().openSession(true);
             PreparedStatement statement = session.getConnection().prepareStatement(
                     "INSERT INTO pet_collections (account_id, item_id, count, discovered, updated_at) " +
                             "VALUES (?, ?, ?, 1, ?)")) {
            statement.setLong(1, accountId);
            statement.setString(2, itemId);
            statement.setInt(3, count);
            statement.setLong(4, now);
            statement.executeUpdate();
        }
    }

    private static void resetDbFactory() throws Exception {
        Field factory = DbInitializer.class.getDeclaredField("FACTORY");
        factory.setAccessible(true);
        factory.set(null, null);
    }

}
