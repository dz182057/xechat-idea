package cn.xeblog.plugin.action.handler.message;

import cn.xeblog.commons.entity.LoginResultDTO;
import cn.xeblog.commons.entity.Response;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.plugin.action.ConsoleAction;
import cn.xeblog.plugin.annotation.DoMessage;
import cn.xeblog.plugin.cache.DataCache;
import cn.xeblog.plugin.persistence.PersistenceService;

/**
 * 账号体系:登录成功回包 LOGIN_RESULT 处理。
 *
 * <p>持久化 token + account 供下次自动登录;同步 DataCache.username
 * 为服务端返回的 nickname,确保后续 ONLINE_USERS 找自己的逻辑能匹配上。</p>
 *
 * @author dz
 * @date 2026/5/22
 */
@DoMessage(MessageType.LOGIN_RESULT)
public class LoginResultMessageHandler extends AbstractMessageHandler<LoginResultDTO> {

    @Override
    protected void process(Response<LoginResultDTO> response) {
        LoginResultDTO body = response.getBody();
        if (body == null || body.getUser() == null) {
            return;
        }
        User user = body.getUser();

        // 持久化 token + account,供 channelActive 下次自动 LOGIN_WITH_TOKEN
        if (body.getToken() != null) {
            PersistenceService.getData().setToken(body.getToken());
        }
        if (user.getAccount() != null) {
            PersistenceService.getData().setAccount(user.getAccount());
        }

        // DataCache.username 同步为 nickname,这样 ONLINE_USERS 找自己时按 username 索引能命中
        DataCache.username = user.getNickname();
        DataCache.account = user.getAccount();

        ConsoleAction.showSimpleMsg("登录成功:" + user.getNickname()
                + (User.Role.ADMIN.equals(user.getRole()) ? "(管理员)" : ""));
    }
}
