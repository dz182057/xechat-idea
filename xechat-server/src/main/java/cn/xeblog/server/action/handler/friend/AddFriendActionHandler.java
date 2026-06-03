package cn.xeblog.server.action.handler.friend;

import cn.xeblog.commons.entity.AddFriendDTO;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.server.account.AccountException;
import cn.xeblog.server.action.handler.AbstractActionHandler;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.friend.FriendService;
import lombok.extern.slf4j.Slf4j;

/**
 * 发起好友申请。
 *
 * @author dz
 * @date 2026/6/3
 */
@Slf4j
@DoAction(Action.ADD_FRIEND)
public class AddFriendActionHandler extends AbstractActionHandler<AddFriendDTO> {

    @Override
    protected void process(User user, AddFriendDTO body) {
        if (user.isGuest()) {
            user.send(ResponseBuilder.system("游客不能添加好友"));
            return;
        }
        try {
            FriendService.addRequest(user, body == null ? null : body.getTarget());
            user.send(ResponseBuilder.system("好友申请已发送"));
        } catch (AccountException e) {
            user.send(ResponseBuilder.system(e.getMessage()));
        } catch (Exception e) {
            log.error("发送好友申请异常 accountId={}", user.getAccountId(), e);
            user.send(ResponseBuilder.system("发送好友申请失败,请稍后重试"));
        }
    }

}
