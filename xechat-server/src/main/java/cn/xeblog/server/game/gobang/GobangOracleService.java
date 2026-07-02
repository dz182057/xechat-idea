package cn.xeblog.server.game.gobang;

import cn.xeblog.commons.entity.game.gobang.GobangOracleRequestDTO;
import cn.xeblog.commons.entity.game.gobang.GobangOracleResponseDTO;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 五子棋天元罗盘推荐服务。
 */
public final class GobangOracleService {

    private static final int SIZE = 15;
    private static final int[][] DIRECTIONS = {
            {1, 0},
            {0, 1},
            {1, 1},
            {1, -1}
    };
    private static final int WIN_SCORE = 1_000_000;
    private static final int SEARCH_DEPTH = 4;
    private static final int MAX_CANDIDATES = 10;

    private GobangOracleService() {
    }

    public static GobangOracleResponseDTO suggest(GobangOracleRequestDTO request) {
        GobangOracleResponseDTO response = new GobangOracleResponseDTO();
        if (request != null) {
            response.setRequestId(request.getRequestId());
            response.setMoveSeq(request.getMoveSeq());
        }
        try {
            if (request == null) {
                return fail(response, "请求为空");
            }
            int type = request.getType();
            if (type != 1 && type != 2) {
                return fail(response, "棋子类型无效");
            }
            int[][] board = normalizeBoard(request.getBoard());
            Point directWin = findWinningPoint(board, type);
            if (directWin != null) {
                return success(response, directWin, type, WIN_SCORE, "服务端 AI 判断这一步可以直接连成五子，优先取胜。");
            }
            Point directBlock = findWinningPoint(board, opponent(type));
            if (directBlock != null) {
                return success(response, directBlock, type, WIN_SCORE - 1, "服务端 AI 判断这里是对手下一手成五点，先封住避免立刻输棋。");
            }
            Point best = searchBestPoint(board, type);
            if (best == null) {
                return fail(response, "暂未找到可推荐的落点");
            }
            return success(response, best, type, best.score,
                    "服务端 AI 已按困难档候选搜索和 Alpha-Beta 剪枝评估，认为这里的后续局面最好。");
        } catch (IllegalArgumentException e) {
            return fail(response, e.getMessage());
        }
    }

    private static GobangOracleResponseDTO success(GobangOracleResponseDTO response, Point point, int type,
                                                   int score, String reason) {
        response.setX(point.x);
        response.setY(point.y);
        response.setType(type);
        response.setScore(score);
        response.setReason(reason);
        response.setSuccess(true);
        response.setError(null);
        return response;
    }

    private static GobangOracleResponseDTO fail(GobangOracleResponseDTO response, String error) {
        response.setSuccess(false);
        response.setError(error);
        return response;
    }

    private static int[][] normalizeBoard(int[][] input) {
        if (input == null || input.length != SIZE) {
            throw new IllegalArgumentException("棋盘尺寸无效");
        }
        int[][] board = new int[SIZE][SIZE];
        for (int y = 0; y < SIZE; y++) {
            if (input[y] == null || input[y].length != SIZE) {
                throw new IllegalArgumentException("棋盘尺寸无效");
            }
            for (int x = 0; x < SIZE; x++) {
                int cell = input[y][x];
                if (cell != 0 && cell != 1 && cell != 2) {
                    throw new IllegalArgumentException("棋盘内容无效");
                }
                board[y][x] = cell;
            }
        }
        return board;
    }

    private static Point searchBestPoint(int[][] board, int type) {
        List<Point> candidates = topCandidates(board, type, MAX_CANDIDATES);
        if (candidates.isEmpty()) {
            return isEmptyBoard(board) ? new Point(SIZE / 2, SIZE / 2, 0) : null;
        }
        Point best = null;
        int alpha = -Integer.MAX_VALUE;
        int beta = Integer.MAX_VALUE;
        for (Point point : candidates) {
            board[point.y][point.x] = type;
            int score = minimax(board, opponent(type), type, SEARCH_DEPTH - 1, alpha, beta);
            board[point.y][point.x] = 0;
            point.score = score + point.score / 10;
            if (best == null || point.score > best.score) {
                best = point;
            }
            alpha = Math.max(alpha, point.score);
        }
        return best;
    }

    private static int minimax(int[][] board, int currentType, int rootType, int depth, int alpha, int beta) {
        Point rootWin = findWinningPoint(board, rootType);
        if (rootWin != null) {
            return WIN_SCORE + depth;
        }
        Point opponentWin = findWinningPoint(board, opponent(rootType));
        if (opponentWin != null) {
            return -WIN_SCORE - depth;
        }
        if (depth <= 0 || isFull(board)) {
            return evaluateBoard(board, rootType) - evaluateBoard(board, opponent(rootType));
        }

        boolean maximizing = currentType == rootType;
        int best = maximizing ? -Integer.MAX_VALUE : Integer.MAX_VALUE;
        List<Point> candidates = topCandidates(board, currentType, MAX_CANDIDATES);
        for (Point point : candidates) {
            board[point.y][point.x] = currentType;
            int score = minimax(board, opponent(currentType), rootType, depth - 1, alpha, beta);
            board[point.y][point.x] = 0;
            if (maximizing) {
                best = Math.max(best, score);
                alpha = Math.max(alpha, best);
            } else {
                best = Math.min(best, score);
                beta = Math.min(beta, best);
            }
            if (beta <= alpha) {
                break;
            }
        }
        return best;
    }

    private static List<Point> topCandidates(int[][] board, int type, int limit) {
        List<Point> points = new ArrayList<>();
        if (isEmptyBoard(board)) {
            points.add(new Point(SIZE / 2, SIZE / 2, WIN_SCORE / 10));
            return points;
        }
        int opponent = opponent(type);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (board[y][x] != 0 || !hasNeighbor(board, x, y, 2)) {
                    continue;
                }
                int attackScore = evaluateMove(board, x, y, type);
                int defenseScore = evaluateMove(board, x, y, opponent);
                int centerScore = 14 - Math.abs(x - SIZE / 2) - Math.abs(y - SIZE / 2);
                points.add(new Point(x, y, attackScore + defenseScore * 9 / 10 + centerScore));
            }
        }
        points.sort(Comparator.comparingInt((Point point) -> point.score).reversed());
        if (points.size() > limit) {
            return new ArrayList<>(points.subList(0, limit));
        }
        return points;
    }

    private static Point findWinningPoint(int[][] board, int type) {
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (board[y][x] != 0) {
                    continue;
                }
                board[y][x] = type;
                boolean win = isWin(board, x, y, type);
                board[y][x] = 0;
                if (win) {
                    return new Point(x, y, WIN_SCORE);
                }
            }
        }
        return null;
    }

    private static int evaluateBoard(int[][] board, int type) {
        int score = 0;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (board[y][x] != 0) {
                    continue;
                }
                score += evaluateMove(board, x, y, type) / 12;
            }
        }
        return score;
    }

    private static int evaluateMove(int[][] board, int x, int y, int type) {
        int score = 0;
        for (int[] direction : DIRECTIONS) {
            Line line = lineInfo(board, x, y, direction[0], direction[1], type);
            score += scoreLine(line.count, line.openEnds);
        }
        return score;
    }

    private static int scoreLine(int count, int openEnds) {
        if (count >= 5) {
            return WIN_SCORE;
        }
        if (count == 4 && openEnds == 2) {
            return 120_000;
        }
        if (count == 4 && openEnds == 1) {
            return 20_000;
        }
        if (count == 3 && openEnds == 2) {
            return 8_000;
        }
        if (count == 3 && openEnds == 1) {
            return 1_500;
        }
        if (count == 2 && openEnds == 2) {
            return 800;
        }
        if (count == 2 && openEnds == 1) {
            return 120;
        }
        return Math.max(1, count * 8 + openEnds * 6);
    }

    private static Line lineInfo(int[][] board, int x, int y, int dx, int dy, int type) {
        int count = 1;
        int openEnds = 0;
        int forward = countDirection(board, x, y, dx, dy, type);
        count += forward;
        int fx = x + dx * (forward + 1);
        int fy = y + dy * (forward + 1);
        if (inBounds(fx, fy) && board[fy][fx] == 0) {
            openEnds++;
        }
        int backward = countDirection(board, x, y, -dx, -dy, type);
        count += backward;
        int bx = x - dx * (backward + 1);
        int by = y - dy * (backward + 1);
        if (inBounds(bx, by) && board[by][bx] == 0) {
            openEnds++;
        }
        return new Line(count, openEnds);
    }

    private static int countDirection(int[][] board, int x, int y, int dx, int dy, int type) {
        int count = 0;
        int cx = x + dx;
        int cy = y + dy;
        while (inBounds(cx, cy) && board[cy][cx] == type) {
            count++;
            cx += dx;
            cy += dy;
        }
        return count;
    }

    private static boolean isWin(int[][] board, int x, int y, int type) {
        for (int[] direction : DIRECTIONS) {
            int total = 1
                    + countDirection(board, x, y, direction[0], direction[1], type)
                    + countDirection(board, x, y, -direction[0], -direction[1], type);
            if (total >= 5) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasNeighbor(int[][] board, int x, int y, int distance) {
        for (int dy = -distance; dy <= distance; dy++) {
            for (int dx = -distance; dx <= distance; dx++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                int nx = x + dx;
                int ny = y + dy;
                if (inBounds(nx, ny) && board[ny][nx] != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isEmptyBoard(int[][] board) {
        for (int[] row : board) {
            for (int cell : row) {
                if (cell != 0) {
                    return false;
                }
            }
        }
        return true;
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

    private static boolean inBounds(int x, int y) {
        return x >= 0 && x < SIZE && y >= 0 && y < SIZE;
    }

    private static int opponent(int type) {
        return type == 1 ? 2 : 1;
    }

    private static final class Point {
        private final int x;
        private final int y;
        private int score;

        private Point(int x, int y, int score) {
            this.x = x;
            this.y = y;
            this.score = score;
        }
    }

    private static final class Line {
        private final int count;
        private final int openEnds;

        private Line(int count, int openEnds) {
            this.count = count;
            this.openEnds = openEnds;
        }
    }

}
