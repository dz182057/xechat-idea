package cn.xeblog.server.action.handler.account;

import cn.xeblog.commons.entity.SetStealthDTO;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.server.account.DbInitializer;
import cn.xeblog.server.account.mapper.AccountMapper;
import cn.xeblog.server.action.ChannelAction;
import cn.xeblog.server.action.handler.AbstractActionHandler;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.cache.UserCache;
import cn.xeblog.server.friend.FriendService;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSession;

/**
 * 设置隐身状态。
 *
 * @author dz
 * @date 2026/6/3
 */
@Slf4j
@DoAction(Action.SET_STEALTH)
public class SetStealthActionHandler extends AbstractActionHandler<SetStealthDTO> {

    @Override
    protected void process(User user, SetStealthDTO body) {
        if (user.isGuest()) {
            user.send(ResponseBuilder.system("游客不能设置隐身"));
            return;
        }
        if (body == null) {
            user.send(ResponseBuilder.system("隐身设置不能为空"));
            return;
        }
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            session.getMapper(AccountMapper.class).updateStealth(user.getAccountId(), body.isStealth());
        } catch (Exception e) {
            log.error("设置隐身失败 accountId={}", user.getAccountId(), e);
            user.send(ResponseBuilder.system("设置隐身失败,请稍后重试"));
            return;
        }

        for (User conn : UserCache.getByAccount(user.getAccountId())) {
            conn.setStealth(body.isStealth());
        }
        ChannelAction.sendOnlineUsers();
        FriendService.pushFriendListRefreshForAccount(user.getAccountId());
        user.send(ResponseBuilder.system(body.isStealth() ? "已开启隐身" : "已关闭隐身"));
    }

}
