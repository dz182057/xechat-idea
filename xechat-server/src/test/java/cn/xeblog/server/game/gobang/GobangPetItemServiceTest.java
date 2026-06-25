package cn.xeblog.server.game.gobang;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.game.gobang.GobangDTO;
import cn.xeblog.commons.enums.Game;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class GobangPetItemServiceTest {

    private static final String ROOM_ID = "gobang-opening-room";

    @After
    public void tearDown() {
        GobangPetItemService.clearRoom(ROOM_ID);
    }

    @Test
    public void openingColorMessageOnlyMatchesBeforeGameStarted() {
        User homeowner = user(3010L, "homeowner-channel", "房主");
        User opponent = user(3011L, "opponent-channel", "对手");
        GameRoom room = room(homeowner, opponent);
        GobangDTO colorMessage = new GobangDTO(0, 0, 2);

        Assert.assertTrue(GobangPetItemService.isOpeningColorMessage(homeowner, room, colorMessage));
        Assert.assertFalse(GobangPetItemService.isOpeningColorMessage(opponent, room, colorMessage));

        GobangPetItemService.handleMove(homeowner, room, colorMessage);

        Assert.assertFalse(GobangPetItemService.isOpeningColorMessage(homeowner, room, new GobangDTO(0, 0, 1)));
    }

    private static GameRoom room(User homeowner, User opponent) {
        GameRoom room = new GameRoom();
        room.setId(ROOM_ID);
        room.setGame(Game.GOBANG);
        room.setNums(2);
        room.setHomeowner(homeowner);
        room.addUser(homeowner);
        room.addUser(opponent);
        return room;
    }

    private static User user(long accountId, String channelId, String nickname) {
        User user = new User();
        user.setAccountId(accountId);
        user.setAccount("account" + accountId);
        user.setId(channelId);
        user.setNickname(nickname);
        return user;
    }
}
