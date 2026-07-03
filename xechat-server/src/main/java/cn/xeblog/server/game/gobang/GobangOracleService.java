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
    private static final int MAX_CANDIDATES = 12;
    private static final int MAX_TACTICAL_CANDIDATES = 8;
    private static final int PLUGIN_MAX_NODES = 16;
    private static final int MAX_DEFENSES = 6;
    private static final int VCF_DEPTH = 7;
    private static final int VCT_DEPTH = 5;
    private static final long TIME_BUDGET_NANOS = 2_300_000_000L;
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
            Map<String, Boolean> forceCache = new HashMap<>();

            Point directWin = findWinningPoint(board, type);
            if (directWin != null) {
                return successWithHumanReason(response, board, directWin, type, WIN_SCORE);
            }
            Point directBlock = findWinningPoint(board, opponent(type));
            if (directBlock != null) {
                return successWithHumanReason(response, board, directBlock, type, WIN_SCORE - 1);
            }

            Point ownVcf = findForcingMove(board, type, ThreatMode.VCF, VCF_DEPTH, deadline, forceCache);
            if (ownVcf != null) {
                return successWithHumanReason(response, board, ownVcf, type, ownVcf.score);
            }

            Point linbicheng = pluginLinbichengPoint(board, type, deadline);
            if (linbicheng != null && !leavesOpponentImmediateWin(board, linbicheng.x, linbicheng.y, type)) {
                return successWithHumanReason(response, board, linbicheng, type, linbicheng.score);
            }

            Point opponentVcf = findForcingMove(board, opponent(type), ThreatMode.VCF, VCF_DEPTH, deadline, forceCache);
            if (opponentVcf != null) {
                return successWithHumanReason(response, board, opponentVcf, type, opponentVcf.score);
            }
            Point ownVct = findForcingMove(board, type, ThreatMode.VCT, VCT_DEPTH, deadline, forceCache);
            if (ownVct != null) {
                return successWithHumanReason(response, board, ownVct, type, ownVct.score);
            }
            Point opponentVct = findForcingMove(board, opponent(type), ThreatMode.VCT, VCT_DEPTH, deadline, forceCache);
            if (opponentVct != null) {
                return successWithHumanReason(response, board, opponentVct, type, opponentVct.score);
            }

            Point ownThreat = findBestThreatPoint(board, type, RISK_MEDIUM);
            Point opponentHighThreat = findBestThreatPoint(board, opponent(type), RISK_HIGH);
            if (ownThreat != null && (opponentHighThreat == null || ownThreat.score >= opponentHighThreat.score)) {
                return successWithHumanReason(response, board, ownThreat, type, ownThreat.score);
            }
            if (opponentHighThreat != null) {
                return successWithHumanReason(response, board, opponentHighThreat, type, opponentHighThreat.score);
            }
            Point opponentMediumThreat = findBestThreatPoint(board, opponent(type), RISK_MEDIUM);
            if (opponentMediumThreat != null && (ownThreat == null || opponentMediumThreat.score > ownThreat.score)) {
                return successWithHumanReason(response, board, opponentMediumThreat, type, opponentMediumThreat.score);
            }
            Point best = searchBestPoint(board, type, deadline);
            if (best == null) {
                return fail(response, "暂未找到可推荐的落点");
            }
            return successWithHumanReason(response, board, best, type, best.score);
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

    private static GobangOracleResponseDTO successWithHumanReason(GobangOracleResponseDTO response, int[][] board,
                                                                  Point point, int type, int score) {
        return success(response, point, type, score, buildHumanReason(board, point, type));
    }

    private static String buildHumanReason(int[][] board, Point point, int type) {
        if (isEmptyBoard(board)) {
            return "下在这里可以先占住天元，四个方向都保留较好的连接空间。";
        }

        ThreatProfile own = analyzeMove(board, point.x, point.y, type);
        ThreatProfile defense = analyzeMove(board, point.x, point.y, opponent(type));
        List<String> parts = new ArrayList<>();
        List<String> defenseLabels = threatLabels(defense, true);
        List<String> ownLabels = threatLabels(own, false);
        if (!defenseLabels.isEmpty()) {
            parts.add("可以堵住对手的" + joinLabels(defenseLabels));
        }
        if (!ownLabels.isEmpty()) {
            parts.add("自己能形成" + joinLabels(ownLabels));
        }
        if (parts.isEmpty()) {
            return "下在这里可以靠近已有棋子，增加后续连线和进攻空间。";
        }
        return "下在这里" + String.join("，同时", parts) + "。";
    }

    private static List<String> threatLabels(ThreatProfile profile, boolean defense) {
        List<String> labels = new ArrayList<>();
        addThreatLabel(labels, profile.five, defense ? "成五点" : "连五取胜");
        addThreatLabel(labels, profile.liveFour, "活四");
        addThreatLabel(labels, profile.rushFour, "冲四");
        addThreatLabel(labels, profile.liveThree, "活三");
        if (!defense || labels.isEmpty()) {
            addThreatLabel(labels, profile.sleepThree, "眠三");
            addThreatLabel(labels, profile.liveTwo, "活二");
        }
        return labels;
    }

    private static void addThreatLabel(List<String> labels, int count, String label) {
        if (count <= 0) {
            return;
        }
        labels.add(count > 1 ? count + "个" + label : label);
    }

    private static String joinLabels(List<String> labels) {
        if (labels.size() <= 1) {
            return labels.isEmpty() ? "" : labels.get(0);
        }
        return String.join("、", labels);
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
        List<Point> candidates = topCandidates(board, type, MAX_CANDIDATES, true);
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
        List<Point> candidates = topCandidates(board, currentType, depth >= 3 ? MAX_CANDIDATES : 8, false);
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

    private static List<Point> topCandidates(int[][] board, int type, int limit, boolean strictRisk) {
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
                int strategicScore = strategicMoveScore(board, x, y, type)
                        + strategicMoveScore(board, x, y, opponent) * 4 / 5;
                int riskPenalty = strictRisk && leavesOpponentImmediateWin(board, x, y, type) && attack.score < WIN_SCORE
                        ? WIN_SCORE
                        : 0;
                int score = attack.score + defense.score * 6 / 5 + strategicScore
                        + centerScore + neighborScore - riskPenalty;
                points.add(new Point(x, y, score));
            }
        }
        points.sort(Comparator.comparingInt((Point point) -> point.score).reversed());
        if (points.size() > limit) {
            return new ArrayList<>(points.subList(0, limit));
        }
        return points;
    }

    private static Point pluginLinbichengPoint(int[][] board, int type, long deadline) {
        PluginHardAiEngine engine = new PluginHardAiEngine(board, type, deadline, 8, PLUGIN_MAX_NODES, 1, 10);
        return engine.getPoint();
    }

    private static Point findForcingMove(int[][] board, int type, ThreatMode mode, int maxPly, long deadline,
                                         Map<String, Boolean> forceCache) {
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
                    || isForcedWinAfterAttack(board, type, mode, maxPly - 1, deadline, forceCache);
            board[point.y][point.x] = 0;
            if (force) {
                point.score += FORCE_SCORE;
                return point;
            }
        }
        return null;
    }

    private static boolean isForcedWinAfterAttack(int[][] board, int attacker, ThreatMode mode, int remainingPly,
                                                  long deadline, Map<String, Boolean> forceCache) {
        if (isTimeUp(deadline)) {
            return false;
        }
        String key = "force:" + attacker + ':' + mode + ':' + remainingPly + ':'
                + encodeBoard(board, attacker, attacker, remainingPly);
        Boolean cached = forceCache.get(key);
        if (cached != null) {
            return cached;
        }
        boolean forced = computeForcedWinAfterAttack(board, attacker, mode, remainingPly, deadline, forceCache);
        forceCache.put(key, forced);
        return forced;
    }

    private static boolean computeForcedWinAfterAttack(int[][] board, int attacker, ThreatMode mode, int remainingPly,
                                                       long deadline, Map<String, Boolean> forceCache) {
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
            Point next = defenderWin ? null
                    : findForcingMove(board, attacker, mode, remainingPly - 1, deadline, forceCache);
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
        int score = evaluateLinePotential(board, type);
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

    private static int evaluateLinePotential(int[][] board, int type) {
        int score = 0;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                for (int[] direction : DIRECTIONS) {
                    score += segmentPotentialScore(board, x, y, direction[0], direction[1], type);
                }
            }
        }
        return score;
    }

    private static int segmentPotentialScore(int[][] board, int x, int y, int dx, int dy, int type) {
        int endX = x + dx * 4;
        int endY = y + dy * 4;
        if (!inBounds(x, y) || !inBounds(endX, endY)) {
            return 0;
        }
        int own = 0;
        int empty = 0;
        int opponent = opponent(type);
        for (int i = 0; i < 5; i++) {
            int cell = board[y + dy * i][x + dx * i];
            if (cell == opponent) {
                return 0;
            }
            if (cell == type) {
                own++;
            } else {
                empty++;
            }
        }
        if (own == 0 || empty == 0) {
            return 0;
        }
        int openEnds = 0;
        if (isOpen(board, x - dx, y - dy)) {
            openEnds++;
        }
        if (isOpen(board, endX + dx, endY + dy)) {
            openEnds++;
        }
        if (own >= 4) {
            return 180_000 + openEnds * 90_000;
        }
        if (own == 3) {
            return (openEnds == 2 ? 42_000 : 12_000) + empty * 500;
        }
        if (own == 2) {
            return (openEnds == 2 ? 1_800 : 700) + empty * 90;
        }
        return openEnds * 60 + empty * 12;
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

    private static int strategicMoveScore(int[][] board, int x, int y, int type) {
        int score = 0;
        for (int[] direction : DIRECTIONS) {
            int left = countDirection(board, x, y, -direction[0], -direction[1], type);
            int right = countDirection(board, x, y, direction[0], direction[1], type);
            int connected = left + right;
            int leftSpace = countLineSpace(board, x, y, -direction[0], -direction[1], type);
            int rightSpace = countLineSpace(board, x, y, direction[0], direction[1], type);
            int totalSpace = leftSpace + rightSpace + 1;
            if (totalSpace < 5) {
                continue;
            }
            int openEnds = 0;
            if (leftSpace > left) {
                openEnds++;
            }
            if (rightSpace > right) {
                openEnds++;
            }
            score += connected * connected * 520;
            score += totalSpace * 55;
            if (left > 0 && right > 0) {
                score += 850;
            }
            if (openEnds == 2) {
                score += 650 + connected * 220;
            } else if (openEnds == 1) {
                score += 160 + connected * 80;
            }
        }
        return score;
    }

    private static int countLineSpace(int[][] board, int x, int y, int dx, int dy, int type) {
        int space = 0;
        int blockedBy = opponent(type);
        int cx = x + dx;
        int cy = y + dy;
        while (space < 4 && inBounds(cx, cy) && board[cy][cx] != blockedBy) {
            space++;
            cx += dx;
            cy += dy;
        }
        return space;
    }

    private static boolean isOpen(int[][] board, int x, int y) {
        return inBounds(x, y) && board[y][x] == 0;
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

    private static final class PluginHardAiEngine {
        private static final int INFINITY = 999_999_999;
        private static final int HIGH_RISK = 800_000;
        private static final int MEDIUM_RISK = 500_000;
        private static final int LOW_RISK = 100_000;
        private static final long[][] BLACK_ZOBRIST = createZobrist(0x4d595df4d0f33173L);
        private static final long[][] WHITE_ZOBRIST = createZobrist(0x785f3ec32d456c9bL);
        private static final PluginModel[] PLUGIN_MODELS = {
                new PluginModel("LIANWU", 10_000_000, "11111"),
                new PluginModel("HUOSI", 1_000_000, "011110"),
                new PluginModel("HUOSAN", 10_000, "001110", "011100", "010110", "011010"),
                new PluginModel("CHONGSI", 9_000, "11110", "01111", "10111", "11011", "11101"),
                new PluginModel("HUOER", 100, "001100", "011000", "000110", "001010", "010100"),
                new PluginModel("HUOYI", 80, "010200", "002010", "020100", "001020", "201000", "000102", "000201"),
                new PluginModel("MIANSAN", 30, "001112", "010112", "011012", "211100", "211010"),
                new PluginModel("MIANER", 10, "011200", "001120", "002110", "021100", "110000", "000011", "000112", "211000"),
                new PluginModel("MIANYI", 1, "001200", "002100", "000210", "000120", "210000", "000012")
        };

        private final int[][] chessData = new int[SIZE][SIZE];
        private final int ai;
        private final long deadline;
        private final int depth;
        private final int maxNodes;
        private final int vcx;
        private final int vcxDepth;
        private final float attack;
        private final int rounds;
        private long hashcode;
        private Point bestPoint;
        private Map<Long, PluginCache> situationCacheMap = new HashMap<>();

        private PluginHardAiEngine(int[][] board, int type, long deadline,
                                   int depth, int maxNodes, int vcx, int vcxDepth) {
            this.ai = type;
            this.deadline = deadline;
            this.depth = depth;
            this.maxNodes = maxNodes;
            this.vcx = vcx;
            this.vcxDepth = vcxDepth;
            this.attack = type == 1 ? 1.8f : 0.5f;
            int total = 0;
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    int cell = board[y][x];
                    if (cell == 0) {
                        continue;
                    }
                    putChess(new PluginPoint(x, y, cell));
                    total++;
                }
            }
            this.rounds = total / 2 + 1;
        }

        private Point getPoint() {
            if (rounds == 1 && ai == 1) {
                return new Point(SIZE / 2, SIZE / 2, 0);
            }

            if (vcx > 0) {
                PluginPoint vcxPoint = deepeningVcx(true, vcxDepth, vcx == 2);
                if (vcxPoint != null && chessData[vcxPoint.x][vcxPoint.y] == 0) {
                    return toPoint(vcxPoint);
                }
            }

            situationCacheMap = new HashMap<>();
            PluginPoint minimaxPoint = deepeningMinimax(2, rounds < 4 ? 4 : depth);
            if (minimaxPoint != null && chessData[minimaxPoint.x][minimaxPoint.y] == 0) {
                return toPoint(minimaxPoint);
            }
            PluginPoint best = getBestPoint(null);
            return best == null ? null : toPoint(best);
        }

        private PluginPoint deepeningVcx(boolean isAi, int maxDepth, boolean isVcf) {
            int originalAi = ai;
            PluginPoint point = null;
            situationCacheMap = new HashMap<>();
            for (int depth = 1; depth <= maxDepth && !isTimeUp(deadline); depth += 2) {
                point = vcx(0, depth, isVcf, originalAi);
                if (point != null) {
                    break;
                }
            }
            if (!isAi && point != null) {
                point.type = originalAi;
            }
            return point;
        }

        private PluginPoint vcx(int type, int depth, boolean isVcf, int rootAi) {
            PluginCache cache = situationCacheMap.get(hashcode);
            if (cache != null && cache.depth >= depth) {
                return cache.point;
            }
            if (depth == 0 || isTimeUp(deadline)) {
                return null;
            }

            int nextType = type == 0 ? rootAi : type;
            boolean isAI = nextType == rootAi;
            PluginPoint best = null;

            for (PluginPoint point : getVcxPoints(nextType, isVcf, rootAi)) {
                if (isTimeUp(deadline)) {
                    break;
                }
                if (point.score >= HIGH_RISK) {
                    return isAI ? point : null;
                }

                putChess(point);
                best = vcx(opponent(nextType), depth - 1, isVcf, rootAi);
                revokeChess(point);

                if (best == null) {
                    if (isAI) {
                        continue;
                    }
                    return null;
                }

                best = point;
                if (isAI) {
                    break;
                }
            }

            situationCacheMap.put(hashcode, new PluginCache(best, depth));
            return best;
        }

        private List<PluginPoint> getVcxPoints(int type, boolean isVcf, int rootAi) {
            boolean isAI = type == rootAi;
            List<PluginPoint> attackPointList = new ArrayList<>();
            List<PluginPoint> defensePointList = new ArrayList<>();
            List<PluginPoint> vcxPointList = new ArrayList<>();
            boolean isDanger = false;

            for (int x = 0; x < SIZE; x++) {
                for (int y = 0; y < SIZE; y++) {
                    if (chessData[x][y] != 0) {
                        continue;
                    }
                    PluginPoint point = new PluginPoint(x, y, type);
                    int score = evaluate(point);
                    if (score >= PluginModel.LIANWU_SCORE) {
                        return singleton(point);
                    }
                    if (isDanger) {
                        continue;
                    }

                    PluginPoint foePoint = new PluginPoint(x, y, opponent(type));
                    int foeScore = evaluate(foePoint);
                    if (foeScore >= PluginModel.LIANWU_SCORE) {
                        isDanger = true;
                        defensePointList.clear();
                        defensePointList.add(point);
                        continue;
                    }

                    if (score >= MEDIUM_RISK) {
                        attackPointList.add(point);
                        continue;
                    }

                    if (isAI) {
                        if (checkSituation(point, "CHONGSI")) {
                            vcxPointList.add(point);
                        } else if (!isVcf && checkSituation(point, "HUOSAN")) {
                            vcxPointList.add(point);
                        }
                    } else if (!isVcf && (checkSituation(point, "CHONGSI") || foeScore >= PluginModel.HUOSI_SCORE)) {
                        defensePointList.add(point);
                    }
                }
            }

            List<PluginPoint> pointList = new ArrayList<>();
            if (!isDanger) {
                if (!attackPointList.isEmpty()) {
                    sortPluginPoints(attackPointList);
                    if (isAI) {
                        return attackPointList;
                    }
                    pointList.addAll(attackPointList);
                }
                pointList.addAll(vcxPointList);
            }

            if (!defensePointList.isEmpty()) {
                if (isAI) {
                    pointList.addAll(defensePointList);
                } else {
                    pointList.addAll(0, defensePointList);
                }
            }
            return pointList;
        }

        private PluginPoint deepeningMinimax(int depth, int maxDepth) {
            PluginPoint best = null;
            for (int nextDepth = depth; nextDepth <= maxDepth && !isTimeUp(deadline); nextDepth += 2) {
                int score = minimax(0, nextDepth, -INFINITY, INFINITY);
                if (bestPoint != null) {
                    best = new PluginPoint(bestPoint.x, bestPoint.y, ai, bestPoint.score);
                }
                if (Math.abs(score) >= INFINITY - 1) {
                    break;
                }
            }
            return best;
        }

        private int minimax(int type, int depth, int alpha, int beta) {
            int nextType = type == 0 ? ai : type;
            boolean isRoot = type == 0;
            boolean isAI = nextType == ai;
            PluginCache cache = situationCacheMap.get(hashcode);
            if (cache != null && cache.depth >= depth && cache.hasScore) {
                return cache.score;
            }
            if (depth == 0 || isTimeUp(deadline)) {
                return evaluateAll();
            }

            List<PluginPoint> pointList = getHeuristicPoints(nextType);
            if (pointList.isEmpty()) {
                return evaluateAll();
            }
            if (isRoot && pointList.size() == 1) {
                bestPoint = toPoint(pointList.get(0));
                return bestPoint.score;
            }

            List<PluginPoint> bestPointList = new ArrayList<>();
            for (PluginPoint point : pointList) {
                if (isTimeUp(deadline)) {
                    break;
                }
                if (point.score >= PluginModel.LIANWU_SCORE) {
                    point.score = isAI ? INFINITY - 1 : -INFINITY + 1;
                } else {
                    putChess(point);
                    point.score = minimax(opponent(nextType), depth - 1, alpha, beta);
                    revokeChess(point);
                }

                if (isAI) {
                    if (point.score >= alpha) {
                        if (isRoot) {
                            if (point.score > alpha) {
                                bestPointList.clear();
                            }
                            bestPointList.add(point);
                        }
                        alpha = point.score;
                    }
                } else if (point.score < beta) {
                    beta = point.score;
                }

                if (alpha >= beta) {
                    break;
                }
            }

            if (isRoot && !bestPointList.isEmpty()) {
                PluginPoint best = bestPointList.size() > 1 ? getBestPoint(bestPointList) : bestPointList.get(0);
                bestPoint = toPoint(best);
            }

            int score = isAI ? alpha : beta;
            situationCacheMap.put(hashcode, new PluginCache(score, depth));
            return score;
        }

        private List<PluginPoint> getHeuristicPoints(int type) {
            List<PluginPoint> highPriorityPointList = new ArrayList<>();
            List<PluginPoint> lowPriorityPointList = new ArrayList<>();
            List<PluginPoint> alternatePointList = new ArrayList<>();
            List<PluginPoint> killPointList = new ArrayList<>();
            int dangerLevel = 0;

            for (int x = 0; x < SIZE; x++) {
                for (int y = 0; y < SIZE; y++) {
                    if (chessData[x][y] != 0) {
                        continue;
                    }

                    PluginPoint point = new PluginPoint(x, y, type);
                    int score = evaluate(point);
                    if (score >= PluginModel.LIANWU_SCORE) {
                        return singleton(point);
                    }

                    if (dangerLevel == 2) {
                        continue;
                    }

                    if (score >= MEDIUM_RISK) {
                        killPointList.add(point);
                    }

                    PluginPoint foePoint = new PluginPoint(x, y, opponent(type));
                    int foeScore = evaluate(foePoint);
                    int level = 0;
                    if (foeScore >= PluginModel.LIANWU_SCORE) {
                        level = 2;
                    } else if (foeScore >= MEDIUM_RISK) {
                        level = 1;
                    }

                    if (level > 0) {
                        if (dangerLevel < level) {
                            dangerLevel = level;
                            highPriorityPointList.clear();
                        }
                        highPriorityPointList.add(point);
                    }

                    if (dangerLevel > 0) {
                        continue;
                    }

                    if (between(score, LOW_RISK, MEDIUM_RISK) || between(foeScore, LOW_RISK, MEDIUM_RISK)) {
                        highPriorityPointList.add(point);
                        continue;
                    }

                    if (highPriorityPointList.isEmpty()) {
                        if (score >= PluginModel.CHONGSI_SCORE || foeScore >= PluginModel.CHONGSI_SCORE) {
                            lowPriorityPointList.add(point);
                            continue;
                        }
                        if (lowPriorityPointList.isEmpty() && score >= PluginModel.MIANYI_SCORE) {
                            alternatePointList.add(point);
                        }
                    }
                }
            }

            if (dangerLevel < 2 && !killPointList.isEmpty()) {
                sortPluginPoints(killPointList);
                return killPointList;
            }

            List<PluginPoint> pointList;
            if (highPriorityPointList.isEmpty()) {
                if (lowPriorityPointList.isEmpty()) {
                    if (alternatePointList.isEmpty()) {
                        return randomPoint(type, 1);
                    }
                    pointList = alternatePointList;
                } else {
                    pointList = lowPriorityPointList;
                }
            } else {
                pointList = highPriorityPointList;
            }

            sortPluginPoints(pointList);
            return new ArrayList<>(pointList.subList(0, Math.min(pointList.size(), maxNodes)));
        }

        private PluginPoint getBestPoint(List<PluginPoint> pointList) {
            List<PluginPoint> points = pointList == null ? new ArrayList<>() : new ArrayList<>(pointList);
            if (pointList == null) {
                for (int x = 0; x < SIZE; x++) {
                    for (int y = 0; y < SIZE; y++) {
                        if (chessData[x][y] == 0) {
                            points.add(new PluginPoint(x, y, ai));
                        }
                    }
                }
            }
            PluginPoint best = null;
            int bestScore = -INFINITY;
            for (PluginPoint point : points) {
                int score = Math.round(evaluate(point) * attack)
                        + evaluate(new PluginPoint(point.x, point.y, opponent(point.type)));
                score += Math.max(0, 14 - Math.abs(point.x - SIZE / 2) - Math.abs(point.y - SIZE / 2));
                if (score > bestScore) {
                    bestScore = score;
                    best = point;
                    best.score = score;
                }
            }
            return best;
        }

        private List<PluginPoint> randomPoint(int type, int num) {
            List<PluginPoint> points = new ArrayList<>();
            for (int x = 0; x < SIZE; x++) {
                for (int y = 0; y < SIZE; y++) {
                    if (chessData[x][y] == 0 && hasPluginNeighbor(x, y, 2)) {
                        PluginPoint point = new PluginPoint(x, y, type);
                        evaluate(point);
                        points.add(point);
                    }
                }
            }
            if (points.isEmpty()) {
                for (int x = 0; x < SIZE; x++) {
                    for (int y = 0; y < SIZE; y++) {
                        if (chessData[x][y] == 0) {
                            points.add(new PluginPoint(x, y, type));
                        }
                    }
                }
            }
            sortPluginPoints(points);
            return new ArrayList<>(points.subList(0, Math.min(num, points.size())));
        }

        private int evaluate(PluginPoint point) {
            int score = 0;
            int huosanTotal = 0;
            int chongsiTotal = 0;
            int tfTotal = 0;

            for (int direction = 1; direction < 5; direction++) {
                String situation = getSituation(point, direction);
                PluginModel model = getChessModel(situation);
                if (model == null) {
                    continue;
                }
                if ("HUOSAN".equals(model.id)) {
                    huosanTotal++;
                    if (checkSituation(situation, "CHONGSI")) {
                        tfTotal++;
                    }
                } else if ("CHONGSI".equals(model.id)) {
                    chongsiTotal++;
                }
                score += model.score;
            }

            if (chongsiTotal > 1 || tfTotal > 1) {
                score += HIGH_RISK;
            } else if ((chongsiTotal > 0 && huosanTotal > 0) || (tfTotal > 0 && huosanTotal > 1)) {
                score += MEDIUM_RISK;
            } else if (huosanTotal > 1) {
                score += LOW_RISK;
            }

            point.score = score;
            return score;
        }

        private int evaluateAll() {
            int aiScore = 0;
            int foeScore = 0;
            for (int x = 0; x < SIZE; x++) {
                for (int y = 0; y < SIZE; y++) {
                    int type = chessData[x][y];
                    if (type == 0) {
                        continue;
                    }
                    int val = evaluate(new PluginPoint(x, y, type));
                    if (type == ai) {
                        aiScore += val;
                    } else {
                        foeScore += val;
                    }
                }
            }
            return Math.round(aiScore * attack) - foeScore;
        }

        private boolean checkSituation(PluginPoint point, String modelId) {
            for (int direction = 1; direction < 5; direction++) {
                if (checkSituation(getSituation(point, direction), modelId)) {
                    return true;
                }
            }
            return false;
        }

        private boolean checkSituation(String situation, String modelId) {
            for (PluginModel model : PLUGIN_MODELS) {
                if (model.id.equals(modelId) && model.matches(situation)) {
                    return true;
                }
            }
            return false;
        }

        private PluginModel getChessModel(String situation) {
            for (PluginModel model : PLUGIN_MODELS) {
                if (model.matches(situation)) {
                    return model;
                }
            }
            return null;
        }

        private String getSituation(PluginPoint point, int direction) {
            int leftDirection = direction * 2 - 1;
            StringBuilder builder = new StringBuilder(9);
            appendChess(builder, point, leftDirection, 4);
            appendChess(builder, point, leftDirection, 3);
            appendChess(builder, point, leftDirection, 2);
            appendChess(builder, point, leftDirection, 1);
            builder.append('1');
            appendChess(builder, point, leftDirection + 1, 1);
            appendChess(builder, point, leftDirection + 1, 2);
            appendChess(builder, point, leftDirection + 1, 3);
            appendChess(builder, point, leftDirection + 1, 4);
            return builder.toString();
        }

        private void appendChess(StringBuilder builder, PluginPoint point, int direction, int offset) {
            int chess = relativePoint(point, direction, offset);
            if (chess < 0) {
                return;
            }
            if (point.type == 2 && chess > 0) {
                chess = opponent(chess);
            }
            builder.append(chess);
        }

        private int relativePoint(PluginPoint point, int direction, int offset) {
            int x = point.x;
            int y = point.y;
            switch (direction) {
                case 1:
                    x -= offset;
                    break;
                case 2:
                    x += offset;
                    break;
                case 3:
                    y -= offset;
                    break;
                case 4:
                    y += offset;
                    break;
                case 5:
                    x += offset;
                    y -= offset;
                    break;
                case 6:
                    x -= offset;
                    y += offset;
                    break;
                case 7:
                    x -= offset;
                    y -= offset;
                    break;
                case 8:
                    x += offset;
                    y += offset;
                    break;
                default:
                    return -1;
            }
            if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) {
                return -1;
            }
            return chessData[x][y];
        }

        private void putChess(PluginPoint point) {
            chessData[point.x][point.y] = point.type;
            hashcode ^= point.type == 1 ? BLACK_ZOBRIST[point.x][point.y] : WHITE_ZOBRIST[point.x][point.y];
        }

        private void revokeChess(PluginPoint point) {
            chessData[point.x][point.y] = 0;
            hashcode ^= point.type == 1 ? BLACK_ZOBRIST[point.x][point.y] : WHITE_ZOBRIST[point.x][point.y];
        }

        private boolean hasPluginNeighbor(int x, int y, int distance) {
            for (int dy = -distance; dy <= distance; dy++) {
                for (int dx = -distance; dx <= distance; dx++) {
                    if (dx == 0 && dy == 0) {
                        continue;
                    }
                    int nx = x + dx;
                    int ny = y + dy;
                    if (nx >= 0 && ny >= 0 && nx < SIZE && ny < SIZE && chessData[nx][ny] != 0) {
                        return true;
                    }
                }
            }
            return false;
        }

        private Point toPoint(PluginPoint point) {
            return new Point(point.x, point.y, point.score);
        }

        private static List<PluginPoint> singleton(PluginPoint point) {
            List<PluginPoint> result = new ArrayList<>(1);
            result.add(point);
            return result;
        }

        private static boolean between(int score, int left, int right) {
            return score >= left && score < right;
        }

        private static void sortPluginPoints(List<PluginPoint> points) {
            points.sort(Comparator
                    .comparingInt((PluginPoint point) -> point.score).reversed()
                    .thenComparingInt(point -> Math.abs(point.x - SIZE / 2) + Math.abs(point.y - SIZE / 2))
                    .thenComparingInt(point -> point.y)
                    .thenComparingInt(point -> point.x));
        }

        private static long[][] createZobrist(long seed) {
            long[][] values = new long[SIZE][SIZE];
            long current = seed;
            for (int x = 0; x < SIZE; x++) {
                for (int y = 0; y < SIZE; y++) {
                    current = nextZobrist(current);
                    values[x][y] = current;
                }
            }
            return values;
        }

        private static long nextZobrist(long value) {
            value ^= value << 13;
            value ^= value >>> 7;
            value ^= value << 17;
            return value;
        }
    }

    private static final class PluginPoint {
        private final int x;
        private final int y;
        private int type;
        private int score;

        private PluginPoint(int x, int y, int type) {
            this(x, y, type, 0);
        }

        private PluginPoint(int x, int y, int type, int score) {
            this.x = x;
            this.y = y;
            this.type = type;
            this.score = score;
        }
    }

    private static final class PluginCache {
        private final PluginPoint point;
        private final int score;
        private final int depth;
        private final boolean hasScore;

        private PluginCache(PluginPoint point, int depth) {
            this.point = point;
            this.score = 0;
            this.depth = depth;
            this.hasScore = false;
        }

        private PluginCache(int score, int depth) {
            this.point = null;
            this.score = score;
            this.depth = depth;
            this.hasScore = true;
        }
    }

    private static final class PluginModel {
        private static final int LIANWU_SCORE = 10_000_000;
        private static final int HUOSI_SCORE = 1_000_000;
        private static final int CHONGSI_SCORE = 9_000;
        private static final int MIANYI_SCORE = 1;

        private final String id;
        private final int score;
        private final String[] values;

        private PluginModel(String id, int score, String... values) {
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
