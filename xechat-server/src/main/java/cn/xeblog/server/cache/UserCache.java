package cn.xeblog.server.cache;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.enums.Platform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 在线用户缓存,从单账号单连接演进为<b>多端并发</b>:同一 accountId 允许多个 channel 同时在线。
 *
 * <p>数据结构:</p>
 * <ul>
 *   <li>{@link #ID_TO_USER}: channelId → User(每个连接一条)</li>
 *   <li>{@link #ACCOUNT_TO_IDS}: accountId → 该账号当前所有活跃 channelId</li>
 * </ul>
 *
 * <p>对外语义:</p>
 * <ul>
 *   <li>{@link #get}: 按 channelId 取 User(不变,沿用旧调用方)</li>
 *   <li>{@link #getByAccount}: 取目标账号所有连接,用于私聊多端同步推送</li>
 *   <li>{@link #listOnlineByAccount}: 在线列表按账号去重,每个账号填上 platforms 集合</li>
 * </ul>
 *
 * @author anlingyi(原作者),dz(账号体系改造)
 */
public final class UserCache {

    private static final Map<String, User> ID_TO_USER = new ConcurrentHashMap<>(32);
    private static final Map<Long, Set<String>> ACCOUNT_TO_IDS = new ConcurrentHashMap<>(32);

    private UserCache() {
    }

    public static void add(String channelId, User user) {
        ID_TO_USER.put(channelId, user);
        long accountId = user.getAccountId();
        if (accountId != 0L) {
            ACCOUNT_TO_IDS
                    .computeIfAbsent(accountId, k -> ConcurrentHashMap.newKeySet())
                    .add(channelId);
        }
    }

    public static User get(String channelId) {
        return ID_TO_USER.get(channelId);
    }

    public static void remove(String channelId) {
        User user = ID_TO_USER.remove(channelId);
        if (user == null) {
            return;
        }
        long accountId = user.getAccountId();
        if (accountId == 0L) {
            return;
        }
        Set<String> ids = ACCOUNT_TO_IDS.get(accountId);
        if (ids != null) {
            ids.remove(channelId);
            if (ids.isEmpty()) {
                ACCOUNT_TO_IDS.remove(accountId);
            }
        }
    }

    /**
     * 取目标账号当前全部在线连接的 User 视图(用于私聊广播给该账号所有端)。
     */
    public static List<User> getByAccount(long accountId) {
        Set<String> ids = ACCOUNT_TO_IDS.get(accountId);
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<User> result = new ArrayList<>(ids.size());
        for (String id : ids) {
            User u = ID_TO_USER.get(id);
            if (u != null) {
                result.add(u);
            }
        }
        return result;
    }

    public static boolean isOnlineByAccount(long accountId) {
        Set<String> ids = ACCOUNT_TO_IDS.get(accountId);
        return ids != null && !ids.isEmpty();
    }

    public static int size() {
        return ID_TO_USER.size();
    }

    public static void clear() {
        ID_TO_USER.clear();
        ACCOUNT_TO_IDS.clear();
    }

    /**
     * 所有连接的 User 列表(不去重)。多数场景应改用 {@link #listOnlineByAccount}。
     */
    public static List<User> listUser() {
        return new ArrayList<>(ID_TO_USER.values());
    }

    /**
     * 在线用户列表(按 accountId 去重),每个账号取首个连接作代表,
     * 同时把该账号所有在线端的 platform 集合塞到 representative.platforms。
     * <p>返回的是<b>拷贝</b>,不会影响原 User 实例的 platforms 字段。</p>
     */
    public static List<User> listOnlineByAccount() {
        Map<Long, User> rep = new LinkedHashMap<>();
        Map<Long, Set<Platform>> platforms = new HashMap<>();

        // 没接入账号体系的旧连接(accountId=0)按 channelId 各算一条
        List<User> anonymous = new ArrayList<>();

        for (User u : ID_TO_USER.values()) {
            long aid = u.getAccountId();
            if (aid == 0L) {
                anonymous.add(u);
                continue;
            }
            rep.putIfAbsent(aid, u);
            platforms.computeIfAbsent(aid, k -> EnumSet.noneOf(Platform.class)).add(u.getPlatform());
        }

        List<User> result = new ArrayList<>(rep.size() + anonymous.size());
        for (Map.Entry<Long, User> entry : rep.entrySet()) {
            User copy = shallowCopy(entry.getValue());
            copy.setPlatforms(platforms.get(entry.getKey()));
            result.add(copy);
        }
        result.addAll(anonymous);
        return result;
    }

    /**
     * 同 platform 同账号是否已有在线连接(可用于"是否要踢旧"判定)。
     * 同 platform 允许重复登录(多窗口场景),实际是否互踢由 Handler 决定。
     */
    public static boolean hasOnlineByAccountAndPlatform(long accountId, Platform platform) {
        Set<String> ids = ACCOUNT_TO_IDS.get(accountId);
        if (ids == null) {
            return false;
        }
        for (String id : ids) {
            User u = ID_TO_USER.get(id);
            if (u != null && u.getPlatform() == platform) {
                return true;
            }
        }
        return false;
    }

    // ============ 过渡兼容(任务 #10 改造完 LoginActionHandler 后删除) ============

    /**
     * @deprecated 旧的 username 唯一性判定。账号体系改造后由 DB 唯一索引保证,
     * 此方法仅供 {@link cn.xeblog.server.action.handler.LoginActionHandler} 过渡期使用,
     * task #10 后删除。
     */
    @Deprecated
    public static boolean existUsername(String username) {
        for (User u : ID_TO_USER.values()) {
            if (u.getUsername() != null && u.getUsername().equals(username)) {
                return true;
            }
        }
        return false;
    }

    private static User shallowCopy(User src) {
        User dst = new User();
        dst.setUuid(src.getUuid());
        dst.setId(src.getId());
        dst.setAccountId(src.getAccountId());
        dst.setAccount(src.getAccount());
        dst.setNickname(src.getNickname());
        dst.setAvatarVersion(src.getAvatarVersion());
        dst.setStatus(src.getStatus());
        dst.setShortRegion(src.getShortRegion());
        dst.setRole(src.getRole());
        dst.setPermit(src.getPermit());
        dst.setPlatform(src.getPlatform());
        // 不复制 ip/region/channel(transient)
        return dst;
    }

}
