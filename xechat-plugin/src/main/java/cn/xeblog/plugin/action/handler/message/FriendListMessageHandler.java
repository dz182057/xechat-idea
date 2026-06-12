package cn.xeblog.plugin.action.handler.message;

import cn.xeblog.commons.entity.FriendDTO;
import cn.xeblog.commons.entity.FriendListMsgDTO;
import cn.xeblog.commons.entity.Response;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.commons.enums.Platform;
import cn.xeblog.plugin.action.ConsoleAction;
import cn.xeblog.plugin.annotation.DoMessage;
import cn.xeblog.plugin.cache.DataCache;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 处理服务端好友列表,只做账号映射缓存。
 *
 * @author dz
 * @date 2026/6/3
 */
@DoMessage(MessageType.FRIEND_LIST)
public class FriendListMessageHandler extends AbstractMessageHandler<FriendListMsgDTO> {

    private static final DateTimeFormatter NOTICE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MM/dd HH:mm:ss");

    @Override
    protected void process(Response<FriendListMsgDTO> response) {
        FriendListMsgDTO body = response.getBody();
        Map<String, FriendDTO> old = DataCache.friendMap;
        Map<String, FriendDTO> next = new ConcurrentHashMap<>();
        if (body != null && body.getFriends() != null) {
            for (FriendDTO friend : body.getFriends()) {
                if (friend == null || friend.getNickname() == null || friend.getAccount() == null) {
                    continue;
                }
                next.put(friend.getNickname(), friend);
                notifyOnlineChanged(old, friend);
                DataCache.peerAccountByUsername.put(friend.getNickname(), friend.getAccount());
                DataCache.peerNicknameByAccount.put(friend.getAccount(), friend.getNickname());
                if (friend.getAccountId() != null) {
                    DataCache.peerAccountIdByAccount.put(friend.getAccount(), friend.getAccountId());
                }
            }
        }
        DataCache.friendMap = next;
    }

    private void notifyOnlineChanged(Map<String, FriendDTO> old, FriendDTO current) {
        if (old == null || old.isEmpty()) {
            return;
        }
        FriendDTO previous = old.get(current.getNickname());
        if (previous == null || previous.isOnline() == current.isOnline()) {
            return;
        }
        ConsoleAction.showSimpleMsg(buildOnlineChangedMessage(currentNoticeTime(), current));
    }

    static String buildOnlineChangedMessage(String time, FriendDTO current) {
        if (current.isOnline()) {
            return "[" + time + "] 好友 " + current.getNickname() + " 已上线" + formatPlatforms(current.getPlatforms());
        }
        return "[" + time + "] 好友 " + current.getNickname() + " 已离线";
    }

    private static String currentNoticeTime() {
        return NOTICE_TIME_FORMATTER.format(LocalDateTime.now());
    }

    private static String formatPlatforms(Set<Platform> platforms) {
        if (platforms == null || platforms.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(" (");
        boolean first = true;
        for (Platform platform : platforms) {
            if (!first) {
                sb.append("/");
            }
            first = false;
            if (platform == Platform.DESKTOP) {
                sb.append("桌面");
            } else if (platform == Platform.IDEA) {
                sb.append("IDEA");
            } else {
                sb.append("Web");
            }
        }
        sb.append(")");
        return sb.toString();
    }

}
