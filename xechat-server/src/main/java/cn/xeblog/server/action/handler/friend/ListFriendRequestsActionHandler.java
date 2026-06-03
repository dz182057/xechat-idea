package cn.xeblog.server.action.handler.friend;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.action.handler.AbstractActionHandler;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.friend.FriendService;

/**
 * 拉取待处理好友申请。
 *
 * @author dz
 * @date 2026/6/3
 */
@DoAction(Action.LIST_FRIEND_REQUESTS)
public class ListFriendRequestsActionHandler extends AbstractActionHandler<Object> {

    @Override
    protected void process(User user, Object body) {
        if (user.isGuest()) {
            user.send(ResponseBuilder.system("游客不能查看好友申请"));
            return;
        }
        user.send(ResponseBuilder.build(null,
                FriendService.listPendingRequests(user.getAccountId()),
                MessageType.FRIEND_REQUEST_LIST));
    }

}
