package cn.xeblog.server.game;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.server.cache.UserCache;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class GameRoomIdentityTest {

    @After
    public void tearDown() {
        UserCache.clear();
    }

    @Test
    public void sameAccountDifferentConnectionsUseOnePlayerIdentity() {
        User desktop = accountUser("desktop-channel", 1001L, "ash", "小智");
        User idea = accountUser("idea-channel", 1001L, "ash", "小智");
        GameRoom room = new GameRoom();
        room.setNums(2);

        Assert.assertEquals("account:1001", desktop.getIdentityKey());
        Assert.assertTrue(room.addUser(desktop));
        Assert.assertFalse("同账号不同连接不能作为两个玩家重复加入", room.addUser(idea));
        Assert.assertTrue(room.existUser(idea));
        Assert.assertEquals(1, room.getUsers().size());
        Assert.assertTrue(room.getUsers().containsKey("account:1001"));
        Assert.assertEquals("account:1001", room.getUsers().get("account:1001").getId());
    }

    @Test
    public void userCacheFindsAllConnectionsByIdentityKey() {
        User desktop = accountUser("desktop-channel", 1001L, "ash", "小智");
        User idea = accountUser("idea-channel", 1001L, "ash", "小智");
        User other = accountUser("other-channel", 1002L, "misty", "小霞");
        UserCache.add(desktop.getId(), desktop);
        UserCache.add(idea.getId(), idea);
        UserCache.add(other.getId(), other);

        List<User> users = UserCache.getByIdentityKey("account:1001");

        Assert.assertEquals(2, users.size());
        Assert.assertTrue(users.stream().anyMatch(user -> "desktop-channel".equals(user.getId())));
        Assert.assertTrue(users.stream().anyMatch(user -> "idea-channel".equals(user.getId())));
    }

    @Test
    public void guestCannotJoinGameRoom() {
        User guest = new User();
        guest.setId("guest-channel");
        guest.setUuid("guest-uuid");
        guest.setNickname("游客");
        guest.setGuest(true);
        GameRoom room = new GameRoom();
        room.setNums(2);

        Assert.assertFalse("游客不支持玩游戏，不能进入房间", room.addUser(guest));
        Assert.assertTrue(room.getUsers().isEmpty());
    }

    private static User accountUser(String channelId, long accountId, String account, String nickname) {
        User user = new User();
        user.setId(channelId);
        user.setAccountId(accountId);
        user.setAccount(account);
        user.setNickname(nickname);
        user.setUuid(channelId + "-uuid");
        return user;
    }
}
