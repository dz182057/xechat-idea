package cn.xeblog.plugin.crypto;

import cn.xeblog.commons.entity.GetPeerKeyDTO;
import cn.xeblog.commons.entity.PeerKeyResponseDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.plugin.action.MessageAction;
import cn.xeblog.plugin.cache.DataCache;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;

/**
 * E2EE 会话密钥管理(GET_PEER_KEY → ECDH+HKDF → 缓存)。
 *
 * <p>对照 desktop {@code src/renderer/src/lib/e2ee-session.ts}:
 * 异步等待 PEER_KEY 回包,合并并发同一 account 的请求,挂超时兜底。</p>
 *
 * <ul>
 *   <li>缓存命中 → 立即返回</li>
 *   <li>identityPrivKey 缺失(token 登录) → failed future,UI 提示用密码重登</li>
 *   <li>未命中 → 发 GET_PEER_KEY,所有等待者共享一个 future</li>
 *   <li>{@link #handlePeerKey(PeerKeyResponseDTO)} 由 PeerKeyMessageHandler 转发进来</li>
 * </ul>
 *
 * @author dz
 * @date 2026/5/26
 */
public final class E2EESessionService {

    private static final long REQUEST_TIMEOUT_MS = 10_000;

    private static final ScheduledExecutorService TIMER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "e2ee-session-timeout");
                t.setDaemon(true);
                return t;
            });

    /** account → 同一 peer 正在等待中的 future 列表(同帧多次调用会合并成一次请求) */
    private static final Map<String, List<CompletableFuture<SessionEntry>>> PENDING = new ConcurrentHashMap<>();

    private E2EESessionService() {
    }

    public static final class SessionEntry {
        public final String account;
        public final long accountId;
        public final String nickname;
        public final String identityPubKey;
        public final byte[] sessionKey;

        public SessionEntry(String account, long accountId, String nickname,
                            String identityPubKey, byte[] sessionKey) {
            this.account = account;
            this.accountId = accountId;
            this.nickname = nickname;
            this.identityPubKey = identityPubKey;
            this.sessionKey = sessionKey;
        }
    }

    /**
     * 确保拿到与某 peer 的 sessionKey。线程安全,可在 EDT / Netty 线程调用。
     */
    public static CompletableFuture<SessionEntry> ensureSessionKey(String peerAccount) {
        if (StringUtils.isBlank(peerAccount)) {
            return failed(new IllegalArgumentException("peerAccount 不能为空"));
        }
        byte[] cached = DataCache.peerSessionKeys.get(peerAccount);
        if (cached != null && cached.length == 32) {
            String pub = DataCache.peerPublicKeys.get(peerAccount);
            Long aid = DataCache.peerAccountIdByAccount.get(peerAccount);
            String nick = DataCache.peerNicknameByAccount.get(peerAccount);
            if (pub != null && aid != null) {
                return CompletableFuture.completedFuture(
                        new SessionEntry(peerAccount, aid, nick, pub, cached));
            }
        }
        if (DataCache.identityPrivKey == null) {
            return failed(new IllegalStateException(
                    "E2EE 私钥未解锁,token 登录无法解密私聊,请 #exit 后用密码重登"));
        }

        CompletableFuture<SessionEntry> future = new CompletableFuture<>();
        boolean shouldSend;
        synchronized (PENDING) {
            List<CompletableFuture<SessionEntry>> waiters = PENDING.get(peerAccount);
            shouldSend = (waiters == null);
            if (shouldSend) {
                waiters = new ArrayList<>();
                PENDING.put(peerAccount, waiters);
            }
            waiters.add(future);
        }

        if (shouldSend) {
            MessageAction.send(new GetPeerKeyDTO(peerAccount), Action.GET_PEER_KEY);
            TIMER.schedule(() -> rejectIfStillPending(peerAccount,
                    new RuntimeException("拉取对端公钥超时(" + peerAccount + ")")),
                    REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
        return future;
    }

    /**
     * 由 PeerKeyMessageHandler 调用:收到 PEER_KEY 回包,派生 sessionKey + 唤醒所有等待者。
     */
    public static void handlePeerKey(PeerKeyResponseDTO resp) {
        if (resp == null || StringUtils.isBlank(resp.getAccount())
                || StringUtils.isBlank(resp.getIdentityPubKey())) {
            return;
        }
        String account = resp.getAccount();
        if (DataCache.identityPrivKey == null || DataCache.accountId == 0L) {
            rejectIfStillPending(account, new IllegalStateException("本端身份私钥未就绪"));
            return;
        }
        try {
            // 公钥变化检测:旧公钥与新公钥不同 → 标记 peerKeyChanged
            String prevPub = DataCache.peerPublicKeys.get(account);
            if (prevPub != null && !prevPub.equals(resp.getIdentityPubKey())) {
                DataCache.peerKeyChanged.add(account);
                DataCache.peerSessionKeys.remove(account); // 旧 sessionKey 作废
            }
            byte[] sessionKey = E2EECrypto.deriveSessionKey(
                    DataCache.identityPrivKey,
                    resp.getIdentityPubKey(),
                    String.valueOf(DataCache.accountId),
                    String.valueOf(resp.getAccountId()));
            DataCache.peerSessionKeys.put(account, sessionKey);
            DataCache.peerPublicKeys.put(account, resp.getIdentityPubKey());
            DataCache.peerAccountIdByAccount.put(account, resp.getAccountId());
            if (resp.getNickname() != null) {
                DataCache.peerNicknameByAccount.put(account, resp.getNickname());
                DataCache.peerAccountByUsername.put(resp.getNickname(), account);
            }
            SessionEntry entry = new SessionEntry(account, resp.getAccountId(),
                    resp.getNickname(), resp.getIdentityPubKey(), sessionKey);
            resolveAll(account, entry);
        } catch (Exception e) {
            rejectIfStillPending(account, e);
        }
    }

    /**
     * 由 PeerKeyChangedMessageHandler 调用:服务端主动推送对端公钥变化。
     * 清旧 sessionKey,标记 peerKeyChanged。下次 ensureSessionKey 会重新拉公钥。
     */
    public static void handlePeerKeyChanged(String account, String newPubKey) {
        if (StringUtils.isBlank(account)) {
            return;
        }
        String prevPub = DataCache.peerPublicKeys.get(account);
        if (prevPub != null && !prevPub.equals(newPubKey)) {
            DataCache.peerKeyChanged.add(account);
            DataCache.peerSessionKeys.remove(account);
            DataCache.peerPublicKeys.remove(account);
        }
    }

    private static void resolveAll(String account, SessionEntry entry) {
        List<CompletableFuture<SessionEntry>> waiters;
        synchronized (PENDING) {
            waiters = PENDING.remove(account);
        }
        if (waiters == null) return;
        for (CompletableFuture<SessionEntry> f : waiters) {
            f.complete(entry);
        }
    }

    private static void rejectIfStillPending(String account, Throwable err) {
        List<CompletableFuture<SessionEntry>> waiters;
        synchronized (PENDING) {
            waiters = PENDING.remove(account);
        }
        if (waiters == null) return;
        for (CompletableFuture<SessionEntry> f : waiters) {
            f.completeExceptionally(err);
        }
    }

    private static CompletableFuture<SessionEntry> failed(Throwable err) {
        CompletableFuture<SessionEntry> f = new CompletableFuture<>();
        f.completeExceptionally(err);
        return f;
    }

}
