package cn.xeblog.plugin.action.handler.command;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import cn.xeblog.commons.entity.OnlineServer;
import cn.xeblog.commons.util.CheckUtils;
import cn.xeblog.commons.util.ServerUtils;
import cn.xeblog.plugin.action.ConnectionAction;
import cn.xeblog.plugin.action.ConsoleAction;
import cn.xeblog.plugin.annotation.DoCommand;
import cn.xeblog.plugin.cache.DataCache;
import cn.xeblog.plugin.client.ClientConnectConsumer;
import cn.xeblog.plugin.enums.Command;
import cn.xeblog.commons.util.ParamsUtils;
import cn.xeblog.plugin.persistence.PersistenceService;
import io.netty.channel.Channel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.List;

/**
 * @author anlingyi
 * @date 2020/8/19
 */
@DoCommand(Command.LOGIN)
public class LoginCommandHandler extends AbstractCommandHandler {

    private static boolean CONNECTING;

    /**
     * 账号格式([a-zA-Z0-9_]{4,20}),用于区分"一个参数"时是账号还是游客昵称
     */
    private static final Pattern ACCOUNT_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{4,20}$");

    @Getter
    @AllArgsConstructor
    private enum Config {
        /**
         * 服务器地址
         */
        HOST("-h"),
        /**
         * 端口
         */
        PORT("-p"),
        /**
         * 清除缓存的服务器配置信息
         */
        CLEAN("-c"),
        /**
         * 指定服务器编号
         */
        SERVER("-s");

        private String key;

        public static Config getConfig(String name) {
            for (Config value : values()) {
                if (value.getKey().equals(name)) {
                    return value;
                }
            }

            return null;
        }
    }

    @Override
    public void process(String[] args) {
        if (DataCache.isOnline) {
            ConsoleAction.showSimpleMsg("已是登录状态！");
            return;
        }
        if (CONNECTING) {
            ConsoleAction.showSimpleMsg("请等待之前的连接...");
            return;
        }

        // 解析非 -k 选项的"位置参数"(用户名/账号/密码)
        int len = args.length;
        String arg1 = null; // 用户名(游客) or 账号
        String arg2 = null; // 密码(仅账号登录时有)
        for (int i = 0; i < len; i++) {
            String a = args[i];
            if (Config.getConfig(a) != null) {
                i++; // 跳过 -k 的值
                continue;
            }
            if (arg1 == null) {
                arg1 = a;
            } else if (arg2 == null) {
                arg2 = a;
            }
        }

        // 三路分发:
        // 1. 两个位置参数 → 账号+密码登录
        // 2. 一个位置参数 + token 在本地 + 与账号匹配 → token 自动登录
        // 3. 一个位置参数 → 游客模式
        // 4. 零位置参数 → 复用 DataCache.username 走游客
        String loginAccount = null;
        String loginPassword = null;
        String guestNickname = null;
        boolean useToken = false;

        if (arg2 != null) {
            // 账号+密码登录
            if (!ACCOUNT_PATTERN.matcher(arg1).matches()) {
                ConsoleAction.showSimpleMsg("账号格式不合法(4-20 位字母数字下划线)");
                return;
            }
            if (arg2.length() < 8) {
                ConsoleAction.showSimpleMsg("密码至少 8 位");
                return;
            }
            loginAccount = arg1;
            loginPassword = arg2;
        } else {
            // 一个或零个位置参数:游客模式或 token 复用
            String name = arg1 != null ? arg1 : DataCache.username;
            if (StringUtils.isBlank(name)) {
                ConsoleAction.showSimpleMsg("用法: #login {昵称} 进入游客模式;或 #login {账号} {密码} 账号登录");
                return;
            }
            if (!CheckUtils.checkUsername(name)) {
                ConsoleAction.showSimpleMsg("名称不合法，请修改后重试！");
                return;
            }
            if (name.length() > 12) {
                ConsoleAction.showSimpleMsg("名称长度不能超过12个字符！");
                return;
            }
            // 若与本地持久化 account 匹配 + 有 token → token 路径
            String storedAccount = PersistenceService.getData().getAccount();
            String storedToken = PersistenceService.getData().getToken();
            if (ACCOUNT_PATTERN.matcher(name).matches()
                    && name.equals(storedAccount)
                    && StringUtils.isNotBlank(storedToken)) {
                loginAccount = name;
                useToken = true;
            } else {
                guestNickname = name;
            }
        }

        if (ParamsUtils.hasKey(args, Config.CLEAN.getKey())) {
            DataCache.connectionAction = null;
        }

        ConnectionAction conn = new ConnectionAction();
        if (DataCache.connectionAction != null) {
            BeanUtil.copyProperties(DataCache.connectionAction, conn);
        }

        String host = ParamsUtils.getValue(args, Config.HOST.getKey());
        String port = ParamsUtils.getValue(args, Config.PORT.getKey());
        if (StrUtil.isNotBlank(host)) {
            conn.setHost(host);
        }
        if (StrUtil.isNotBlank(port)) {
            conn.setPort(Integer.parseInt(port));
        }

        String serverIdStr = ParamsUtils.getValue(args, Config.SERVER.getKey());
        if (StrUtil.isNotBlank(serverIdStr)) {
            List<OnlineServer> onlineServerList = DataCache.serverList;
            if (CollUtil.isEmpty(onlineServerList)) {
                onlineServerList = ServerUtils.getServerList();
                DataCache.serverList = onlineServerList;
            }

            if (CollUtil.isEmpty(onlineServerList)) {
                ConsoleAction.showSimpleMsg("服务列表为空！");
                return;
            }

            int serverId = -1;
            if (NumberUtil.isNumber(serverIdStr)) {
                serverId = Integer.parseInt(serverIdStr);
            }
            if (serverId < 0 || serverId >= onlineServerList.size()) {
                ConsoleAction.showSimpleMsg("非法的服务器编号！");
                return;
            }

            OnlineServer onlineServer = onlineServerList.get(serverId);
            conn.setHost(onlineServer.getIp());
            conn.setPort(onlineServer.getPort());
        }

        if (StrUtil.isBlank(DataCache.uuid)) {
            String uuid = getMac();
            if (StrUtil.isBlank(uuid)) {
                uuid = IdUtil.fastUUID();
            }
            DataCache.uuid = uuid;
        }

        CONNECTING = true;
        DataCache.account = loginAccount;
        DataCache.password = loginPassword;
        DataCache.guestMode = guestNickname != null;
        // username 字段:游客时直接是 nickname;账号登录则待 LoginResultMessageHandler 用服务端 nickname 同步
        if (guestNickname != null) {
            DataCache.username = guestNickname;
        } else if (useToken) {
            DataCache.username = loginAccount; // 临时占位,token 登录返回 LoginResult 时再用 nickname 覆盖
        } else {
            DataCache.username = loginAccount; // 同上
        }

        String modeDesc = guestNickname != null
                ? "游客模式 " + guestNickname
                : (useToken ? "token 自动登录 " + loginAccount : "账号登录 " + loginAccount);
        ConsoleAction.showSimpleMsg("正在连接服务器(" + modeDesc + ")...");
        conn.exec(new ClientConnectConsumer() {
            @Override
            public void doSucceed(Channel channel) {
                CONNECTING = false;
                DataCache.connectionAction = conn;
            }

            @Override
            public void doFailed() {
                CONNECTING = false;
                ConsoleAction.showSimpleMsg("连接服务器失败！");
            }
        });
    }

    @Override
    protected boolean check(String[] args) {
        return true;
    }

    public static String getMac() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                byte[] bytes = networkInterface.getHardwareAddress();
                if (bytes != null) {
                    StringBuilder sb = new StringBuilder();
                    for (byte b : bytes) {
                        sb.append(String.format("%02X", b)).append("-");
                    }
                    sb.deleteCharAt(sb.length() - 1);
                    return sb.toString();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

}
