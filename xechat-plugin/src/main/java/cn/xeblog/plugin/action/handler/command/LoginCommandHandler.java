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
        // 命令行 #login 已废弃,改用登录页(MainWindow 的 LOGIN 卡)输入框 + 按钮触发登录
        ConsoleAction.showSimpleMsg("#login 命令已停用,请在登录页输入账号密码或昵称登录");
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
