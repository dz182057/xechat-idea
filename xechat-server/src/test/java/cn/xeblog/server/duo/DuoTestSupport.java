package cn.xeblog.server.duo;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.enums.UserStatus;
import cn.xeblog.server.account.DbInitializer;
import cn.xeblog.server.cache.UserCache;
import cn.xeblog.server.config.GlobalConfig;
import io.netty.channel.embedded.EmbeddedChannel;
import org.apache.ibatis.session.SqlSession;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.ZoneId;

/** 双人小屋集成测试共用的数据库和账号夹具。 */
public final class DuoTestSupport {

    public static final long ACCOUNT_A = 1001L;
    public static final long ACCOUNT_B = 1002L;

    private DuoTestSupport() {
    }

    public static void setUpDatabase() throws Exception {
        Path root = Files.createTempDirectory("xechat-duo-test");
        System.setProperty(GlobalConfig.DATA_PATH_PROPERTY, root.toString());
        GlobalConfig.initDataPath(null);
        resetFactory();
        DbInitializer.initIfNeeded();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            Connection conn = session.getConnection();
            insertAccount(conn, ACCOUNT_A, "duo_user_a", "小屋甲");
            insertAccount(conn, ACCOUNT_B, "duo_user_b", "小屋乙");
            insertFriend(conn, ACCOUNT_A, ACCOUNT_B);
            insertFriend(conn, ACCOUNT_B, ACCOUNT_A);
            insertQuizQuestion(conn);
            session.commit();
        }
    }

    public static void tearDownDatabase() throws Exception {
        DuoSpaceService.resetNowSupplier();
        UserCache.clear();
        resetFactory();
        System.clearProperty(GlobalConfig.DATA_PATH_PROPERTY);
        GlobalConfig.initDataPath(null);
    }

    public static String activateSpace() {
        DuoSpaceService.invite(ACCOUNT_A, ACCOUNT_B);
        DuoSpaceService.respondInvite(ACCOUNT_B, true);
        return DuoSpaceService.profile(ACCOUNT_A).getSpaceId();
    }

    public static long atNoon(LocalDate date) {
        return date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public static long count(String table) throws Exception {
        if (!table.matches("[a-z_]+")) throw new IllegalArgumentException("测试表名不合法");
        try (SqlSession session = DbInitializer.factory().openSession(true);
             PreparedStatement ps = session.getConnection().prepareStatement("SELECT COUNT(1) FROM " + table);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    public static String attachmentStorageName(String attachmentId) throws Exception {
        try (SqlSession session = DbInitializer.factory().openSession(true);
             PreparedStatement ps = session.getConnection().prepareStatement(
                     "SELECT storage_name FROM duo_attachments WHERE id=?")) {
            ps.setString(1, attachmentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    public static void markAttachmentOld(String attachmentId) throws Exception {
        try (SqlSession session = DbInitializer.factory().openSession(false);
             PreparedStatement ps = session.getConnection().prepareStatement(
                     "UPDATE duo_attachments SET created_at=? WHERE id=?")) {
            ps.setLong(1, System.currentTimeMillis() - 2L * 24L * 60L * 60L * 1000L);
            ps.setString(2, attachmentId);
            ps.executeUpdate();
            session.commit();
        }
    }

    public static User user(long accountId, String channelId) {
        User user = new User();
        user.setId(channelId);
        user.setAccountId(accountId);
        user.setAccount("duo_user_" + accountId);
        user.setNickname("小屋用户");
        user.setStatus(UserStatus.FISHING);
        user.setChannel(new EmbeddedChannel());
        return user;
    }

    public static void setNow(long timestamp) {
        DuoSpaceService.setNowSupplierForTest(() -> timestamp);
    }

    private static void insertAccount(Connection conn, long id, String account, String nickname)
            throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO accounts(account_id, account, nickname, password_hash, created_at) VALUES(?,?,?,?,?)")) {
            ps.setLong(1, id);
            ps.setString(2, account);
            ps.setString(3, nickname);
            ps.setString(4, "test-password-hash");
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    private static void insertFriend(Connection conn, long ownerId, long friendId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO friends(owner_account_id, friend_account_id, created_at) VALUES(?,?,?)")) {
            ps.setLong(1, ownerId);
            ps.setLong(2, friendId);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    private static void insertQuizQuestion(Connection conn) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO tacit_quiz_questions " +
                        "(question, options_json, sort_order, active, created_at, updated_at) VALUES(?,?,?,?,?,?)")) {
            long now = System.currentTimeMillis();
            ps.setString(1, "测试默契题");
            ps.setString(2, "[\"A\",\"B\",\"C\"]");
            ps.setInt(3, 0);
            ps.setInt(4, 1);
            ps.setLong(5, now);
            ps.setLong(6, now);
            ps.executeUpdate();
        }
    }

    private static void resetFactory() throws Exception {
        Field field = DbInitializer.class.getDeclaredField("FACTORY");
        field.setAccessible(true);
        field.set(null, null);
    }
}
