package cn.xeblog.server.action;

import cn.xeblog.commons.entity.*;
import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.game.GameRoomMsgDTO;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.cache.GameRoomCache;
import cn.xeblog.server.cache.UserCache;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.factory.ObjectFactory;
import cn.xeblog.server.friend.FriendService;
import cn.xeblog.server.game.dogbattle.DogBattleService;
import cn.xeblog.server.game.dograce.DogRaceService;
import cn.xeblog.server.game.minesweeper.MinesweeperService;
import cn.xeblog.server.game.quickquiz.QuickQuizService;
import cn.xeblog.server.game.tacitquiz.TacitQuizService;
import cn.xeblog.server.game.turtlesoup.TurtleSoupService;
import cn.xeblog.server.pet.PetGameItemDeclarationService;
import cn.xeblog.server.service.AbstractResponseHistoryService;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import lombok.extern.slf4j.Slf4j;

/**
 * @author anlingyi
 * @date 2020/8/14
 */
@Slf4j
public class ChannelAction {

    private static final ChannelGroup GROUP = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    public static void send(Response resp) {
        GROUP.writeAndFlush(resp);
        if (resp.getType() == MessageType.SYSTEM || resp.getType() == MessageType.USER) {
            ObjectFactory.getObject(AbstractResponseHistoryService.class).addHistory(resp);
        }
    }

    public static void send(ChannelHandlerContext ctx, Object body, MessageType messageType) {
        send(getUser(ctx), body, messageType);
    }

    public static void send(User user, Object body, MessageType messageType) {
        send(ResponseBuilder.build(user, body, messageType));
    }

    public static void add(Channel channel) {
        GROUP.add(channel);
    }

    public static String getId(ChannelHandlerContext ctx) {
        return ctx.channel().id().asLongText();
    }

    public static User getUser(ChannelHandlerContext ctx) {
        return getUser(getId(ctx));
    }

    public static User getUser(String id) {
        return UserCache.get(id);
    }

    public static void sendOnlineUsers() {
        for (User viewer : UserCache.listUser()) {
            sendOnlineUsers(viewer);
        }
    }

    public static void sendOnlineUsers(User user) {
        // 账号体系: 在线列表按 accountId 去重,每条带 platforms 集合
        Response response = ResponseBuilder.build(null,
                new UserListMsgDTO(listOnlineByViewer(user)),
                MessageType.ONLINE_USERS);
        if (user != null) {
            user.send(response);
        }
    }

    private static java.util.List<User> listOnlineByViewer(User viewer) {
        java.util.List<User> visible = new java.util.ArrayList<>();
        for (User target : UserCache.listOnlineByAccount()) {
            if (canSeeOnline(viewer, target)) {
                visible.add(target);
            }
        }
        return visible;
    }

    private static boolean canSeeOnline(User viewer, User target) {
        if (viewer == null) {
            return !target.isStealth();
        }
        if (viewer.getId() != null && viewer.getId().equals(target.getId())) {
            return true;
        }
        if (viewer.getAccountId() > 0 && viewer.getAccountId() == target.getAccountId()) {
            return true;
        }
        return !target.isStealth();
    }

    public static void cleanUser(ChannelHandlerContext ctx) {
        cleanUser(getId(ctx));
    }

    public static User cleanUser(String id) {
        log.debug("清理用户, id -> {}", id);

        User user = getUser(id);
        if (user == null) {
            return null;
        }

        log.debug("清理用户, username -> {}", user.getUsername());

        // 游客上下线在运维日志里单独标识,便于观察访客活跃度
        if (user.isGuest()) {
            log.info("游客 {} 下线 platform={}", user.getNickname(), user.getPlatform());
        }

        GameRoom gameRoom = GameRoomCache.getGameRoomByConnectionId(user.getId());
        if (gameRoom != null) {
            GameRoomMsgDTO msg = new GameRoomMsgDTO(
                    gameRoom.getId(),
                    gameRoom.getGame(),
                    GameRoomMsgDTO.MsgType.PLAYER_LEFT,
                    null);
            Response response = ResponseBuilder.build(user, msg, MessageType.GAME_ROOM);
            gameRoom.getUsers().forEach((k, v) -> {
                if (v.isConnection(user)) {
                    return;
                }

                User player = UserCache.get(v.getChannelId());
                if (player != null) {
                    player.send(response);
                }
            });
            boolean removed = GameRoomCache.disconnectRoomConnection(gameRoom.getId(), user);
            if (removed) {
                PetGameItemDeclarationService.releaseReservedForRoom(gameRoom);
                clearGameRoomState(gameRoom);
            }
        }

        UserCache.remove(id);
        if (user.getAccountId() > 0 && UserCache.isOnlineByAccount(user.getAccountId())) {
            sendOnlineUsers();
        } else {
            sendUserState(user, UserStateMsgDTO.State.OFFLINE);
        }
        if (!user.isGuest() && user.getAccountId() > 0) {
            FriendService.pushFriendListRefreshForAccount(user.getAccountId());
        }

        return user;
    }

    public static void updateUserStatus(User user) {
        send(ResponseBuilder.build(user, null, MessageType.STATUS_UPDATE));
    }

    public static void sendUserState(User user, UserStateMsgDTO.State state) {
        Response response = ResponseBuilder.build(null, new UserStateMsgDTO(user, state), MessageType.USER_STATE);
        for (User viewer : UserCache.listUser()) {
            if (canSeeOnline(viewer, user)) {
                viewer.send(response);
            }
        }
    }

    private static void clearGameRoomState(GameRoom gameRoom) {
        QuickQuizService.clearRoom(gameRoom);
        TacitQuizService.clearRoom(gameRoom);
        MinesweeperService.clearRoom(gameRoom.getId());
        TurtleSoupService.clearRoom(gameRoom.getId());
        DogRaceService.clearRoom(gameRoom.getId());
        DogBattleService.clearRoom(gameRoom.getId());
    }

}
