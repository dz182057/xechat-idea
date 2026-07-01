package cn.xeblog.server.action.handler;

import cn.xeblog.commons.entity.Response;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.GameInviteDTO;
import cn.xeblog.commons.entity.game.GameInviteResultDTO;
import cn.xeblog.commons.entity.game.GamePlayerPetItemsDTO;
import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.game.GameRoomMsgDTO;
import cn.xeblog.commons.entity.game.dogbattle.DogBattleDTO;
import cn.xeblog.commons.entity.game.gobang.GobangDTO;
import cn.xeblog.commons.enums.Game;
import cn.xeblog.commons.enums.InviteStatus;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.commons.enums.UserStatus;
import cn.xeblog.server.cache.GameRoomCache;
import cn.xeblog.server.cache.UserCache;
import cn.xeblog.server.game.dogbattle.DogBattleService;
import cn.xeblog.server.game.gobang.GobangPetItemService;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

public class GameRoomEndpointRoutingTest {

    private final String roomId = "endpoint-routing-room";
    private final String dogBattleRoomId = "dog-battle-reconnect-routing-room";
    private final String gobangRegretRoomId = "gobang-regret-routing-room";

    @After
    public void tearDown() {
        GameRoomCache.removeRoom(roomId);
        GameRoomCache.removeRoom(dogBattleRoomId);
        GameRoomCache.removeRoom(gobangRegretRoomId);
        DogBattleService.clearRoom(dogBattleRoomId);
        GobangPetItemService.clearRoom(gobangRegretRoomId);
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
                GameRoomMsgDTO.MsgType.PLAYER_READY,
                new GamePlayerPetItemsDTO(null, null));
        handler.process(acceptedEndpoint, room, ready);

        GameRoom.Player acceptedPlayer = room.getUsers().get(acceptedEndpoint.getIdentityKey());
        Assert.assertNull("空携带道具不应写入房间状态", acceptedPlayer.getPetPlayItemId());
        Assert.assertNull("空携带道具不应写入房间状态", acceptedPlayer.getPetInteractionItemId());

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

    @Test
    public void dogBattleReconnectSnapshotOnlySendsToReconnectingPlayer() throws Exception {
        User left = user("left-channel", 2001L, "左侧");
        User right = user("right-channel", 2002L, "右侧");
        addOnline(left);
        addOnline(right);

        GameRoom room = GameRoomCache.seize(dogBattleRoomId);
        room.setGame(Game.DOG_BATTLE);
        room.setNums(2);
        room.setHomeowner(left);
        room.addUser(left);
        room.addUser(right);

        GameRoomActionHandler handler = new GameRoomActionHandler();
        GameRoomMsgDTO started = new GameRoomMsgDTO(room.getId(), Game.DOG_BATTLE,
                GameRoomMsgDTO.MsgType.PLAYER_GAME_STARTED, null);

        handler.process(left, room, started);
        Response leftWaiting = readResponse(left);
        Assert.assertEquals(MessageType.GAME_ROOM, leftWaiting.getType());
        Assert.assertNull(readResponse(right));
        drain(left);
        drain(right);

        handler.process(right, room, started);
        Response leftMatchStart = readResponse(left);
        Response rightMatchStart = readResponse(right);
        Assert.assertEquals("MATCH_START", ((DogBattleDTO) leftMatchStart.getBody()).getEvent());
        Assert.assertEquals("MATCH_START", ((DogBattleDTO) rightMatchStart.getBody()).getEvent());
        Response homeownerNotice = readResponse(left);
        Assert.assertEquals(MessageType.GAME_ROOM, homeownerNotice.getType());
        drain(left);
        drain(right);

        handler.process(left, room, started);
        Response leftSnapshot = readResponse(left);
        Assert.assertEquals(MessageType.GAME, leftSnapshot.getType());
        Assert.assertEquals("SNAPSHOT", ((DogBattleDTO) leftSnapshot.getBody()).getEvent());
        drain(left);
        Assert.assertNull("重连 snapshot 不应广播给对手", readResponse(right));

        DogBattleService.clearRoom(room.getId());
    }

    @Test
    public void agreedGobangRegretRollsBackAuthoritativeBoard() throws Exception {
        User homeowner = user("gobang-owner-channel", 3001L, "黑棋");
        User opponent = user("gobang-opponent-channel", 3002L, "白棋");
        addOnline(homeowner);
        addOnline(opponent);

        GameRoom room = GameRoomCache.seize(gobangRegretRoomId);
        room.setGame(Game.GOBANG);
        room.setNums(2);
        room.setHomeowner(homeowner);
        room.addUser(homeowner);
        room.addUser(opponent);

        GobangPetItemService.handleMove(homeowner, room, new GobangDTO(0, 0, 2));
        Assert.assertNotNull(GobangPetItemService.handleMove(homeowner, room, new GobangDTO(7, 7, 1)));
        Assert.assertNotNull(GobangPetItemService.handleMove(opponent, room, new GobangDTO(8, 7, 2)));

        GameRoomActionHandler handler = new GameRoomActionHandler();
        GameRoomMsgDTO agreed = new GameRoomMsgDTO(room.getId(), Game.GOBANG,
                GameRoomMsgDTO.MsgType.REGRET_RESPONSE, true);
        handler.process(opponent, room, agreed);
        drain(homeowner);
        drain(opponent);

        GobangDTO replay = GobangPetItemService.handleMove(homeowner, room, new GobangDTO(7, 7, 1));

        Assert.assertNotNull("同意悔棋后，被撤销的原落点应可重新落子", replay);
        Assert.assertEquals("MOVE", replay.getEvent());
        Assert.assertEquals(2, replay.getTurn());
        Assert.assertEquals(1, replay.getMoveSeq());
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
