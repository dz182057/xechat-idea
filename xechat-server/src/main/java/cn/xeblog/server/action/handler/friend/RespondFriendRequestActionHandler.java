package cn.xeblog.server.action.handler.friend;

import cn.xeblog.commons.entity.RespondFriendRequestDTO;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.account.AccountException;
import cn.xeblog.server.action.handler.AbstractActionHandler;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.friend.FriendService;
import lombok.extern.slf4j.Slf4j;

/**
 * 处理好友申请。
 *
 * @author dz
 * @date 2026/6/3
 */
@Slf4j
@DoAction(Action.RESPOND_FRIEND_REQUEST)
public class RespondFriendRequestActionHandler extends AbstractActionHandler<RespondFriendRequestDTO> {

    @Override
    protected void process(User user, RespondFriendRequestDTO body) {
        if (user.isGuest()) {
            user.send(ResponseBuilder.system("游客不能处理好友申请"));
            return;
        }
        if (body == null || body.getRequestId() == null) {
            user.send(ResponseBuilder.system("好友申请 ID 不能为空"));
            return;
        }
        try {
            FriendService.respond(user, body.getRequestId(), body.isAccepted());
            user.send(ResponseBuilder.build(null,
                    FriendService.listPendingRequests(user.getAccountId()),
                    MessageType.FRIEND_REQUEST_LIST));
            user.send(ResponseBuilder.system(body.isAccepted() ? "已同意好友申请" : "已拒绝好友申请"));
        } catch (AccountException e) {
            user.send(ResponseBuilder.build(null,
                    FriendService.listPendingRequests(user.getAccountId()),
                    MessageType.FRIEND_REQUEST_LIST));
            user.send(ResponseBuilder.system(e.getMessage()));
        } catch (Exception e) {
            log.error("处理好友申请异常 accountId={} requestId={}", user.getAccountId(), body.getRequestId(), e);
            user.send(ResponseBuilder.system("处理好友申请失败,请稍后重试"));
        }
    }

}
