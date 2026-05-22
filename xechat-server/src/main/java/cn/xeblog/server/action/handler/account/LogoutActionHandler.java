package cn.xeblog.server.action.handler.account;

import cn.xeblog.commons.entity.LogoutDTO;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.server.account.SessionService;
import cn.xeblog.server.action.handler.AbstractActionHandler;
import cn.xeblog.server.annotation.DoAction;
import lombok.extern.slf4j.Slf4j;

/**
 * 退出登录(LOGOUT):吊销当前会话 token + 关闭 channel。
 *
 * <p>仅吊销本会话,不影响该账号其他端的 token。</p>
 *
 * @author dz
 * @date 2026/5/22
 */
@Slf4j
@DoAction(Action.LOGOUT)
public class LogoutActionHandler extends AbstractActionHandler<LogoutDTO> {

    @Override
    protected void process(User user, LogoutDTO body) {
        if (user.getToken() != null) {
            SessionService.revoke(user.getToken());
        }
        log.info("账号 {} 退出登录", user.getAccount());
        if (user.getChannel() != null) {
            user.getChannel().close();
        }
    }

}
