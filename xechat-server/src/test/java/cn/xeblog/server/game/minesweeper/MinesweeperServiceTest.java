package cn.xeblog.server.game.minesweeper;

import cn.xeblog.commons.entity.Response;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.game.minesweeper.MinesweeperDTO;
import cn.xeblog.commons.enums.Game;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.cache.UserCache;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class MinesweeperServiceTest {

    private final User user = user();

    @After
    public void tearDown() {
        MinesweeperDTO reset = new MinesweeperDTO("single");
        reset.setEvent(MinesweeperDTO.Event.SERVER_START_REQUEST);
        MinesweeperService.handleSingle(user, reset);
        drain(user);
    }

    @Test
    public void singleActionShouldRebuildWhenRequestedSizeChanges() {
        MinesweeperDTO large = actionRequest(16, 30, 99, 4, 4);
        MinesweeperService.handleSingle(user, large);
        MinesweeperDTO largeResponse = gameBody(readResponse(user));
        Assert.assertEquals(Integer.valueOf(16), largeResponse.getRows());
        Assert.assertEquals(Integer.valueOf(30), largeResponse.getCols());
        Assert.assertEquals(Integer.valueOf(99), largeResponse.getMines());

        MinesweeperDTO medium = actionRequest(16, 16, 40, 4, 4);
        MinesweeperService.handleSingle(user, medium);
        MinesweeperDTO mediumResponse = gameBody(readResponse(user));

        Assert.assertEquals(Integer.valueOf(16), mediumResponse.getRows());
        Assert.assertEquals(Integer.valueOf(16), mediumResponse.getCols());
        Assert.assertEquals(Integer.valueOf(40), mediumResponse.getMines());
    }

    @Test
    public void roomActionShouldRebuildWhenRequestedSizeChangesAfterTurnMovedToOpponent() {
        User homeowner = user("home-channel", 9101L, "home");
        User opponent = user("opponent-channel", 9102L, "opponent");
        UserCache.add(homeowner.getId(), homeowner);
        UserCache.add(opponent.getId(), opponent);
        GameRoom room = room(homeowner, opponent);
        MinesweeperService.clearRoom(room.getId());
        try {
            MinesweeperDTO large = actionRequest(room.getId(), 16, 30, 99, 4, 4);
            large.setActorKey(homeowner.getIdentityKey());
            MinesweeperService.handleRoom(homeowner, room, large);
            drain(homeowner);
            drain(opponent);

            MinesweeperDTO medium = actionRequest(room.getId(), 16, 16, 40, 4, 4);
            medium.setActorKey(homeowner.getIdentityKey());
            MinesweeperService.handleRoom(homeowner, room, medium);
            MinesweeperDTO mediumResponse = gameBody(readResponse(homeowner));

            Assert.assertEquals(Integer.valueOf(16), mediumResponse.getRows());
            Assert.assertEquals(Integer.valueOf(16), mediumResponse.getCols());
            Assert.assertEquals(Integer.valueOf(40), mediumResponse.getMines());
            Assert.assertEquals(opponent.getIdentityKey(), mediumResponse.getNextTurnPlayerKey());
        } finally {
            MinesweeperService.clearRoom(room.getId());
            UserCache.remove(homeowner.getId());
            UserCache.remove(opponent.getId());
        }
    }

    @Test
    public void roomApprovedRestartShouldClearTurnStateBeforeNextAction() {
        User homeowner = user("home-channel-restart", 9201L, "home-restart");
        User opponent = user("opponent-channel-restart", 9202L, "opponent-restart");
        UserCache.add(homeowner.getId(), homeowner);
        UserCache.add(opponent.getId(), opponent);
        GameRoom room = room("minesweeper-room-restart-test", homeowner, opponent);
        MinesweeperService.clearRoom(room.getId());
        try {
            MinesweeperDTO firstAction = actionRequest(room.getId(), 9, 9, 10, 4, 4);
            firstAction.setActorKey(homeowner.getIdentityKey());
            MinesweeperService.handleRoom(homeowner, room, firstAction);
            drain(homeowner);
            drain(opponent);

            MinesweeperDTO restart = new MinesweeperDTO(room.getId());
            restart.setEvent(MinesweeperDTO.Event.RESTART_RESPONSE);
            restart.setRestartApproved(true);
            restart.setActorKey(opponent.getIdentityKey());
            Assert.assertFalse(MinesweeperService.handleRoom(opponent, room, restart));

            MinesweeperDTO restartedAction = actionRequest(room.getId(), 9, 9, 10, 4, 4);
            restartedAction.setActorKey(homeowner.getIdentityKey());
            MinesweeperService.handleRoom(homeowner, room, restartedAction);
            MinesweeperDTO restartedResponse = gameBody(readResponse(homeowner));

            Assert.assertEquals(Integer.valueOf(9), restartedResponse.getRows());
            Assert.assertEquals(Integer.valueOf(9), restartedResponse.getCols());
            Assert.assertEquals(Integer.valueOf(10), restartedResponse.getMines());
            Assert.assertEquals(opponent.getIdentityKey(), restartedResponse.getNextTurnPlayerKey());
        } finally {
            MinesweeperService.clearRoom(room.getId());
            UserCache.remove(homeowner.getId());
            UserCache.remove(opponent.getId());
        }
    }

    @Test
    public void roomActionShouldBroadcastLastActionPositionAndActor() {
        User homeowner = user("home-channel-highlight", 9301L, "home-highlight");
        User opponent = user("opponent-channel-highlight", 9302L, "opponent-highlight");
        UserCache.add(homeowner.getId(), homeowner);
        UserCache.add(opponent.getId(), opponent);
        GameRoom room = room("minesweeper-room-highlight-test", homeowner, opponent);
        MinesweeperService.clearRoom(room.getId());
        try {
            MinesweeperDTO action = actionRequest(room.getId(), 9, 9, 10, 3, 5);
            action.setActorKey(homeowner.getIdentityKey());
            MinesweeperService.handleRoom(homeowner, room, action);
            MinesweeperDTO response = gameBody(readResponse(opponent));

            Assert.assertEquals(Integer.valueOf(3), response.getX());
            Assert.assertEquals(Integer.valueOf(5), response.getY());
            Assert.assertEquals(homeowner.getIdentityKey(), response.getActorKey());
        } finally {
            MinesweeperService.clearRoom(room.getId());
            UserCache.remove(homeowner.getId());
            UserCache.remove(opponent.getId());
        }
    }

    private static MinesweeperDTO actionRequest(int rows, int cols, int mines, int x, int y) {
        return actionRequest("single", rows, cols, mines, x, y);
    }

    private static MinesweeperDTO actionRequest(String roomId, int rows, int cols, int mines, int x, int y) {
        MinesweeperDTO dto = new MinesweeperDTO(roomId);
        dto.setEvent(MinesweeperDTO.Event.SERVER_ACTION_REQUEST);
        dto.setAction(MinesweeperDTO.ActionType.OPEN);
        dto.setRows(rows);
        dto.setCols(cols);
        dto.setMines(mines);
        dto.setX(x);
        dto.setY(y);
        return dto;
    }

    private static User user() {
        User user = new User();
        user.setId("minesweeper-test-channel");
        user.setAccountId(9001L);
        user.setAccount("minesweeper-test");
        user.setNickname("扫雷测试");
        user.setUuid("minesweeper-test-uuid");
        user.setChannel(new EmbeddedChannel());
        return user;
    }

    private static User user(String channelId, long accountId, String account) {
        User user = new User();
        user.setId(channelId);
        user.setAccountId(accountId);
        user.setAccount(account);
        user.setNickname(account);
        user.setUuid(account + "-uuid");
        user.setChannel(new EmbeddedChannel());
        return user;
    }

    private static GameRoom room(User homeowner, User opponent) {
        return room("minesweeper-room-test", homeowner, opponent);
    }

    private static GameRoom room(String roomId, User homeowner, User opponent) {
        GameRoom room = new GameRoom();
        room.setId(roomId);
        room.setGame(Game.MINESWEEPER);
        room.setNums(2);
        room.setHomeowner(homeowner);
        room.getUsers().put(homeowner.getIdentityKey(), new GameRoom.Player(homeowner));
        room.getUsers().put(opponent.getIdentityKey(), new GameRoom.Player(opponent));
        return room;
    }

    private static MinesweeperDTO gameBody(Response response) {
        Assert.assertNotNull(response);
        Assert.assertEquals(MessageType.GAME, response.getType());
        Assert.assertTrue(response.getBody() instanceof MinesweeperDTO);
        return (MinesweeperDTO) response.getBody();
    }

    private static Response readResponse(User user) {
        return ((EmbeddedChannel) user.getChannel()).readOutbound();
    }

    private static void drain(User user) {
        while (readResponse(user) != null) {
        }
    }
}
