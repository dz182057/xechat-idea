package cn.xeblog.plugin.game.minesweeper;

import org.junit.Assert;
import org.junit.Test;

public class MinesweeperTest {

    @Test
    public void createRoomModeListShouldContainCoopMode() {
        Assert.assertTrue(Minesweeper.createRoomModeList().contains(Minesweeper.COOP_GAME_MODE));
    }

    @Test
    public void formatConcealedCellTextShouldUseNeutralSymbols() {
        Assert.assertEquals("3", Minesweeper.formatConcealedCellText(true, false, 3, false, false));
        Assert.assertEquals("x", Minesweeper.formatConcealedCellText(false, false, 0, true, false));
        Assert.assertEquals("o", Minesweeper.formatConcealedCellText(false, false, 0, false, true));
        Assert.assertEquals("·", Minesweeper.formatConcealedCellText(false, false, 0, false, false));
        Assert.assertEquals("!", Minesweeper.formatConcealedCellText(true, true, 0, false, false));
    }

    @Test
    public void normalizeBoardConfigShouldClampCustomSize() {
        Minesweeper.BoardConfig config = Minesweeper.normalizeBoardConfig(1, 100, 999);

        Assert.assertEquals(5, config.rows);
        Assert.assertEquals(40, config.cols);
        Assert.assertEquals(199, config.mines);
    }

    @Test
    public void concealedToolbarLabelsShouldStayLowProfile() {
        Minesweeper.ConcealedToolbarLabels labels = Minesweeper.createConcealedToolbarLabels(false);

        Assert.assertEquals("Debug", labels.title);
        Assert.assertEquals("R", labels.restart);
        Assert.assertEquals("M", labels.sharedMark);
        Assert.assertEquals("V", labels.mode);
    }

    @Test
    public void presetBoardConfigShouldMatchClassicDifficulty() {
        Minesweeper.BoardConfig easy = Minesweeper.presetBoardConfig(0);
        Minesweeper.BoardConfig medium = Minesweeper.presetBoardConfig(1);
        Minesweeper.BoardConfig hard = Minesweeper.presetBoardConfig(2);

        Assert.assertEquals(9, easy.rows);
        Assert.assertEquals(9, easy.cols);
        Assert.assertEquals(10, easy.mines);
        Assert.assertEquals(16, medium.rows);
        Assert.assertEquals(16, medium.cols);
        Assert.assertEquals(40, medium.mines);
        Assert.assertEquals(16, hard.rows);
        Assert.assertEquals(30, hard.cols);
        Assert.assertEquals(99, hard.mines);
    }

    @Test
    public void coopLastActionCellShouldMatchSameCoordinateOnly() {
        Assert.assertTrue(Minesweeper.isCoopLastActionCell(2, 3, 2, 3));
        Assert.assertFalse(Minesweeper.isCoopLastActionCell(2, 3, 3, 2));
        Assert.assertFalse(Minesweeper.isCoopLastActionCell(2, 3, null, 3));
        Assert.assertFalse(Minesweeper.isCoopLastActionCell(2, 3, 2, null));
    }
}
