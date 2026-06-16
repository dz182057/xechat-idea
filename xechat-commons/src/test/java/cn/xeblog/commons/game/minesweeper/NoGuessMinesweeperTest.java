package cn.xeblog.commons.game.minesweeper;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NoGuessMinesweeperTest {

    @Test
    public void shouldRejectGuessOnlyBoard() {
        NoGuessMinesweeper.Board board = NoGuessMinesweeper.Board.fromMines(
                2,
                2,
                Arrays.asList(new NoGuessMinesweeper.Point(1, 1)));

        assertFalse(NoGuessMinesweeper.isSolvable(board, Arrays.asList(new NoGuessMinesweeper.Point(0, 0))));
    }

    @Test
    public void shouldUseCombinationReasoningForOneTwoOnePattern() {
        NoGuessMinesweeper.Board board = NoGuessMinesweeper.Board.fromMines(
                2,
                3,
                Arrays.asList(
                        new NoGuessMinesweeper.Point(0, 1),
                        new NoGuessMinesweeper.Point(2, 1)));

        NoGuessMinesweeper.SolveResult result = NoGuessMinesweeper.solve(
                board,
                Arrays.asList(
                        new NoGuessMinesweeper.Point(0, 0),
                        new NoGuessMinesweeper.Point(1, 0),
                        new NoGuessMinesweeper.Point(2, 0)));

        assertTrue(result.isSolved());
        assertTrue(result.getSafeKeys().contains("1:1"));
        assertEquals(new HashSet<>(Arrays.asList("0:1", "2:1")), result.getMineKeys());
    }

    @Test
    public void shouldGenerateSolvableBoard() {
        NoGuessMinesweeper.Board board = NoGuessMinesweeper.generate(
                9,
                9,
                10,
                new NoGuessMinesweeper.Point(4, 4),
                new Random(7));

        assertTrue(NoGuessMinesweeper.isSolvable(board, Arrays.asList(new NoGuessMinesweeper.Point(4, 4))));
    }
}
