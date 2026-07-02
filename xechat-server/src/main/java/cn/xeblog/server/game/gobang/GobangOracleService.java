package cn.xeblog.server.game.gobang;

import cn.xeblog.commons.entity.game.gobang.GobangOracleRequestDTO;
import cn.xeblog.commons.entity.game.gobang.GobangOracleResponseDTO;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private static final int WIN_SCORE = 10_000_000;
    private static final int FORCE_SCORE = 9_000_000;
    private static final int SEARCH_DEPTH = 5;
    private static final int MAX_CANDIDATES = 14;
    private static final int MAX_TACTICAL_CANDIDATES = 10;
    private static final int MAX_DEFENSES = 6;
    private static final int VCF_DEPTH = 9;
    private static final int VCT_DEPTH = 7;
    private static final long TIME_BUDGET_NANOS = 1_400_000_000L;
    private static final int RISK_HIGH = 1_200_000;
    private static final int RISK_MEDIUM = 220_000;
    private static final int RISK_LOW = 45_000;
    private static final Model[] MODELS = {
            new Model("LIANWU", WIN_SCORE, "11111"),
            new Model("HUOSI", 1_200_000, "011110"),
            new Model("CHONGSI", 180_000, "11110", "01111", "10111", "11011", "11101"),
            new Model("HUOSAN", 30_000, "001110", "011100", "010110", "011010", "0101110"),
            new Model("MIANSAN", 6_000, "001112", "010112", "011012", "211100", "211010", "210110"),
            new Model("HUOER", 900, "001100", "011000", "000110", "001010", "010100", "010010"),
            new Model("MIANER", 120, "011200", "001120", "002110", "021100", "110000", "000011", "000112", "211000"),
            new Model("HUOYI", 40, "010200", "002010", "020100", "001020", "201000", "000102", "000201"),
            new Model("MIANYI", 8, "001200", "002100", "000210", "000120", "210000", "000012")
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
            long deadline = System.nanoTime() + TIME_BUDGET_NANOS;

            Point directWin = findWinningPoint(board, type);
            if (directWin != null) {
                return success(response, directWin, type, WIN_SCORE, "服务端 AI 判断这一步可以直接连成五子，优先取胜。");
            }
            Point directBlock = findWinningPoint(board, opponent(type));
            if (directBlock != null) {
                return success(response, directBlock, type, WIN_SCORE - 1, "服务端 AI 判断这里是对手下一手成五点，先封住避免立刻输棋。");
            }

            Point ownVcf = findForcingMove(board, type, ThreatMode.VCF, VCF_DEPTH, deadline);
            if (ownVcf != null) {
                return success(response, ownVcf, type, ownVcf.score,
                        "服务端 AI 的 VCF 连续冲四求解器判断这一步可以进入强迫取胜序列。");
            }
            Point opponentVcf = findForcingMove(board, opponent(type), ThreatMode.VCF, VCF_DEPTH, deadline);
            if (opponentVcf != null) {
                return success(response, opponentVcf, type, opponentVcf.score,
                        "服务端 AI 的 VCF 连续冲四求解器判断对手这里有强迫取胜点，先占住关键点。");
            }
            Point ownVct = findForcingMove(board, type, ThreatMode.VCT, VCT_DEPTH, deadline);
            if (ownVct != null) {
                return success(response, ownVct, type, ownVct.score,
                        "服务端 AI 的 VCT 连续威胁求解器判断这一步可以形成持续强迫攻势。");
            }
            Point opponentVct = findForcingMove(board, opponent(type), ThreatMode.VCT, VCT_DEPTH, deadline);
            if (opponentVct != null) {
                return success(response, opponentVct, type, opponentVct.score,
                        "服务端 AI 的 VCT 连续威胁求解器判断对手这里会形成持续强迫攻势，先行压制。");
            }

            Point ownThreat = findBestThreatPoint(board, type, RISK_MEDIUM);
            Point opponentHighThreat = findBestThreatPoint(board, opponent(type), RISK_HIGH);
            if (ownThreat != null && (opponentHighThreat == null || ownThreat.score >= opponentHighThreat.score)) {
                return success(response, ownThreat, type, ownThreat.score,
                        "服务端 AI 判断这一步能形成强威胁，优先扩大胜势。");
            }
            if (opponentHighThreat != null) {
                return success(response, opponentHighThreat, type, opponentHighThreat.score,
                        "服务端 AI 判断对手下一手会形成强威胁，先占住关键点。");
            }
            Point opponentMediumThreat = findBestThreatPoint(board, opponent(type), RISK_MEDIUM);
            if (opponentMediumThreat != null && (ownThreat == null || opponentMediumThreat.score > ownThreat.score)) {
                return success(response, opponentMediumThreat, type, opponentMediumThreat.score,
                        "服务端 AI 判断对手这里威胁过强，先行压制。");
            }
            Point best = searchBestPoint(board, type, deadline);
            if (best == null) {
                return fail(response, "暂未找到可推荐的落点");
            }
            return success(response, best, type, best.score,
                    "服务端 AI 已按强化候选搜索、威胁画像和 Alpha-Beta 剪枝评估，认为这里的后续局面最好。");
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

    private static Point searchBestPoint(int[][] board, int type, long deadline) {
        List<Point> candidates = topCandidates(board, type, MAX_CANDIDATES);
        if (candidates.isEmpty()) {
            return isEmptyBoard(board) ? new Point(SIZE / 2, SIZE / 2, 0) : null;
        }
        Point best = null;
        int alpha = -Integer.MAX_VALUE;
        int beta = Integer.MAX_VALUE;
        Map<String, Integer> cache = new HashMap<>();
        for (Point point : candidates) {
            if (isTimeUp(deadline)) {
                break;
            }
            board[point.y][point.x] = type;
            int score = minimax(board, opponent(type), type, SEARCH_DEPTH - 1, alpha, beta, deadline, cache);
            board[point.y][point.x] = 0;
            point.score = score + point.score / 8;
            if (best == null || point.score > best.score) {
                best = point;
            }
            alpha = Math.max(alpha, point.score);
        }
        return best == null ? candidates.get(0) : best;
    }

    private static int minimax(int[][] board, int currentType, int rootType, int depth, int alpha, int beta,
                               long deadline, Map<String, Integer> cache) {
        Point rootWin = findWinningPoint(board, rootType);
        if (rootWin != null) {
            return WIN_SCORE + depth;
        }
        Point opponentWin = findWinningPoint(board, opponent(rootType));
        if (opponentWin != null) {
            return -WIN_SCORE - depth;
        }
        if (depth <= 0 || isFull(board) || isTimeUp(deadline)) {
            return evaluateBoard(board, rootType) - evaluateBoard(board, opponent(rootType));
        }

        String key = encodeBoard(board, currentType, rootType, depth);
        Integer cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        boolean maximizing = currentType == rootType;
        int best = maximizing ? -Integer.MAX_VALUE : Integer.MAX_VALUE;
        List<Point> candidates = topCandidates(board, currentType, depth >= 3 ? MAX_CANDIDATES : 9);
        if (candidates.isEmpty()) {
            return evaluateBoard(board, rootType) - evaluateBoard(board, opponent(rootType));
        }
        for (Point point : candidates) {
            board[point.y][point.x] = currentType;
            int score = minimax(board, opponent(currentType), rootType, depth - 1, alpha, beta, deadline, cache);
            board[point.y][point.x] = 0;
            if (maximizing) {
                best = Math.max(best, score);
                alpha = Math.max(alpha, best);
            } else {
                best = Math.min(best, score);
                beta = Math.min(beta, best);
            }
            if (beta <= alpha || isTimeUp(deadline)) {
                break;
            }
        }
        cache.put(key, best);
        return best;
    }

    private static List<Point> topCandidates(int[][] board, int type, int limit) {
        List<Point> points = new ArrayList<>();
        int stones = countStones(board);
        if (stones == 0) {
            points.add(new Point(SIZE / 2, SIZE / 2, WIN_SCORE / 10));
            return points;
        }
        int opponent = opponent(type);
        int neighborDistance = stones < 6 ? 3 : 2;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (board[y][x] != 0 || !hasNeighbor(board, x, y, neighborDistance)) {
                    continue;
                }
                ThreatProfile attack = analyzeMove(board, x, y, type);
                ThreatProfile defense = analyzeMove(board, x, y, opponent);
                int centerScore = Math.max(0, 14 - Math.abs(x - SIZE / 2) - Math.abs(y - SIZE / 2))
                        * (stones < 10 ? 60 : 10);
                int neighborScore = countNeighbors(board, x, y, 1) * 220 + countNeighbors(board, x, y, 2) * 45;
                int riskPenalty = leavesOpponentImmediateWin(board, x, y, type) && attack.score < WIN_SCORE
                        ? WIN_SCORE
                        : 0;
                int score = attack.score + defense.score * 6 / 5 + centerScore + neighborScore - riskPenalty;
                points.add(new Point(x, y, score));
            }
        }
        points.sort(Comparator.comparingInt((Point point) -> point.score).reversed());
        if (points.size() > limit) {
            return new ArrayList<>(points.subList(0, limit));
        }
        return points;
    }

    private static Point findForcingMove(int[][] board, int type, ThreatMode mode, int maxPly, long deadline) {
        List<Point> candidates = forcingCandidates(board, type, mode, MAX_TACTICAL_CANDIDATES);
        for (Point point : candidates) {
            if (isTimeUp(deadline)) {
                return null;
            }
            ThreatProfile profile = analyzeMove(board, point.x, point.y, type);
            boolean vctEntry = mode == ThreatMode.VCT
                    && profile.liveThree > 1
                    && !leavesOpponentImmediateWin(board, point.x, point.y, type);
            board[point.y][point.x] = type;
            boolean force = vctEntry
                    || isWin(board, point.x, point.y, type)
                    || isForcedWinAfterAttack(board, type, mode, maxPly - 1, deadline);
            board[point.y][point.x] = 0;
            if (force) {
                point.score += FORCE_SCORE;
                return point;
            }
        }
        return null;
    }

    private static boolean isForcedWinAfterAttack(int[][] board, int attacker, ThreatMode mode, int remainingPly,
                                                  long deadline) {
        if (isTimeUp(deadline)) {
            return false;
        }
        int defender = opponent(attacker);
        if (!findWinningPoints(board, defender).isEmpty()) {
            return false;
        }

        List<Point> winningPoints = findWinningPoints(board, attacker);
        if (winningPoints.size() >= 2) {
            return true;
        }
        if (remainingPly <= 0) {
            return false;
        }

        List<Point> defenses;
        if (winningPoints.size() == 1) {
            defenses = winningPoints;
        } else if (mode == ThreatMode.VCT) {
            defenses = threatDefensePoints(board, attacker);
        } else {
            return false;
        }
        if (defenses.isEmpty() || defenses.size() > MAX_DEFENSES) {
            return false;
        }

        for (Point defense : defenses) {
            if (board[defense.y][defense.x] != 0) {
                continue;
            }
            board[defense.y][defense.x] = defender;
            boolean defenderWin = isWin(board, defense.x, defense.y, defender);
            Point next = defenderWin ? null : findForcingMove(board, attacker, mode, remainingPly - 1, deadline);
            board[defense.y][defense.x] = 0;
            if (next == null) {
                return false;
            }
        }
        return true;
    }

    private static List<Point> forcingCandidates(int[][] board, int type, ThreatMode mode, int limit) {
        List<Point> points = new ArrayList<>();
        if (isEmptyBoard(board)) {
            points.add(new Point(SIZE / 2, SIZE / 2, FORCE_SCORE));
            return points;
        }
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (board[y][x] != 0 || !hasNeighbor(board, x, y, 2)) {
                    continue;
                }
                ThreatProfile profile = analyzeMove(board, x, y, type);
                if (profile.isForcingCandidate(mode)) {
                    points.add(new Point(x, y, profile.score));
                }
            }
        }
        points.sort(Comparator.comparingInt((Point point) -> point.score).reversed());
        if (points.size() > limit) {
            return new ArrayList<>(points.subList(0, limit));
        }
        return points;
    }

    private static List<Point> threatDefensePoints(int[][] board, int attacker) {
        List<Point> points = new ArrayList<>();
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (board[y][x] != 0 || !hasNeighbor(board, x, y, 2)) {
                    continue;
                }
                ThreatProfile profile = analyzeMove(board, x, y, attacker);
                if (profile.score >= RISK_LOW && (profile.liveThree > 0 || profile.rushFour > 0 || profile.liveFour > 0)) {
                    points.add(new Point(x, y, profile.score));
                }
            }
        }
        points.sort(Comparator.comparingInt((Point point) -> point.score).reversed());
        if (points.size() > MAX_DEFENSES + 1) {
            return new ArrayList<>(points.subList(0, MAX_DEFENSES + 1));
        }
        return points;
    }

    private static Point findWinningPoint(int[][] board, int type) {
        List<Point> points = findWinningPoints(board, type);
        return points.isEmpty() ? null : points.get(0);
    }

    private static List<Point> findWinningPoints(int[][] board, int type) {
        List<Point> points = new ArrayList<>();
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (board[y][x] != 0) {
                    continue;
                }
                board[y][x] = type;
                boolean win = isWin(board, x, y, type);
                board[y][x] = 0;
                if (win) {
                    points.add(new Point(x, y, WIN_SCORE));
                }
            }
        }
        points.sort(Comparator.comparingInt((Point point) -> evaluateMove(board, point.x, point.y, type)).reversed());
        return points;
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
                if (board[y][x] != 0 || !hasNeighbor(board, x, y, 2)) {
                    continue;
                }
                score += evaluateMove(board, x, y, type) / 10;
            }
        }
        return score;
    }

    private static int evaluateMove(int[][] board, int x, int y, int type) {
        return analyzeMove(board, x, y, type).score;
    }

    private static ThreatProfile analyzeMove(int[][] board, int x, int y, int type) {
        ThreatProfile profile = new ThreatProfile();
        if (!inBounds(x, y) || board[y][x] != 0) {
            return profile;
        }
        board[y][x] = type;
        if (isWin(board, x, y, type)) {
            board[y][x] = 0;
            profile.score = WIN_SCORE;
            profile.five = 1;
            return profile;
        }
        for (int[] direction : DIRECTIONS) {
            String situation = getSituation(board, x, y, direction[0], direction[1], type);
            Model model = getModel(situation);
            if (model == null) {
                continue;
            }
            profile.score += model.score;
            profile.add(model.id);
        }
        board[y][x] = 0;
        profile.applyComboBonus();
        return profile;
    }

    private static Model getModel(String situation) {
        for (Model model : MODELS) {
            if (model.matches(situation)) {
                return model;
            }
        }
        return null;
    }

    private static String getSituation(int[][] board, int x, int y, int dx, int dy, int type) {
        StringBuilder builder = new StringBuilder(9);
        for (int offset = -4; offset <= 4; offset++) {
            int cx = x + dx * offset;
            int cy = y + dy * offset;
            if (!inBounds(cx, cy)) {
                builder.append('2');
                continue;
            }
            int cell = offset == 0 ? type : board[cy][cx];
            builder.append(cell == 0 ? '0' : cell == type ? '1' : '2');
        }
        return builder.toString();
    }

    private static boolean leavesOpponentImmediateWin(int[][] board, int x, int y, int type) {
        board[y][x] = type;
        boolean danger = !findWinningPoints(board, opponent(type)).isEmpty();
        board[y][x] = 0;
        return danger;
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

    private static int countNeighbors(int[][] board, int x, int y, int distance) {
        int count = 0;
        for (int dy = -distance; dy <= distance; dy++) {
            for (int dx = -distance; dx <= distance; dx++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                int nx = x + dx;
                int ny = y + dy;
                if (inBounds(nx, ny) && board[ny][nx] != 0) {
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean isEmptyBoard(int[][] board) {
        return countStones(board) == 0;
    }

    private static int countStones(int[][] board) {
        int count = 0;
        for (int[] row : board) {
            for (int cell : row) {
                if (cell != 0) {
                    count++;
                }
            }
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

    private static boolean inBounds(int x, int y) {
        return x >= 0 && x < SIZE && y >= 0 && y < SIZE;
    }

    private static int opponent(int type) {
        return type == 1 ? 2 : 1;
    }

    private static boolean isTimeUp(long deadline) {
        return System.nanoTime() >= deadline;
    }

    private static String encodeBoard(int[][] board, int currentType, int rootType, int depth) {
        StringBuilder builder = new StringBuilder(SIZE * SIZE + 8);
        builder.append(currentType).append(rootType).append(depth).append(':');
        for (int[] row : board) {
            for (int cell : row) {
                builder.append(cell);
            }
        }
        return builder.toString();
    }

    private enum ThreatMode {
        VCF,
        VCT
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

    private static final class ThreatProfile {
        private int score;
        private int five;
        private int liveFour;
        private int rushFour;
        private int liveThree;
        private int sleepThree;
        private int liveTwo;

        private void add(String id) {
            if ("LIANWU".equals(id)) {
                five++;
            } else if ("HUOSI".equals(id)) {
                liveFour++;
            } else if ("CHONGSI".equals(id)) {
                rushFour++;
            } else if ("HUOSAN".equals(id)) {
                liveThree++;
            } else if ("MIANSAN".equals(id)) {
                sleepThree++;
            } else if ("HUOER".equals(id)) {
                liveTwo++;
            }
        }

        private void applyComboBonus() {
            if (five > 0) {
                score += WIN_SCORE;
            }
            if (liveFour > 0 || rushFour > 1) {
                score += RISK_HIGH;
            } else if (rushFour > 0 && liveThree > 0) {
                score += RISK_HIGH;
            } else if (liveThree > 1) {
                score += RISK_MEDIUM;
            } else if (rushFour > 0) {
                score += RISK_MEDIUM / 2;
            } else if (liveThree > 0 && sleepThree > 0) {
                score += RISK_LOW;
            } else if (liveThree > 0 && liveTwo > 0) {
                score += RISK_LOW / 2;
            }
        }

        private boolean isForcingCandidate(ThreatMode mode) {
            if (score >= WIN_SCORE || liveFour > 0 || rushFour > 0) {
                return true;
            }
            return mode == ThreatMode.VCT && (liveThree > 0 || score >= RISK_LOW);
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
