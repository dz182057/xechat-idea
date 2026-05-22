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
import cn.xeblog.plugin.persistence.PersistenceService;
import cn.xeblog.commons.util.ParamsUtils;
import io.netty.channel.Channel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.List;

/**
 * /login &lt;account&gt; &lt;password&gt; [-h host] [-p port] [-s 服务器编号] [-c 清缓存]
 *
 * <p>账号体系改造后:
 * <ul>
 *   <li>第一参数必填,作为<b>登录账号</b>(原 username 含义弃用)</li>
 *   <li>第二参数为<b>明文密码</b>;若省略且本地有 token 且账号匹配,使用 token 自动登录</li>
 * </ul></p>
 *
 * @author anlingyi(原作者),dz(账号体系最小化改造)
 */
@DoCommand(Command.LOGIN)
public class LoginCommandHandler extends AbstractCommandHandler {

    private static boolean CONNECTING;

    @Getter
    @AllArgsConstructor
    private enum Config {
        HOST("-h"),
        PORT("-p"),
        CLEAN("-c"),
        SERVER("-s");

        private String key;

        public static Config getConfig(String name) {
            if (name == null) {
                return null;
            }
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

        int len = args.length;
        // 提取前两个非 flag 位置参数
        String account = null;
        String password = null;
        if (len > 0 && Config.getConfig(args[0]) == null) {
            account = args[0];
        }
        if (len > 1 && Config.getConfig(args[1]) == null) {
            password = args[1];
        }

        if (StringUtils.isBlank(account)) {
            ConsoleAction.showSimpleMsg("用法:/login <账号> <密码> [-h host] [-p port]");
            return;
        }
        if (!CheckUtils.checkUsername(account) || !account.matches("^[a-zA-Z0-9_]{4,20}$")) {
            ConsoleAction.showSimpleMsg("账号不合法(4-20 位字母数字下划线)！");
            return;
        }

        // 密码可省略:本地有 token 且 account 匹配则走 token 登录
        String savedToken = PersistenceService.getData().getToken();
        String savedAccount = PersistenceService.getData().getAccount();
        boolean canUseToken = StringUtils.isNotBlank(savedToken)
                && account.equals(savedAccount);
        if (StringUtils.isBlank(password) && !canUseToken) {
            ConsoleAction.showSimpleMsg("请输入密码,例:/login " + account + " <密码>");
            return;
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
        DataCache.account = account;
        DataCache.password = password; // 可能为 null;为 null 时 XEChatClientHandler 走 token 流程
        // username 先用 account 占位,登录成功后 LoginResultMessageHandler 会更新为 nickname
        DataCache.username = account;
        ConsoleAction.showSimpleMsg("正在连接服务器...");
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
