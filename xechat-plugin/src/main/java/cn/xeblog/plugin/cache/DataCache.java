package cn.xeblog.plugin.cache;

import cn.hutool.core.util.StrUtil;
import cn.xeblog.commons.entity.OnlineServer;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.enums.UserStatus;
import cn.xeblog.plugin.action.ConnectionAction;
import cn.xeblog.plugin.tools.browser.config.BrowserConfig;
import cn.xeblog.plugin.tools.read.ReadConfig;
import com.intellij.openapi.project.Project;
import io.netty.channel.Channel;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * @author anlingyi
 * @date 2020/6/1
 */
public class DataCache {

    /**
     * 用户唯一标识
     */
    public static String uuid;

    /**
     * 当前登录的用户名(账号登录时由 LoginResultMessageHandler 同步为服务端返回的 nickname)
     */
    public static String username;

    /**
     * 账号登录账号(账号体系登录;为空则走游客或 token 路径)
     */
    public static String account;

    /**
     * 明文密码(仅一次性,channelActive 发送后立刻清空,不在内存长期持有)
     */
    public static String password;

    /**
     * 是否游客模式(channelActive 据此决定发 LOGIN 时只填 username)
     */
    public static boolean guestMode;

    /**
     * 是否在线
     */
    public static boolean isOnline;

    /**
     * 通道
     */
    public static Channel channel;

    /**
     * 当前在线用户缓存，key -> username
     */
    public static Map<String, User> userMap = new ConcurrentHashMap<>();

    /**
     * 服务器连接数据缓存
     */
    public static ConnectionAction connectionAction;

    /**
     * 断线重连标记
     */
    public static boolean reconnected;

    /**
     * 用户状态设置缓存
     */
    public static UserStatus userStatus;

    /**
     * 当前项目
     */
    public static Project project;

    /**
     * 消息通知 1.正常通知 2.隐晦通知 3.关闭通知
     */
    public static int msgNotify;

    /**
     * 服务端列表
     */
    public static List<OnlineServer> serverList;

    /**

     * 阅读配置
     */
    public static ReadConfig readConfig = new ReadConfig();

    /**
     * 浏览器配置
     */
    public static BrowserConfig browserConfig = new BrowserConfig();

    // ============ E2EE 私聊 ============
    // 见 docs/design/e2ee-and-history.md §九。所有字段纯内存,登出/重连清空。
    // 安全模型:密码即历史 —— password 仅一次性,派生 master 后立即清空;
    // token 自动登录路径下 password 缺失,identityPrivKey 不可用 → 私聊禁用,提示用密码重登。

    /**
     * 当前账号雪花 ID(LOGIN_RESULT.user.accountId 同步)
     */
    public static long accountId;

    /**
     * 当前账号 X25519 身份公钥(base64url),用于安全码展示
     */
    public static String identityPubKey;

    /**
     * 当前账号 X25519 身份私钥(raw 32B)。
     * 由 LOGIN_RESULT 阶段用 master key 解包得到,内存只,严禁持久化。
     */
    public static byte[] identityPrivKey;

    /**
     * 与某 peer(account 为 key)派生出的 AES-256 长期会话密钥(raw 32B)。
     * 由 ECDH(myPriv, peerPub) + HKDF 得到,首次私聊后缓存。
     */
    public static Map<String, byte[]> peerSessionKeys = new ConcurrentHashMap<>();

    /**
     * peer account → peer 当前身份公钥(base64url)。
     * 用于变化检测:下次拉到不同的公钥即认为换设备/重置密钥,提示用户。
     */
    public static Map<String, String> peerPublicKeys = new ConcurrentHashMap<>();

    /**
     * peer account → peer nickname(展示用)。PEER_KEY / PRIVATE_USER 回包时回写。
     */
    public static Map<String, String> peerNicknameByAccount = new ConcurrentHashMap<>();

    /**
     * peer account → peer accountId(发起 PRIVATE_CHAT 时填充 envelope.peerAccountId)。
     */
    public static Map<String, Long> peerAccountIdByAccount = new ConcurrentHashMap<>();

    /**
     * peer nickname → peer account 反查表。
     * 由 ONLINE_USERS / USER_STATE 维护,#to/#fingerprint 命令用昵称定位 account。
     */
    public static Map<String, String> peerAccountByUsername = new ConcurrentHashMap<>();

    /**
     * 已被标记"公钥已变化"的 peer account 集合(展示给用户在 #fingerprint 时看到)。
     */
    public static Set<String> peerKeyChanged = new CopyOnWriteArraySet<>();

    /**
     * 获取用户信息
     *
     * @param username 用户名
     * @return
     */
    public static User getUser(String username) {
        if (userMap == null) {
             return null;
        }

        return userMap.get(username);
    }

    /**
     * 获取当前用户信息
     *
     * @return
     */
    public static User getCurrentUser() {
        return getUser(username);
    }

    public static void addUser(User user) {
        if (getUser(user.getUsername()) != null) {
            return;
        }

        userMap.put(user.getUsername(), user);
    }

    public static void updateUser(User user) {
        if (getUser(user.getUsername()) == null) {
            return;
        }

        userMap.put(user.getUsername(), user);
    }

    public static void removeUser(User user) {
        if (userMap == null) {
            return;
        }

        User origin = userMap.get(user.getUsername());
        if (origin == null) {
            return;
        }

        if (StrUtil.equals(origin.getId(), user.getId())) {
            userMap.remove(user.getUsername());
        }
    }

    public static int getOnlineUserTotal() {
        if (userMap == null) {
            return 0;
        }

        return userMap.size();
    }

}
