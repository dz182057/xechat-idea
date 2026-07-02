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

    @Test
    public void quietBoardUsesGlobalPositionSearch() {
        int[][] board = board();
        board[5][5] = 1;
        board[9][9] = 1;
        board[6][6] = 2;
        board[8][8] = 2;

        GobangOracleResponseDTO response = GobangOracleService.suggest(
                new GobangOracleRequestDTO("req-8", board, 1, 4));

        Assert.assertTrue(response.isSuccess());
        Assert.assertTrue(response.getReason().contains("全局线势"));
    }

    @Test
    public void denseMidgameSuggestionReturnsBeforeClientTimeout() {
        int[][] board = board();
        put(board, 1,
                8, 2, 6, 3, 8, 4, 10, 5, 13, 5,
                7, 6, 11, 6, 14, 7, 7, 7, 9, 7, 11, 7,
                6, 8, 9, 8, 10, 8, 11, 8,
                6, 9, 8, 9, 9, 9, 10, 9, 11, 10, 9, 11);
        put(board, 2,
                9, 3, 7, 4, 10, 4, 8, 5, 11, 5,
                6, 6, 8, 6, 9, 6, 12, 6,
                8, 7, 10, 7, 7, 8, 8, 8, 13, 8,
                7, 9, 11, 9, 8, 10, 9, 10, 6, 11, 8, 11);

        long start = System.nanoTime();
        GobangOracleResponseDTO response = GobangOracleService.suggest(
                new GobangOracleRequestDTO("req-9", board, 2, 41));
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;

        Assert.assertTrue(response.isSuccess());
        Assert.assertTrue("服务端推荐耗时过长: " + elapsedMillis + "ms", elapsedMillis < 2_500L);
    }

    private static int[][] board() {
        return new int[15][15];
    }

    private static void put(int[][] board, int type, int... values) {
        for (int i = 0; i + 1 < values.length; i += 2) {
            board[values[i + 1]][values[i]] = type;
        }
    }

}
