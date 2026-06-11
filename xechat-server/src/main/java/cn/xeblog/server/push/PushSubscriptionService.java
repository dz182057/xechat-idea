package cn.xeblog.server.push;

import cn.xeblog.server.account.DbInitializer;
import org.apache.ibatis.session.SqlSession;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public final class PushSubscriptionService {

    private PushSubscriptionService() {
    }

    public static void upsert(long accountId, String endpoint, String p256dh, String auth) {
        if (accountId <= 0 || isBlank(endpoint) || isBlank(p256dh) || isBlank(auth)) {
            throw new IllegalArgumentException("推送订阅参数不完整");
        }
        long now = System.currentTimeMillis();
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            Connection conn = session.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO push_subscriptions(account_id, endpoint, p256dh, auth, created_at, updated_at) " +
                            "VALUES(?,?,?,?,?,?) " +
                            "ON CONFLICT(endpoint) DO UPDATE SET account_id=excluded.account_id, " +
                            "p256dh=excluded.p256dh, auth=excluded.auth, updated_at=excluded.updated_at")) {
                ps.setLong(1, accountId);
                ps.setString(2, endpoint);
                ps.setString(3, p256dh);
                ps.setString(4, auth);
                ps.setLong(5, now);
                ps.setLong(6, now);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            throw new IllegalStateException("保存推送订阅失败", e);
        }
    }

    public static void delete(long accountId, String endpoint) {
        if (accountId <= 0 || isBlank(endpoint)) {
            return;
        }
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            try (PreparedStatement ps = session.getConnection().prepareStatement(
                    "DELETE FROM push_subscriptions WHERE account_id=? AND endpoint=?")) {
                ps.setLong(1, accountId);
                ps.setString(2, endpoint);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            throw new IllegalStateException("删除推送订阅失败", e);
        }
    }

    public static void deleteEndpoint(String endpoint) {
        if (isBlank(endpoint)) {
            return;
        }
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            try (PreparedStatement ps = session.getConnection().prepareStatement(
                    "DELETE FROM push_subscriptions WHERE endpoint=?")) {
                ps.setString(1, endpoint);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            throw new IllegalStateException("删除失效推送订阅失败", e);
        }
    }

    public static List<PushSubscriptionEntity> listByAccount(long accountId) {
        List<PushSubscriptionEntity> rows = new ArrayList<>();
        if (accountId <= 0) {
            return rows;
        }
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            try (PreparedStatement ps = session.getConnection().prepareStatement(
                    "SELECT account_id, endpoint, p256dh, auth FROM push_subscriptions WHERE account_id=?")) {
                ps.setLong(1, accountId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new PushSubscriptionEntity(
                                rs.getLong("account_id"),
                                rs.getString("endpoint"),
                                rs.getString("p256dh"),
                                rs.getString("auth")
                        ));
                    }
                }
            }
            return rows;
        } catch (Exception e) {
            throw new IllegalStateException("读取推送订阅失败", e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
