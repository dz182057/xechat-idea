package cn.xeblog.server.cache;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.enums.Game;
import cn.xeblog.commons.enums.UserStatus;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class GameRoomCacheStatusTest {

    @Before
    public void setUp() {
        GameRoomCache.clear();
        UserCache.clear();
    }

    @After
    public void tearDown() {
        GameRoomCache.clear();
        UserCache.clear();
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

}
