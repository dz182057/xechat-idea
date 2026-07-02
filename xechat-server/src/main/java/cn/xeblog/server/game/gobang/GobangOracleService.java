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
    private static final int SEARCH_DEPTH = 5;
    private static final int MAX_CANDIDATES = 12;
    private static final int RISK_HIGH = 800_000;
    private static final int RISK_MEDIUM = 500_000;
    private static final int RISK_LOW = 100_000;
    private static final Model[] MODELS = {
            new Model("LIANWU", 10_000_000, "11111"),
            new Model("HUOSI", 1_000_000, "011110"),
            new Model("HUOSAN", 10_000, "001110", "011100", "010110", "011010"),
            new Model("CHONGSI", 9_000, "11110", "01111", "10111", "11011", "11101"),
            new Model("HUOER", 100, "001100", "011000", "000110", "001010", "010100"),
            new Model("HUOYI", 80, "010200", "002010", "020100", "001020", "201000", "000102", "000201"),
            new Model("MIANSAN", 30, "001112", "010112", "011012", "211100", "211010"),
            new Model("MIANER", 10, "011200", "001120", "002110", "021100", "110000", "000011", "000112", "211000"),
            new Model("MIANYI", 1, "001200", "002100", "000210", "000120", "210000", "000012")
    };

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
            Point ownThreat = findBestThreatPoint(board, type, RISK_MEDIUM);
            Point opponentHighThreat = findBestThreatPoint(board, opponent(type), RISK_HIGH);
            if (ownThreat != null && (opponentHighThreat == null || ownThreat.score >= opponentHighThreat.score)) {
                return success(response, ownThreat, type, ownThreat.score,
                        "服务端 AI 判断这一步能形成强迫杀，优先扩大胜势。");
            }
            if (opponentHighThreat != null) {
                return success(response, opponentHighThreat, type, opponentHighThreat.score,
                        "服务端 AI 判断对手下一手会形成强迫杀，先占住关键点。");
            }
            Point opponentMediumThreat = findBestThreatPoint(board, opponent(type), RISK_MEDIUM);
            if (opponentMediumThreat != null && (ownThreat == null || opponentMediumThreat.score > ownThreat.score)) {
                return success(response, opponentMediumThreat, type, opponentMediumThreat.score,
                        "服务端 AI 判断对手这里威胁过强，先行压制。");
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
        List<Point> candidates = topCandidates(board, currentType, depth >= 3 ? MAX_CANDIDATES : 8);
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
                int riskBonus = 0;
                if (attackScore >= RISK_HIGH || defenseScore >= RISK_HIGH) {
                    riskBonus += RISK_HIGH;
                } else if (attackScore >= RISK_MEDIUM || defenseScore >= RISK_MEDIUM) {
                    riskBonus += RISK_MEDIUM / 2;
                }
                points.add(new Point(x, y, attackScore + defenseScore * 11 / 10 + centerScore + riskBonus));
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

    private static Point findBestThreatPoint(int[][] board, int type, int threshold) {
        Point best = null;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (board[y][x] != 0 || !hasNeighbor(board, x, y, 2)) {
                    continue;
                }
                int score = evaluateMove(board, x, y, type);
                if (score < threshold) {
                    continue;
                }
                if (best == null || score > best.score) {
                    best = new Point(x, y, score);
                }
            }
        }
        return best;
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
        int liveThree = 0;
        int rushFour = 0;
        int liveFour = 0;
        int threeFourCombo = 0;
        for (int[] direction : DIRECTIONS) {
            String situation = getSituation(board, x, y, direction[0], direction[1], type);
            Model model = getModel(situation);
            if (model == null) {
                continue;
            }
            if ("HUOSI".equals(model.id)) {
                liveFour++;
            } else if ("HUOSAN".equals(model.id)) {
                liveThree++;
                if (containsModel(situation, "CHONGSI")) {
                    threeFourCombo++;
                }
            } else if ("CHONGSI".equals(model.id)) {
                rushFour++;
            }
            score += model.score;
        }
        if (liveFour > 0 || rushFour > 1 || threeFourCombo > 1) {
            score += RISK_HIGH;
        } else if ((rushFour > 0 && liveThree > 0) || (threeFourCombo > 0 && liveThree > 1)) {
            score += RISK_MEDIUM;
        } else if (liveThree > 1) {
            score += RISK_LOW;
        }
        return score;
    }

    private static Model getModel(String situation) {
        for (Model model : MODELS) {
            if (model.matches(situation)) {
                return model;
            }
        }
        return null;
    }

    private static boolean containsModel(String situation, String id) {
        for (Model model : MODELS) {
            if (model.id.equals(id)) {
                return model.matches(situation);
            }
        }
        return false;
    }

    private static String getSituation(int[][] board, int x, int y, int dx, int dy, int type) {
        StringBuilder builder = new StringBuilder(9);
        for (int offset = -4; offset <= 4; offset++) {
            if (offset == 0) {
                builder.append('1');
                continue;
            }
            int cx = x + dx * offset;
            int cy = y + dy * offset;
            if (!inBounds(cx, cy)) {
                continue;
            }
            int cell = board[cy][cx];
            builder.append(cell == 0 ? '0' : cell == type ? '1' : '2');
        }
        return builder.toString();
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

    private static final class Model {
        private final String id;
        private final int score;
        private final String[] values;

        private Model(String id, int score, String... values) {
            this.id = id;
            this.score = score;
            this.values = values;
        }

        private boolean matches(String situation) {
            for (String value : values) {
                if (situation.contains(value)) {
                    return true;
                }
            }
            return false;
        }
    }

}
