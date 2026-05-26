package cn.xeblog.plugin.action.handler.message;

import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.plugin.action.ConsoleAction;
import cn.xeblog.commons.entity.Response;
import cn.xeblog.plugin.annotation.DoMessage;
import cn.xeblog.plugin.ui.LoginPanel;
import cn.xeblog.plugin.ui.MainWindow;

/**
 * @author anlingyi
 * @date 2020/8/19
 */
@DoMessage(MessageType.SYSTEM)
public class SystemMessageHandler extends AbstractMessageHandler<String> {

    @Override
    protected void process(Response<String> response) {
        String body = response.getBody();
        // 登录阶段(LoginPanel.awaitingLogin)收到的 SYSTEM 通常是"账号不存在/密码错误/登录已过期"等错误;
        // 此时控制台对用户不可见,把错误同步到登录页 statusLabel,让用户立刻看到
        LoginPanel lp = MainWindow.getInstance().getLoginPanel();
        if (lp != null && lp.isAwaitingLogin()) {
            lp.showError(body);
        }
        ConsoleAction.showSystemMsg(response.getTime(), body);
    }

}
