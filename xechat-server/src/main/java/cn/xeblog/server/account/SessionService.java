package cn.xeblog.server.account;

import cn.xeblog.server.account.entity.SessionEntity;
import cn.xeblog.server.account.mapper.SessionMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSession;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 会话(token)生命周期管理。
 *
 * <p>token: 32 字节 {@link SecureRandom} → base64url 43 字符。每次使用滑动续期到 now+30d,
 * 30 天未用则失效。改密时同账号全部 token 一起吊销。</p>
 *
 * @author dz
 * @date 2026/5/22
 */
@Slf4j
public final class SessionService {

    /**
     * token 滑动 TTL: 30 天
     */
    public static final long TOKEN_TTL_MS = 30L * 24 * 60 * 60 * 1000;

    /**
     * 已吊销且过期超过 7 天后从表中物理清理
     */
    private static final long CLEANUP_THRESHOLD_MS = 7L * 24 * 60 * 60 * 1000;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private static volatile ScheduledExecutorService CLEANUP_EXECUTOR;

    private SessionService() {
    }

    /**
     * 创建并持久化新 token。
     *
     * @return 新 token 字符串(43 字符 base64url)
     */
    public static SessionEntity createToken(long accountId, String platform,
                                            String clientUuid, String ip) {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        String token = URL_ENCODER.encodeToString(buf);

        long now = System.currentTimeMillis();
        SessionEntity s = SessionEntity.builder()
                .token(token)
                .accountId(accountId)
                .platform(platform)
                .clientUuid(clientUuid)
                .createdAt(now)
                .lastUsedAt(now)
                .expiresAt(now + TOKEN_TTL_MS)
                .revoked(false)
                .ip(ip)
                .build();

        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            session.getMapper(SessionMapper.class).insert(s);
        }
        return s;
    }

    /**
     * 校验 token,有效则滑动续期到 now+30d 并返回会话快照。
     *
     * @return 会话快照(已 touch);无效返回 null
     */
    public static SessionEntity validateAndTouch(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            SessionMapper mapper = session.getMapper(SessionMapper.class);
            SessionEntity s = mapper.findByToken(token);
            if (s == null || s.isRevoked()) {
                return null;
            }
            long now = System.currentTimeMillis();
            if (s.getExpiresAt() < now) {
                return null;
            }
            long newExpires = now + TOKEN_TTL_MS;
            mapper.touchLastUsed(token, now, newExpires);
            s.setLastUsedAt(now);
            s.setExpiresAt(newExpires);
            return s;
        }
    }

    public static void revoke(String token) {
        if (token == null) {
            return;
        }
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            session.getMapper(SessionMapper.class).revoke(token);
        }
    }

    public static void revokeAllByAccount(long accountId) {
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            session.getMapper(SessionMapper.class).revokeAllByAccount(accountId);
        }
    }

    /**
     * 启动定时清理任务:每 24h 清理一次已吊销 + 过期超 7 天的记录。
     *
     * <p>由 {@link cn.xeblog.server.XEChatServer#main} 启动后调用一次。</p>
     */
    public static synchronized void startCleanupJob() {
        if (CLEANUP_EXECUTOR != null) {
            return;
        }
        CLEANUP_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "session-cleanup");
            t.setDaemon(true);
            return t;
        });
        CLEANUP_EXECUTOR.scheduleAtFixedRate(SessionService::runCleanup,
                1, 24, TimeUnit.HOURS);
        log.info("session 清理任务已启动,每 24h 执行一次");
    }

    private static void runCleanup() {
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            long threshold = System.currentTimeMillis() - CLEANUP_THRESHOLD_MS;
            int deleted = session.getMapper(SessionMapper.class).cleanupExpired(threshold);
            if (deleted > 0) {
                log.info("session 清理: 删除 {} 行(已吊销+过期超 7d)", deleted);
            }
        } catch (Exception e) {
            log.warn("session 清理任务异常: {}", e.getMessage());
        }
    }

}
