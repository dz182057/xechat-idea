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

    @Test
    public void acceptsOnlyCurrentTurnAndEmptyCell() {
        User homeowner = user(3012L, "homeowner-channel-2", "房主");
        User opponent = user(3013L, "opponent-channel-2", "对手");
        GameRoom room = room(homeowner, opponent);
        GobangPetItemService.handleMove(homeowner, room, new GobangDTO(0, 0, 2));

        GobangDTO first = GobangPetItemService.handleMove(homeowner, room, new GobangDTO(7, 7, 1));
        GobangDTO duplicate = GobangPetItemService.handleMove(opponent, room, new GobangDTO(7, 7, 2));
        GobangDTO wrongTurn = GobangPetItemService.handleMove(homeowner, room, new GobangDTO(8, 7, 1));
        GobangDTO second = GobangPetItemService.handleMove(opponent, room, new GobangDTO(8, 7, 2));

        Assert.assertNotNull(first);
        Assert.assertEquals("MOVE", first.getEvent());
        Assert.assertEquals(2, first.getTurn());
        Assert.assertEquals("playing", first.getPhase());
        Assert.assertNull(duplicate);
        Assert.assertNull(wrongTurn);
        Assert.assertNotNull(second);
        Assert.assertEquals(1, second.getTurn());

        GobangDTO rejected = GobangPetItemService.rejectedMove(room, new GobangDTO(7, 7, 2));
        Assert.assertNotNull(rejected);
        Assert.assertEquals("REJECTED", rejected.getEvent());
        Assert.assertEquals("playing", rejected.getPhase());
        Assert.assertEquals(1, rejected.getTurn());
    }

    @Test
    public void serverDecidesWinnerAndStopsFurtherMoves() {
        User homeowner = user(3014L, "homeowner-channel-3", "房主");
        User opponent = user(3015L, "opponent-channel-3", "对手");
        GameRoom room = room(homeowner, opponent);
        GobangPetItemService.handleMove(homeowner, room, new GobangDTO(0, 0, 2));

        GobangPetItemService.handleMove(homeowner, room, new GobangDTO(0, 0, 1));
        GobangPetItemService.handleMove(opponent, room, new GobangDTO(0, 1, 2));
        GobangPetItemService.handleMove(homeowner, room, new GobangDTO(1, 0, 1));
        GobangPetItemService.handleMove(opponent, room, new GobangDTO(1, 1, 2));
        GobangPetItemService.handleMove(homeowner, room, new GobangDTO(2, 0, 1));
        GobangPetItemService.handleMove(opponent, room, new GobangDTO(2, 1, 2));
        GobangPetItemService.handleMove(homeowner, room, new GobangDTO(3, 0, 1));
        GobangPetItemService.handleMove(opponent, room, new GobangDTO(3, 1, 2));
        GobangDTO winning = GobangPetItemService.handleMove(homeowner, room, new GobangDTO(4, 0, 1));
        GobangDTO afterOver = GobangPetItemService.handleMove(opponent, room, new GobangDTO(4, 1, 2));

        Assert.assertNotNull(winning);
        Assert.assertEquals("over", winning.getPhase());
        Assert.assertEquals(1, winning.getWinner());
        Assert.assertEquals(2, winning.getTurn());
        Assert.assertNull(afterOver);
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
