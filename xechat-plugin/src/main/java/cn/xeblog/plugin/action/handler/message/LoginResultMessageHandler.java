package cn.xeblog.plugin.action.handler.message;

import cn.xeblog.commons.entity.LoginResultDTO;
import cn.xeblog.commons.entity.Response;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.plugin.action.ConsoleAction;
import cn.xeblog.plugin.annotation.DoMessage;
import cn.xeblog.plugin.cache.DataCache;
import cn.xeblog.plugin.persistence.PersistenceData;
import cn.xeblog.plugin.persistence.PersistenceService;
import org.apache.commons.lang3.StringUtils;

/**
 * 登录结果消息(LOGIN_RESULT)。
 *
 * <p>账号登录/token 登录/游客登录成功后,服务端推送 LoginResultDTO,客户端:</p>
 * <ul>
 *   <li>账号/token 登录:持久化 token+account 到 PersistenceData,后续启动可自动登录</li>
 *   <li>游客登录:不持久化任何凭据,只同步 DataCache.username 为服务端给的 nickname</li>
 *   <li>统一:把 DataCache.username 同步为服务端 nickname,保证 ONLINE_USERS 自查命中</li>
 * </ul>
 *
 * @author dz
 * @date 2026/5/25
 */
@DoMessage(MessageType.LOGIN_RESULT)
public class LoginResultMessageHandler extends AbstractMessageHandler<LoginResultDTO> {

    @Override
    protected void process(Response<LoginResultDTO> response) {
        LoginResultDTO body = response.getBody();
        if (body == null) {
            return;
        }
        User user = body.getUser();
        if (user != null) {
            // 把 DataCache.username 同步为服务端 nickname(注册账号或游客都做),
            // 保证 ONLINE_USERS 列表里的 user.username == DataCache.username
            DataCache.username = user.getNickname();
        }

        // 账号体系登录:持久化 token + account(IDEA PersistentStateComponent 关闭时自动落盘)
        if (StringUtils.isNotBlank(body.getToken()) && user != null && !user.isGuest()) {
            PersistenceData pd = PersistenceService.getData();
            pd.setToken(body.getToken());
            pd.setAccount(DataCache.account);
            ConsoleAction.showSimpleMsg("登录成功: " + user.getNickname() + " (账号 " + DataCache.account + ")");
        } else if (user != null && user.isGuest()) {
            ConsoleAction.showSimpleMsg("游客模式已就绪: " + user.getNickname() + " (仅大厅聊天,不能私聊)");
        }
    }

}
