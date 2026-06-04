package cn.xeblog.plugin.action.handler.message;

import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.plugin.action.ConsoleAction;
import cn.xeblog.plugin.action.ReconnectAction;
import cn.xeblog.commons.entity.Response;
import cn.xeblog.plugin.annotation.DoMessage;
import cn.xeblog.plugin.cache.DataCache;
import cn.xeblog.plugin.ui.LoginPanel;
import cn.xeblog.plugin.ui.MainWindow;
import io.netty.channel.Channel;

/**
 * @author anlingyi
 * @date 2020/8/19
 */
@DoMessage(MessageType.SYSTEM)
public class SystemMessageHandler extends AbstractMessageHandler<String> {

    @Override
    protected void process(Response<String> response) {
        String body = response.getBody();
        if (DataCache.loginFromReconnect) {
            ReconnectAction.disable();
            LoginPanel lp = MainWindow.getInstance().getLoginPanel();
            if (lp != null) {
                lp.showError("自动重连失败: " + body);
            }
            MainWindow.getInstance().switchToLogin();
            closeActiveChannel();
            ConsoleAction.showSystemMsg(response.getTime(), body);
            return;
        }

        // 登录阶段(LoginPanel.awaitingLogin)收到的 SYSTEM 通常是"账号不存在/密码错误/登录已过期"等错误;
        // 此时控制台对用户不可见,把错误同步到登录页 statusLabel,让用户立刻看到
        LoginPanel lp = MainWindow.getInstance().getLoginPanel();
        if (lp != null && lp.isAwaitingLogin()) {
            lp.showError(body);
            // 登录失败时服务端只返回 SYSTEM,不一定主动关闭连接;客户端需清掉这条未认证连接。
            closeActiveChannel();
        }
        ConsoleAction.showSystemMsg(response.getTime(), body);
    }

    private static void closeActiveChannel() {
        Channel channel = DataCache.channel;
        if (channel != null && channel.isActive()) {
            channel.close();
        }
    }

}
