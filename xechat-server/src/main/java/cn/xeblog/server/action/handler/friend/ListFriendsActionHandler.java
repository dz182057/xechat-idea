package cn.xeblog.server.action.handler.friend;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.action.handler.AbstractActionHandler;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.friend.FriendService;

/**
 * 拉取好友列表。
 *
 * @author dz
 * @date 2026/6/3
 */
@DoAction(Action.LIST_FRIENDS)
public class ListFriendsActionHandler extends AbstractActionHandler<Object> {

    @Override
    protected void process(User user, Object body) {
        if (user.isGuest()) {
            user.send(ResponseBuilder.system("游客不能查看好友列表"));
            return;
        }
        user.send(ResponseBuilder.build(null,
                FriendService.listFriends(user.getAccountId()),
                MessageType.FRIEND_LIST));
    }

}
