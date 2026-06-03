package cn.xeblog.plugin.action.handler.message;

import cn.xeblog.commons.entity.Response;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.plugin.action.MessageAction;
import cn.xeblog.plugin.annotation.DoMessage;
import cn.xeblog.plugin.cache.DataCache;

/**
 * 好友关系变化后重新拉好友列表。
 *
 * @author dz
 * @date 2026/6/3
 */
@DoMessage(MessageType.FRIEND_UPDATED)
public class FriendUpdatedMessageHandler extends AbstractMessageHandler<Object> {

    @Override
    protected void process(Response<Object> response) {
        if (!DataCache.guestMode) {
            MessageAction.send(null, Action.LIST_FRIENDS);
        }
    }

}
