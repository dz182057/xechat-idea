package cn.xeblog.plugin.action;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.xeblog.commons.util.CheckUtils;
import cn.xeblog.plugin.cache.DataCache;
import cn.xeblog.plugin.client.ClientConnectConsumer;
import cn.xeblog.plugin.persistence.PersistenceData;
import cn.xeblog.plugin.persistence.PersistenceService;
import io.netty.channel.Channel;
import org.apache.commons.lang3.StringUtils;

import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.regex.Pattern;

/**
 * 登录服务:封装"账号+密码 / 游客 / token 自动"三类登录的核心动作,
 * 供登录页(LoginPanel)和兼容期的命令行入口共享。
 *
 * <p>注意:本类只负责"准备 DataCache 字段 + 发起 TCP 连接",登录回包
 * (LoginResult/SYSTEM)处理仍由 LoginResultMessageHandler / XEChatClientHandler
 * 完成。调用方需通过 {@link Callback} 拿到连接建立成功/失败的回调。</p>
 *
 * @author dz
 * @date 2026/5/26
 */
public final class LoginService {

    /** 账号格式([a-zA-Z0-9_]{4,20}),用于区分输入是账号还是游客昵称 */
    private static final Pattern ACCOUNT_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{4,20}$");

    /** 防并发连接的状态位:CONNECTING 期间不接受新的登录请求 */
    private static volatile boolean CONNECTING;

    private LoginService() {
    }

    /** 连接结果回调(只在 TCP 连接阶段触发,登录回包另行处理) */
    public interface Callback {
        void onConnecting();

        void onConnected(Channel channel);

        void onFailed(String reason);
    }

    public static boolean isConnecting() {
        return CONNECTING;
    }

    /**
     * 账号 + 密码登录。
     *
     * @param account  4-20 位字母数字下划线
     * @param password 至少 8 位
     * @param host     服务器 host(覆盖持久化)
     * @param port     服务器端口(覆盖持久化)
     */
    public static void loginByPassword(String account, String password,
                                        String host, int port, Callback cb) {
        if (preCheck(cb)) {
            return;
        }
        if (!ACCOUNT_PATTERN.matcher(account == null ? "" : account).matches()) {
            cb.onFailed("账号格式不合法(4-20 位字母数字下划线)");
            return;
        }
        if (password == null || password.length() < 8) {
            cb.onFailed("密码至少 8 位");
            return;
        }
        DataCache.account = account;
        DataCache.password = password;
        DataCache.guestMode = false;
        DataCache.username = account; // 临时占位,LOGIN_RESULT 用 nickname 覆盖
        connect(host, port, cb);
    }

    /** 游客登录:用昵称免密进入。 */
    public static void loginAsGuest(String nickname, String host, int port, Callback cb) {
        if (preCheck(cb)) {
            return;
        }
        if (StringUtils.isBlank(nickname)) {
            cb.onFailed("请输入昵称");
            return;
        }
        if (!CheckUtils.checkUsername(nickname)) {
            cb.onFailed("名称不合法,请修改后重试");
            return;
        }
        if (nickname.length() > 12) {
            cb.onFailed("名称长度不能超过 12 个字符");
            return;
        }
        DataCache.account = null;
        DataCache.password = null;
        DataCache.guestMode = true;
        DataCache.username = nickname;
        connect(host, port, cb);
    }

    /**
     * token 自动登录:本地有 token + account 配对时调用。
     * 调用前应自行确认 PersistenceData.token 不为空。
     */
    public static void loginByToken(String host, int port, Callback cb) {
        if (preCheck(cb)) {
            return;
        }
        PersistenceData pd = PersistenceService.getData();
        String token = pd.getToken();
        String storedAccount = pd.getAccount();
        if (StringUtils.isBlank(token) || StringUtils.isBlank(storedAccount)) {
            cb.onFailed("本地无可用 token,请用账号密码登录");
            return;
        }
        DataCache.account = storedAccount;
        DataCache.password = null;
        DataCache.guestMode = false;
        DataCache.username = storedAccount; // 临时占位
        connect(host, port, cb);
    }

    /**
     * 通用连接:执行 TCP 连接,登录消息由 XEChatClientHandler.channelActive 据 DataCache 字段决定发哪种 LOGIN_DTO。
     */
    private static void connect(String host, int port, Callback cb) {
        if (StringUtils.isBlank(host) || port <= 0) {
            CONNECTING = false;
            cb.onFailed("请填写服务器 host 和 port");
            return;
        }
        ensureUuid();
        ConnectionAction conn = new ConnectionAction();
        conn.setHost(host);
        conn.setPort(port);
        cb.onConnecting();
        conn.exec(new ClientConnectConsumer() {
            @Override
            public void doSucceed(Channel channel) {
                CONNECTING = false;
                DataCache.connectionAction = conn;
                // 连接成功才记住本次 host/port,失败/取消不污染上次配置
                PersistenceData pd = PersistenceService.getData();
                pd.setHost(host);
                pd.setPort(port);
                cb.onConnected(channel);
            }

            @Override
            public void doFailed() {
                CONNECTING = false;
                // 连接失败时把临时设置的 DataCache 凭据清掉,避免下次走错路径
                DataCache.password = null;
                cb.onFailed("连接服务器失败");
            }
        });
    }

    /** 公共校验:已登录 / 正在连接中拒绝新请求 */
    private static boolean preCheck(Callback cb) {
        if (DataCache.isOnline) {
            cb.onFailed("已是登录状态");
            return true;
        }
        if (CONNECTING) {
            cb.onFailed("请等待之前的连接...");
            return true;
        }
        CONNECTING = true;
        return false;
    }

    /** DataCache.uuid 缺失时用网卡 MAC / UUID 兜底生成 */
    private static void ensureUuid() {
        if (StrUtil.isNotBlank(DataCache.uuid)) {
            return;
        }
        String uuid = getMac();
        if (StrUtil.isBlank(uuid)) {
            uuid = IdUtil.fastUUID();
        }
        DataCache.uuid = uuid;
    }

    private static String getMac() {
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                byte[] bytes = ni.getHardwareAddress();
                if (bytes != null) {
                    StringBuilder sb = new StringBuilder();
                    for (byte b : bytes) {
                        sb.append(String.format("%02X", b)).append("-");
                    }
                    sb.deleteCharAt(sb.length() - 1);
                    return sb.toString();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

}
