package cn.xeblog.plugin.action.handler.command;

import cn.xeblog.commons.entity.FriendDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.Platform;
import cn.xeblog.plugin.action.ConsoleAction;
import cn.xeblog.plugin.action.MessageAction;
import cn.xeblog.plugin.annotation.DoCommand;
import cn.xeblog.plugin.cache.DataCache;
import cn.xeblog.plugin.enums.Command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;

/**
 * 查看好友列表。插件端只展示已有好友,不处理好友申请。
 *
 * @author dz
 * @date 2026/6/3
 */
@DoCommand(Command.FRIENDS)
public class FriendsCommandHandler extends AbstractCommandHandler {

    @Override
    public void process(String[] args) {
        if (DataCache.guestMode) {
            ConsoleAction.showSimpleMsg("游客模式不能查看好友列表");
            return;
        }
        MessageAction.send(null, Action.LIST_FRIENDS);
        if (DataCache.friendMap.isEmpty()) {
            ConsoleAction.showSimpleMsg("正在拉取好友列表...");
            return;
        }
        ArrayList<FriendDTO> friends = new ArrayList<>(DataCache.friendMap.values());
        friends.sort(Comparator.comparing(FriendDTO::getNickname, String.CASE_INSENSITIVE_ORDER));
        friends.forEach(friend -> ConsoleAction.showSimpleMsg(formatFriend(friend)));
    }

    @Override
    protected boolean check(String[] args) {
        if (!DataCache.isOnline) {
            ConsoleAction.showLoginMsg();
            return false;
        }
        return true;
    }

    private String formatFriend(FriendDTO friend) {
        String status = friend.isOnline() ? "在线" : "离线";
        String platforms = friend.isOnline() ? formatPlatforms(friend.getPlatforms()) : "";
        return friend.getNickname() + " (" + friend.getAccount() + ") " + status + platforms;
    }

    private String formatPlatforms(Set<Platform> platforms) {
        if (platforms == null || platforms.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(" ");
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
        return sb.toString();
    }

}
