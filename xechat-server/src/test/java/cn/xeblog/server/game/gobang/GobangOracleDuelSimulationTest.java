package cn.xeblog.server.game.gobang;

import cn.xeblog.commons.entity.game.gobang.GobangOracleRequestDTO;
import cn.xeblog.commons.entity.game.gobang.GobangOracleResponseDTO;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class GobangOracleDuelSimulationTest {

    private static final int SIZE = 15;
    private static final int MAX_MOVES = 160;
    private static final int[][] DIRECTIONS = {
            {1, 0},
            {0, 1},
            {1, 1},
            {1, -1}
    };

    @Test
    public void oracleDuelBenchmarkAgainstLinbicheng() throws Exception {
        if (!Boolean.getBoolean("gobang.duel")) {
            return;
        }

        Engine candidate = candidateEngine(System.getProperty("gobang.candidate", "oracle"));
        Engine opponent = new PluginEngine("linbicheng", 8, 10, 1, 10);
        int openings = Integer.getInteger("gobang.openings", 8);
        int openingPlies = Integer.getInteger("gobang.openingPlies", 4);
        long seed = Long.getLong("gobang.seed", 20260703L);
        double target = Double.parseDouble(System.getProperty("gobang.target", "0.70"));

        boolean blackOnly = "black".equals(System.getProperty("gobang.sides", "paired"));
        Stats stats = duel(candidate, opponent, openings, openingPlies, seed, blackOnly);

        System.out.println(stats.summary());
        Assert.assertTrue("胜率未达到目标: " + stats.summary(), stats.winRate() >= target);
    }

    private static Engine candidateEngine(String value) throws Exception {
        if (value.startsWith("plugin:")) {
            String[] parts = value.substring("plugin:".length()).split(",");
            if (parts.length != 4) {
                throw new IllegalArgumentException("gobang.candidate 参数应为 plugin:depth,maxNodes,vcx,vcxDepth");
            }
            return new PluginEngine(value,
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]));
        }
        return new OracleEngine();
    }

    private static Stats duel(Engine candidate, Engine opponent, int openings, int openingPlies, long seed,
                              boolean blackOnly) {
        Random random = new Random(seed);
        Stats stats = new Stats(candidate.name(), opponent.name());
        for (int i = 0; i < openings; i++) {
            int[][] opening = randomOpening(random, openingPlies);
            playOne(stats, copy(opening), nextType(opening), candidate, opponent, i, false);
            if (!blackOnly) {
                playOne(stats, copy(opening), nextType(opening), opponent, candidate, i, true);
            }
        }
        return stats;
    }

    private static void playOne(Stats stats, int[][] board, int turn, Engine blackEngine, Engine whiteEngine,
                                int openingIndex, boolean candidateIsWhite) {
        int candidateType = candidateIsWhite ? 2 : 1;
        int moveSeq = countStones(board);
        while (moveSeq < MAX_MOVES && !isFull(board)) {
            Engine engine = turn == 1 ? blackEngine : whiteEngine;
            Move move = engine.move(copy(board), turn, moveSeq);
            if (!isLegal(board, move)) {
                stats.record(turn == candidateType ? -1 : 1, openingIndex, candidateIsWhite, moveSeq);
                return;
            }
            board[move.y][move.x] = turn;
            moveSeq++;
            if (isWin(board, move.x, move.y, turn)) {
                stats.record(turn == candidateType ? 1 : -1, openingIndex, candidateIsWhite, moveSeq);
                return;
            }
            turn = opponent(turn);
        }
        stats.record(0, openingIndex, candidateIsWhite, moveSeq);
    }

    private static int[][] randomOpening(Random random, int plies) {
        int[][] board = new int[SIZE][SIZE];
        if (plies <= 0) {
            return board;
        }
        board[SIZE / 2][SIZE / 2] = 1;
        int turn = 2;
        int moveSeq = 1;
        while (moveSeq < plies) {
            List<Move> candidates = openingCandidates(board);
            if (candidates.isEmpty()) {
                break;
            }
            Move move = candidates.get(random.nextInt(Math.min(candidates.size(), 8)));
            board[move.y][move.x] = turn;
            if (isWin(board, move.x, move.y, turn)) {
                board[move.y][move.x] = 0;
                continue;
            }
            turn = opponent(turn);
            moveSeq++;
        }
        return board;
    }

    private static List<Move> openingCandidates(int[][] board) {
        List<Move> moves = new ArrayList<>();
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (board[y][x] != 0 || !hasNeighbor(board, x, y, 2)) {
                    continue;
                }
                int distance = Math.abs(x - SIZE / 2) + Math.abs(y - SIZE / 2);
                int neighbors = countNeighbors(board, x, y, 1);
                moves.add(new Move(x, y, neighbors * 10 - distance));
            }
        }
        moves.sort(Comparator.comparingInt((Move move) -> move.score).reversed());
        return moves;
    }

    private interface Engine {
        String name();

        Move move(int[][] board, int type, int moveSeq);
    }

    private static final class OracleEngine implements Engine {
        @Override
        public String name() {
            return "oracle";
        }

        @Override
        public Move move(int[][] board, int type, int moveSeq) {
            GobangOracleResponseDTO response = GobangOracleService.suggest(
                    new GobangOracleRequestDTO("duel-" + moveSeq, board, type, moveSeq));
            if (!response.isSuccess() || response.getX() == null || response.getY() == null) {
                return null;
            }
            return new Move(response.getX(), response.getY(), response.getScore() == null ? 0 : response.getScore());
        }
    }

    private static final class PluginEngine implements Engine {
        private static final Constructor<?> CONSTRUCTOR;
        private static final Method GET_POINT;
        private static final Field X;
        private static final Field Y;
        private static final Field SCORE;

        static {
            try {
                Class<?> engineClass = Class.forName(
                        "cn.xeblog.server.game.gobang.GobangOracleService$PluginHardAiEngine");
                CONSTRUCTOR = engineClass.getDeclaredConstructor(
                        int[][].class, int.class, long.class, int.class, int.class, int.class, int.class);
                CONSTRUCTOR.setAccessible(true);
                GET_POINT = engineClass.getDeclaredMethod("getPoint");
                GET_POINT.setAccessible(true);
                Class<?> pointClass = Class.forName(
                        "cn.xeblog.server.game.gobang.GobangOracleService$Point");
                X = pointClass.getDeclaredField("x");
                Y = pointClass.getDeclaredField("y");
                SCORE = pointClass.getDeclaredField("score");
                X.setAccessible(true);
                Y.setAccessible(true);
                SCORE.setAccessible(true);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private final String name;
        private final int depth;
        private final int maxNodes;
        private final int vcx;
        private final int vcxDepth;

        private PluginEngine(String name, int depth, int maxNodes, int vcx, int vcxDepth) {
            this.name = name;
            this.depth = depth;
            this.maxNodes = maxNodes;
            this.vcx = vcx;
            this.vcxDepth = vcxDepth;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Move move(int[][] board, int type, int moveSeq) {
            try {
                long deadline = System.nanoTime() + Long.getLong("gobang.engineBudgetMs", 2_200L) * 1_000_000L;
                Object engine = CONSTRUCTOR.newInstance(board, type, deadline, depth, maxNodes, vcx, vcxDepth);
                Object point = GET_POINT.invoke(engine);
                if (point == null) {
                    return null;
                }
                return new Move(X.getInt(point), Y.getInt(point), SCORE.getInt(point));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static final class Stats {
        private final String candidateName;
        private final String opponentName;
        private int wins;
        private int losses;
        private int draws;
        private int games;
        private int totalMoves;

        private Stats(String candidateName, String opponentName) {
            this.candidateName = candidateName;
            this.opponentName = opponentName;
        }

        private void record(int result, int openingIndex, boolean candidateIsWhite, int moves) {
            games++;
            totalMoves += moves;
            if (result > 0) {
                wins++;
            } else if (result < 0) {
                losses++;
            } else {
                draws++;
            }
            if (Boolean.getBoolean("gobang.verbose")) {
                System.out.println("opening=" + openingIndex
                        + " candidate=" + (candidateIsWhite ? "white" : "black")
                        + " result=" + result
                        + " moves=" + moves);
            }
        }

        private double winRate() {
            return games == 0 ? 0 : (double) wins / games;
        }

        private String summary() {
            long roundedRate = Math.round(winRate() * 10_000);
            return candidateName + " vs " + opponentName
                    + " games=" + games
                    + " wins=" + wins
                    + " losses=" + losses
                    + " draws=" + draws
                    + " winRate=" + (roundedRate / 100.0) + "%"
                    + " avgMoves=" + (games == 0 ? 0 : totalMoves / games);
        }
    }

    private static final class Move {
        private final int x;
        private final int y;
        private final int score;

        private Move(int x, int y, int score) {
            this.x = x;
            this.y = y;
            this.score = score;
        }
    }

    private static boolean isLegal(int[][] board, Move move) {
        return move != null
                && move.x >= 0 && move.x < SIZE
                && move.y >= 0 && move.y < SIZE
                && board[move.y][move.x] == 0;
    }

    private static int nextType(int[][] board) {
        int black = 0;
        int white = 0;
        for (int[] row : board) {
            for (int cell : row) {
                if (cell == 1) {
                    black++;
                } else if (cell == 2) {
                    white++;
                }
            }
        }
        return black <= white ? 1 : 2;
    }

    private static int countStones(int[][] board) {
        int total = 0;
        for (int[] row : board) {
            for (int cell : row) {
                if (cell != 0) {
                    total++;
                }
            }
        }
        return total;
    }

    private static int countNeighbors(int[][] board, int x, int y, int distance) {
        int total = 0;
        for (int dy = -distance; dy <= distance; dy++) {
            for (int dx = -distance; dx <= distance; dx++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                int nx = x + dx;
                int ny = y + dy;
                if (nx >= 0 && ny >= 0 && nx < SIZE && ny < SIZE && board[ny][nx] != 0) {
                    total++;
                }
            }
        }
        return total;
    }

    private static boolean hasNeighbor(int[][] board, int x, int y, int distance) {
        return countNeighbors(board, x, y, distance) > 0;
    }

    private static boolean isWin(int[][] board, int x, int y, int type) {
        for (int[] direction : DIRECTIONS) {
            int count = 1
                    + countDirection(board, x, y, direction[0], direction[1], type)
                    + countDirection(board, x, y, -direction[0], -direction[1], type);
            if (count >= 5) {
                return true;
            }
        }
        return false;
    }

    private static int countDirection(int[][] board, int x, int y, int dx, int dy, int type) {
        int count = 0;
        int nx = x + dx;
        int ny = y + dy;
        while (nx >= 0 && ny >= 0 && nx < SIZE && ny < SIZE && board[ny][nx] == type) {
            count++;
            nx += dx;
            ny += dy;
        }
        return count;
    }

    private static boolean isFull(int[][] board) {
        for (int[] row : board) {
            for (int cell : row) {
                if (cell == 0) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int opponent(int type) {
        return type == 1 ? 2 : 1;
    }

    private static int[][] copy(int[][] board) {
        int[][] result = new int[SIZE][SIZE];
        for (int y = 0; y < SIZE; y++) {
            System.arraycopy(board[y], 0, result[y], 0, SIZE);
        }
        return result;
    }
}
