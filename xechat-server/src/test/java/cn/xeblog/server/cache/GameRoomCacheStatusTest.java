package cn.xeblog.server.cache;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.game.gobang.GobangDTO;
import cn.xeblog.commons.enums.Game;
import cn.xeblog.commons.enums.UserStatus;
import cn.xeblog.server.game.gobang.GobangPetItemService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class GameRoomCacheStatusTest {

    @Before
    public void setUp() {
        GameRoomCache.clear();
        UserCache.clear();
        GobangPetItemService.clearRoom("room-gobang-snapshot");
    }

    @After
    public void tearDown() {
        GameRoomCache.clear();
        UserCache.clear();
        GobangPetItemService.clearRoom("room-gobang-snapshot");
    }

    @Test
    public void joinRoomMarksUserPlayingWithCurrentGame() {
        User user = user("channel-a", 1001L, "小明");
        GameRoom room = room("room-a", Game.GOBANG, 2);

        Assert.assertTrue(GameRoomCache.joinRoom(room.getId(), user));

        Assert.assertEquals(UserStatus.PLAYING, user.getStatus());
        Assert.assertEquals(Game.GOBANG, user.getCurrentGame());
    }

    @Test
    public void removeRoomClearsPlayingStatusForJoinedUsers() {
        User homeowner = user("channel-owner", 1002L, "房主");
        User opponent = user("channel-opponent", 1003L, "对手");
        UserCache.add(homeowner.getId(), homeowner);
        UserCache.add(opponent.getId(), opponent);
        GameRoom room = room("room-b", Game.QUICK_QUIZ, 2);

        Assert.assertTrue(GameRoomCache.joinRoom(room.getId(), homeowner));
        Assert.assertTrue(GameRoomCache.joinRoom(room.getId(), opponent));

        GameRoomCache.removeRoom(room.getId());

        Assert.assertEquals(UserStatus.FISHING, homeowner.getStatus());
        Assert.assertNull(homeowner.getCurrentGame());
        Assert.assertEquals(UserStatus.FISHING, opponent.getStatus());
        Assert.assertNull(opponent.getCurrentGame());
    }

    @Test
    public void disconnectKeepsSeatWhenAnotherPlayerStillActiveThenReconnects() {
        User homeowner = user("channel-owner", 1004L, "房主");
        User opponent = user("channel-opponent", 1005L, "对手");
        GameRoom room = room("room-reconnect", Game.GOBANG, 2);
        room.setHomeowner(homeowner);

        Assert.assertTrue(GameRoomCache.joinRoom(room.getId(), homeowner));
        Assert.assertTrue(GameRoomCache.joinRoom(room.getId(), opponent));

        Assert.assertFalse(GameRoomCache.disconnectRoomConnection(room.getId(), homeowner));
        Assert.assertSame(room, GameRoomCache.getGameRoom(room.getId()));
        Assert.assertNull(GameRoomCache.getGameRoomByConnectionId(homeowner.getId()));
        Assert.assertSame(room, GameRoomCache.getGameRoomByUserId(homeowner.getIdentityKey()));

        User reconnected = user("channel-owner-new", homeowner.getAccountId(), "房主");
        Assert.assertSame(room, GameRoomCache.reconnectRoom(reconnected));
        Assert.assertSame(room, GameRoomCache.getGameRoomByConnectionId(reconnected.getId()));
        Assert.assertEquals(UserStatus.PLAYING, reconnected.getStatus());
        Assert.assertEquals(Game.GOBANG, reconnected.getCurrentGame());
        Assert.assertTrue(room.isHomeowner(reconnected));
    }

    @Test
    public void tacitQuizDisconnectKeepsSeatForReconnectWhileOpponentIsActive() {
        User homeowner = user("tacit-channel-owner", 1011L, "房主");
        User opponent = user("tacit-channel-opponent", 1012L, "对手");
        GameRoom room = room("room-tacit-reconnect", Game.TACIT_QUIZ, 2);
        room.setHomeowner(homeowner);

        Assert.assertTrue(GameRoomCache.joinRoom(room.getId(), homeowner));
        Assert.assertTrue(GameRoomCache.joinRoom(room.getId(), opponent));

        Assert.assertFalse(GameRoomCache.disconnectRoomConnection(room.getId(), homeowner));
        Assert.assertSame(room, GameRoomCache.getGameRoom(room.getId()));
        Assert.assertSame(room, GameRoomCache.getGameRoomByUserId(homeowner.getIdentityKey()));

        User reconnected = user("tacit-channel-owner-new", homeowner.getAccountId(), "房主");
        Assert.assertSame(room, GameRoomCache.reconnectRoom(reconnected));
        Assert.assertSame(room, GameRoomCache.getGameRoomByConnectionId(reconnected.getId()));
        Assert.assertTrue(room.isHomeowner(reconnected));
    }

    @Test
    public void disconnectKeepsGobangRoomWhenNoActivePlayerRemainsThenReconnects() {
        GameRoomCache.setGobangEmptyRoomTtlMillisForTest(20L);
        User homeowner = user("channel-last", 1006L, "房主");
        GameRoom room = room("room-empty", Game.GOBANG, 2);
        room.setHomeowner(homeowner);

        Assert.assertTrue(GameRoomCache.joinRoom(room.getId(), homeowner));

        Assert.assertFalse(GameRoomCache.disconnectRoomConnection(room.getId(), homeowner));
        Assert.assertSame(room, GameRoomCache.getGameRoom(room.getId()));
        Assert.assertSame(room, GameRoomCache.getGameRoomByUserId(homeowner.getIdentityKey()));

        User reconnected = user("channel-last-new", homeowner.getAccountId(), "房主");
        Assert.assertSame(room, GameRoomCache.reconnectRoom(reconnected));
        Assert.assertSame(room, GameRoomCache.getGameRoomByConnectionId(reconnected.getId()));
        Assert.assertTrue(room.isHomeowner(reconnected));
        sleep(60L);
        Assert.assertSame(room, GameRoomCache.getGameRoom(room.getId()));
    }

    @Test
    public void disconnectClosesGobangRoomAfterGraceWhenNoPlayerReconnects() {
        GameRoomCache.setGobangEmptyRoomTtlMillisForTest(20L);
        User homeowner = user("channel-expire", 1009L, "房主");
        User opponent = user("channel-expire-opponent", 1010L, "对手");
        GameRoom room = room("room-expire", Game.GOBANG, 2);
        room.setHomeowner(homeowner);

        Assert.assertTrue(GameRoomCache.joinRoom(room.getId(), homeowner));
        Assert.assertTrue(GameRoomCache.joinRoom(room.getId(), opponent));

        Assert.assertFalse(GameRoomCache.disconnectRoomConnection(room.getId(), homeowner));
        Assert.assertFalse(GameRoomCache.disconnectRoomConnection(room.getId(), opponent));
        Assert.assertSame(room, GameRoomCache.getGameRoom(room.getId()));

        sleep(80L);

        Assert.assertNull(GameRoomCache.getGameRoom(room.getId()));
        Assert.assertNull(GameRoomCache.getGameRoomByUserId(homeowner.getIdentityKey()));
        Assert.assertNull(GameRoomCache.getGameRoomByUserId(opponent.getIdentityKey()));
    }

    @Test
    public void gobangSnapshotUsesReconnectingPlayersOwnTypeAndMoves() {
        User homeowner = user("channel-gobang-owner", 1007L, "房主");
        User opponent = user("channel-gobang-opponent", 1008L, "对手");
        GameRoom room = room("room-gobang-snapshot", Game.GOBANG, 2);
        room.setHomeowner(homeowner);

        Assert.assertTrue(GameRoomCache.joinRoom(room.getId(), homeowner));
        Assert.assertTrue(GameRoomCache.joinRoom(room.getId(), opponent));

        GobangPetItemService.handleMove(homeowner, room, new GobangDTO(0, 0, 2));
        GobangPetItemService.handleMove(homeowner, room, new GobangDTO(7, 7, 1));
        GobangPetItemService.handleMove(opponent, room, new GobangDTO(8, 7, 2));

        List<GobangDTO> homeownerSnapshot = GobangPetItemService.snapshotForUser(room, homeowner);
        List<GobangDTO> opponentSnapshot = GobangPetItemService.snapshotForUser(room, opponent);

        Assert.assertEquals(3, homeownerSnapshot.size());
        Assert.assertEquals(1, homeownerSnapshot.get(0).getType());
        Assert.assertEquals(7, homeownerSnapshot.get(1).getX());
        Assert.assertEquals(8, homeownerSnapshot.get(2).getX());
        Assert.assertEquals(3, opponentSnapshot.size());
        Assert.assertEquals(2, opponentSnapshot.get(0).getType());
        Assert.assertEquals(Game.GOBANG, opponentSnapshot.get(0).getGame());
        Assert.assertEquals(room.getId(), opponentSnapshot.get(0).getRoomId());
    }

    private static GameRoom room(String roomId, Game game, int nums) {
        GameRoom room = GameRoomCache.seize(roomId);
        Assert.assertNotNull(room);
        room.setGame(game);
        room.setNums(nums);
        return room;
    }

    private static User user(String channelId, long accountId, String nickname) {
        User user = new User();
        user.setId(channelId);
        user.setAccountId(accountId);
        user.setAccount("account" + accountId);
        user.setNickname(nickname);
        user.setStatus(UserStatus.FISHING);
        return user;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Assert.fail("等待五子棋空房关闭任务被中断");
        }
    }

}
