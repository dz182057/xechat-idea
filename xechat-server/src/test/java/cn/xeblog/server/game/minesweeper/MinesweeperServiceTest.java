package cn.xeblog.server.game.minesweeper;

import cn.xeblog.commons.entity.Response;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.minesweeper.MinesweeperDTO;
import cn.xeblog.commons.enums.MessageType;
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

    private static MinesweeperDTO actionRequest(int rows, int cols, int mines, int x, int y) {
        MinesweeperDTO dto = new MinesweeperDTO("single");
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
