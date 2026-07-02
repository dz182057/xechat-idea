package cn.xeblog.server.game.gobang;

import cn.xeblog.commons.entity.game.gobang.GobangOracleRequestDTO;
import cn.xeblog.commons.entity.game.gobang.GobangOracleResponseDTO;
import org.junit.Assert;
import org.junit.Test;

public class GobangOracleServiceTest {

    @Test
    public void emptyBoardSuggestsCenter() {
        GobangOracleResponseDTO response = GobangOracleService.suggest(
                new GobangOracleRequestDTO("req-1", board(), 1, 0));

        Assert.assertTrue(response.isSuccess());
        Assert.assertEquals(Integer.valueOf(7), response.getX());
        Assert.assertEquals(Integer.valueOf(7), response.getY());
    }

    @Test
    public void directWinTakesPriority() {
        int[][] board = board();
        board[7][4] = 1;
        board[7][5] = 1;
        board[7][6] = 1;
        board[7][7] = 1;

        GobangOracleResponseDTO response = GobangOracleService.suggest(
                new GobangOracleRequestDTO("req-2", board, 1, 4));

        Assert.assertTrue(response.isSuccess());
        Assert.assertTrue(response.getX() == 3 || response.getX() == 8);
        Assert.assertEquals(Integer.valueOf(7), response.getY());
        Assert.assertTrue(response.getReason().contains("直接连成五子"));
    }

    @Test
    public void directOpponentWinMustBeBlocked() {
        int[][] board = board();
        board[7][4] = 2;
        board[7][5] = 2;
        board[7][6] = 2;
        board[7][7] = 2;

        GobangOracleResponseDTO response = GobangOracleService.suggest(
                new GobangOracleRequestDTO("req-3", board, 1, 4));

        Assert.assertTrue(response.isSuccess());
        Assert.assertTrue(response.getX() == 3 || response.getX() == 8);
        Assert.assertEquals(Integer.valueOf(7), response.getY());
        Assert.assertTrue(response.getReason().contains("对手下一手成五点"));
    }

    @Test
    public void opponentLiveFourSetupMustBeOccupiedBeforeItForms() {
        int[][] board = board();
        board[7][5] = 2;
        board[7][6] = 2;
        board[7][8] = 2;
        board[6][7] = 1;
        board[8][7] = 1;

        GobangOracleResponseDTO response = GobangOracleService.suggest(
                new GobangOracleRequestDTO("req-4", board, 1, 5));

        Assert.assertTrue(response.isSuccess());
        Assert.assertEquals(Integer.valueOf(7), response.getX());
        Assert.assertEquals(Integer.valueOf(7), response.getY());
        Assert.assertTrue(response.getReason().contains("VCF"));
    }

    @Test
    public void vcfAttackTakesOpenFourEntry() {
        int[][] board = board();
        board[7][5] = 1;
        board[7][6] = 1;
        board[7][8] = 1;
        board[6][7] = 2;
        board[8][7] = 2;

        GobangOracleResponseDTO response = GobangOracleService.suggest(
                new GobangOracleRequestDTO("req-5", board, 1, 5));

        Assert.assertTrue(response.isSuccess());
        Assert.assertEquals(Integer.valueOf(7), response.getX());
        Assert.assertEquals(Integer.valueOf(7), response.getY());
        Assert.assertTrue(response.getReason().contains("VCF"));
    }

    @Test
    public void vcfDefenseOccupiesOpponentForcingEntry() {
        int[][] board = board();
        board[7][5] = 2;
        board[7][6] = 2;
        board[7][8] = 2;
        board[6][7] = 1;
        board[8][7] = 1;

        GobangOracleResponseDTO response = GobangOracleService.suggest(
                new GobangOracleRequestDTO("req-6", board, 1, 5));

        Assert.assertTrue(response.isSuccess());
        Assert.assertEquals(Integer.valueOf(7), response.getX());
        Assert.assertEquals(Integer.valueOf(7), response.getY());
        Assert.assertTrue(response.getReason().contains("VCF"));
    }

    @Test
    public void vctAttackTakesDoubleLiveThreeEntry() {
        int[][] board = board();
        board[7][6] = 1;
        board[7][8] = 1;
        board[6][7] = 1;
        board[8][7] = 1;
        board[5][5] = 2;
        board[9][9] = 2;

        GobangOracleResponseDTO response = GobangOracleService.suggest(
                new GobangOracleRequestDTO("req-7", board, 1, 6));

        Assert.assertTrue(response.isSuccess());
        Assert.assertEquals(Integer.valueOf(7), response.getX());
        Assert.assertEquals(Integer.valueOf(7), response.getY());
        Assert.assertTrue(response.getReason().contains("VCT"));
    }

    private static int[][] board() {
        return new int[15][15];
    }

}
