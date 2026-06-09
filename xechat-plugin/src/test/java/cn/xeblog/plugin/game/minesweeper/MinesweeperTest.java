package cn.xeblog.plugin.game.minesweeper;

import org.junit.Assert;
import org.junit.Test;

public class MinesweeperTest {

    @Test
    public void createRoomModeListShouldContainCoopMode() {
        Assert.assertTrue(Minesweeper.createRoomModeList().contains(Minesweeper.COOP_GAME_MODE));
    }
}
