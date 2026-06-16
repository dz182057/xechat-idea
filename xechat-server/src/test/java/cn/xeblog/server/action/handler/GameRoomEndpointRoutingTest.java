package cn.xeblog.server.action.handler;

import cn.xeblog.commons.entity.Response;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.GameInviteDTO;
import cn.xeblog.commons.entity.game.GameInviteResultDTO;
import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.game.GameRoomMsgDTO;
import cn.xeblog.commons.enums.Game;
import cn.xeblog.commons.enums.InviteStatus;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.commons.enums.UserStatus;
import cn.xeblog.server.cache.GameRoomCache;
import cn.xeblog.server.cache.UserCache;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

public class GameRoomEndpointRoutingTest {

    private final String roomId = "endpoint-routing-room";

    @After
    public void tearDown() {
        GameRoomCache.removeRoom(roomId);
        UserCache.clear();
    }

    @Test
    public void inviteAllOnlineEndpointsButRouteGameEventsOnlyToAcceptedEndpoint() throws Exception {
        User homeowner = user("owner-channel", 1001L, "房主");
        User acceptedEndpoint = user("mobile-channel", 1002L, "玩家");
        User otherEndpoint = user("plugin-channel", 1002L, "玩家");
        addOnline(homeowner);
        addOnline(acceptedEndpoint);
        addOnline(otherEndpoint);

        GameRoom room = GameRoomCache.seize(roomId);
        room.setGame(Game.GOBANG);
        room.setNums(2);
        room.setHomeowner(homeowner);
        room.addUser(homeowner);

        GameRoomActionHandler handler = new GameRoomActionHandler();
        GameRoomMsgDTO invite = new GameRoomMsgDTO(roomId, Game.GOBANG,
                GameRoomMsgDTO.MsgType.PLAYER_INVITE,
                new GameInviteDTO(acceptedEndpoint.getIdentityKey()));

        handler.process(homeowner, room, invite);

        Assert.assertEquals(MessageType.GAME_ROOM, readResponse(acceptedEndpoint).getType());
        Assert.assertEquals(MessageType.GAME_ROOM, readResponse(otherEndpoint).getType());
        drain(homeowner);

        GameInviteResultDTO result = new GameInviteResultDTO(InviteStatus.ACCEPT, null,
                acceptedEndpoint.getIdentityKey());
        GameRoomMsgDTO accept = new GameRoomMsgDTO(roomId, Game.GOBANG,
                GameRoomMsgDTO.MsgType.PLAYER_INVITE_RESULT, result);
        handler.process(acceptedEndpoint, room, accept);
        drain(homeowner);
        drain(acceptedEndpoint);
        drain(otherEndpoint);

        GameInviteResultDTO timeoutResult = new GameInviteResultDTO(InviteStatus.TIMEOUT, null,
                acceptedEndpoint.getIdentityKey());
        GameRoomMsgDTO timeout = new GameRoomMsgDTO(roomId, Game.GOBANG,
                GameRoomMsgDTO.MsgType.PLAYER_INVITE_RESULT, timeoutResult);
        handler.process(otherEndpoint, room, timeout);

        Assert.assertEquals(UserStatus.PLAYING, acceptedEndpoint.getStatus());
        Assert.assertNull(readResponse(homeowner));
        Assert.assertNull(readResponse(acceptedEndpoint));
        Assert.assertNull(readResponse(otherEndpoint));

        GameRoomMsgDTO ready = new GameRoomMsgDTO(roomId, null,
                GameRoomMsgDTO.MsgType.PLAYER_READY, null);
        handler.process(acceptedEndpoint, room, ready);

        Response homeownerReadyResponse = readResponse(homeowner);
        Response acceptedReadyResponse = readResponse(acceptedEndpoint);
        Assert.assertEquals(MessageType.GAME_ROOM, homeownerReadyResponse.getType());
        Assert.assertEquals(MessageType.GAME_ROOM, acceptedReadyResponse.getType());
        Assert.assertEquals(Game.GOBANG, ((GameRoomMsgDTO) homeownerReadyResponse.getBody()).getGame());
        Assert.assertEquals(Game.GOBANG, ((GameRoomMsgDTO) acceptedReadyResponse.getBody()).getGame());
        Assert.assertNull("未接受邀请的同账号其它端不应收到游戏房间事件", readResponse(otherEndpoint));

        handler.process(otherEndpoint, room, ready);

        Assert.assertNull("未绑定进房间的同账号其它端不能代替玩家触发房间广播", readResponse(homeowner));
        Assert.assertNull(readResponse(acceptedEndpoint));
        Assert.assertNull(readResponse(otherEndpoint));
    }

    private static User user(String channelId, long accountId, String username) {
        User user = new User();
        user.setId(channelId);
        user.setAccountId(accountId);
        user.setAccount("account-" + accountId);
        user.setNickname(username);
        user.setUuid(channelId + "-uuid");
        user.setStatus(UserStatus.FISHING);
        user.setChannel(new EmbeddedChannel());
        return user;
    }

    private static Response readResponse(User user) {
        return ((EmbeddedChannel) user.getChannel()).readOutbound();
    }

    private static void drain(User user) {
        while (readResponse(user) != null) {
        }
    }

    @SuppressWarnings("unchecked")
    private static void addOnline(User user) throws Exception {
        Field idToUserField = UserCache.class.getDeclaredField("ID_TO_USER");
        idToUserField.setAccessible(true);
        ((Map<String, User>) idToUserField.get(null)).put(user.getId(), user);

        Field accountToIdsField = UserCache.class.getDeclaredField("ACCOUNT_TO_IDS");
        accountToIdsField.setAccessible(true);
        Map<Long, Set<String>> accountToIds = (Map<Long, Set<String>>) accountToIdsField.get(null);
        accountToIds.computeIfAbsent(user.getAccountId(), key -> java.util.concurrent.ConcurrentHashMap.newKeySet())
                .add(user.getId());
    }
}
