package cn.xeblog.server.game.gobang;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.game.gobang.GobangDTO;
import cn.xeblog.commons.enums.Game;
import cn.xeblog.server.pet.MiniGameRewards;
import cn.xeblog.server.pet.PetGameItemDeclarationService;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

@Slf4j
public final class GobangPetItemService {

    private static final int BOARD_SIZE = 15;
    private static final int[][] DIRECTIONS = new int[][]{
            {1, 0},
            {0, 1},
            {1, 1},
            {1, -1}
    };
    private static final String[] RUSH_FOUR_PATTERNS = new String[]{"OOOO_", "_OOOO", "OO_OO", "OOO_O", "O_OOO"};
    private static final String SLOT_GAMEPLAY = "gameplay";
    private static final String SLOT_INTERACTION = "interaction";
    private static final String ITEM_GUARD = "item_gomoku_guard";
    private static final String ITEM_FINISHER = "item_gomoku_finisher";
    private static final String ITEM_PREDICTION = "item_gomoku_prediction";
    private static final String ITEM_PROPHECY = "item_prophecy";
    private static final int PREDICTION_REWARD_BONES = 50;
    private static final int PROPHECY_REWARD_BONES = 20;
    private static final Map<String, RoomState> STATES = new ConcurrentHashMap<>();
    private static MiniGameRewards miniGameRewards = MiniGameRewards.petService();
    private static LongSupplier nowSupplier = System::currentTimeMillis;

    private GobangPetItemService() {
    }

    public static GobangDTO handleMove(User user, GameRoom room, GobangDTO dto) {
        if (user == null || room == null || dto == null || !isValidCell(dto.getX(), dto.getY())) {
            return null;
        }
        RoomState state = STATES.computeIfAbsent(room.getId(), id -> new RoomState(nowSupplier.getAsLong()));
        synchronized (state) {
            if (shouldSkipOpeningColorMessage(user, room, dto, state)) {
                state.started = true;
                recordOpeningTypes(user, room, dto, state);
                state.phase = "playing";
                state.turn = 1;
                state.winner = 0;
                recordHistory("START", user, room, dto, null, state, null);
                return null;
            }
            String playerKey = user.getIdentityKey();
            Integer expectedType = state.playerTypes.get(playerKey);
            if (!"playing".equals(state.phase)
                    || expectedType == null
                    || expectedType != dto.getType()
                    || state.turn != dto.getType()
                    || state.board[dto.getY()][dto.getX()] != 0) {
                return null;
            }
            settlePredictionForMove(room, playerKey, dto, state);
            state.started = true;
            state.board[dto.getY()][dto.getX()] = dto.getType();
            state.playerTypes.put(playerKey, dto.getType());
            armPredictionAfterMove(room, playerKey, dto, state);
            boolean winningMove = isWinningMove(state.board, dto.getX(), dto.getY(), dto.getType());
            if (!winningMove) {
                triggerGuardHints(room, playerKey, dto, state);
            }
            if (winningMove) {
                settleProphecies(room, playerKey, dto);
                applyMiniGameRewards(room, playerKey, state);
            }
            state.winner = winningMove ? dto.getType() : 0;
            state.phase = winningMove || isDraw(state.board) ? "over" : "playing";
            if ("over".equals(state.phase) && !state.gameOverReleased) {
                state.gameOverReleased = true;
                PetGameItemDeclarationService.releaseReservedForRoom(room);
            }
            state.turn = dto.getType() == 1 ? 2 : 1;
            state.moveSeq = state.moveHistory.size() + 1;
            GobangDTO acceptedMove = copyMove(room, dto);
            applyAuthoritativeFields(acceptedMove, state, "MOVE");
            state.moveHistory.add(copyMove(room, acceptedMove));
            recordHistory("MOVE", user, room, dto, acceptedMove, state, null);
            return acceptedMove;
        }
    }

    public static GobangDTO rejectedMove(GameRoom room, GobangDTO source) {
        return rejectedMove(null, room, source);
    }

    public static GobangDTO rejectedMove(User user, GameRoom room, GobangDTO source) {
        if (room == null || source == null) {
            return null;
        }
        GobangDTO dto = copyMove(room, source);
        RoomState state = STATES.get(room.getId());
        Map<String, Object> extra = historyExtra("reason", "server_rejected");
        if (state == null) {
            dto.setEvent("REJECTED");
            dto.setPhase("idle");
            dto.setTurn(1);
            dto.setWinner(0);
            dto.setMoveSeq(0);
            recordHistory("REJECTED", user, room, source, dto, null, extra);
            return dto;
        }
        synchronized (state) {
            applyAuthoritativeFields(dto, state, "REJECTED");
            recordHistory("REJECTED", user, room, source, dto, state, extra);
            return dto;
        }
    }

    public static List<GobangDTO> snapshotForUser(GameRoom room, User user) {
        if (room == null || user == null) {
            return new ArrayList<>();
        }
        RoomState state = STATES.get(room.getId());
        if (state == null) {
            return new ArrayList<>();
        }

        synchronized (state) {
            List<GobangDTO> snapshot = new ArrayList<>();
            Integer myType = state.playerTypes.get(user.getIdentityKey());
            if (myType != null) {
                GobangDTO start = copyMove(room, new GobangDTO(0, 0, myType));
                applyAuthoritativeFields(start, state, "START");
                snapshot.add(start);
            }
            state.moveHistory.forEach(move -> snapshot.add(copyMove(room, move)));
            return snapshot;
        }
    }

    public static boolean isOpeningColorMessage(User user, GameRoom room, GobangDTO dto) {
        if (user == null || room == null || dto == null) {
            return false;
        }
        if (!room.isHomeowner(user) || dto.getX() != 0 || dto.getY() != 0) {
            return false;
        }
        RoomState state = STATES.get(room.getId());
        if (state == null) {
            return true;
        }
        synchronized (state) {
            return shouldSkipOpeningColorMessage(user, room, dto, state);
        }
    }

    public static void clearRoom(GameRoom room) {
        if (room != null) {
            clearRoom(room, room.getId(), "CLEAR_ROOM");
        }
    }

    public static void clearRoom(String roomId) {
        clearRoom(null, roomId, "CLEAR_ROOM");
    }

    private static void clearRoom(GameRoom room, String roomId, String reason) {
        if (roomId != null) {
            RoomState state = STATES.remove(roomId);
            if (state != null) {
                synchronized (state) {
                    recordHistory(reason, null, room, roomId, null, null, state, null);
                }
            }
        }
    }

    public static boolean undoLastMoves(GameRoom room, int steps) {
        return undoLastMoves(null, room, steps);
    }

    public static boolean undoLastMoves(User user, GameRoom room, int steps) {
        if (room == null || steps <= 0) {
            return false;
        }
        RoomState state = STATES.get(room.getId());
        if (state == null) {
            return false;
        }
        synchronized (state) {
            if (state.moveHistory.size() < steps) {
                return false;
            }
            List<GobangDTO> removedMoves = new ArrayList<>();
            for (int i = 0; i < steps; i++) {
                removedMoves.add(0, copyMove(room, state.moveHistory.remove(state.moveHistory.size() - 1)));
            }
            resetBoard(state);
            for (GobangDTO move : state.moveHistory) {
                if (isValidCell(move.getX(), move.getY()) && (move.getType() == 1 || move.getType() == 2)) {
                    state.board[move.getY()][move.getX()] = move.getType();
                }
            }
            GobangDTO lastMove = state.moveHistory.isEmpty()
                    ? null
                    : state.moveHistory.get(state.moveHistory.size() - 1);
            state.turn = lastMove == null ? 1 : lastMove.getType() == 1 ? 2 : 1;
            state.phase = "playing";
            state.winner = 0;
            state.moveSeq = state.moveHistory.size();
            state.pendingByTarget.clear();
            Map<String, Object> extra = historyExtra("steps", steps);
            extra.put("removedMoves", moveHistorySnapshot(removedMoves));
            recordHistory("UNDO", user, room, null, null, state, extra);
            return true;
        }
    }

    public static GobangDTO useFinisherItem(User user, GameRoom room, String itemId, Integer slotIndex) {
        if (user == null || room == null || !ITEM_FINISHER.equals(itemId) || !room.isPlayerConnection(user)) {
            return null;
        }
        RoomState state = STATES.get(room.getId());
        if (state == null) {
            return null;
        }
        synchronized (state) {
            String playerKey = user.getIdentityKey();
            Integer playerType = state.playerTypes.get(playerKey);
            if (!"playing".equals(state.phase) || playerType == null || state.turn != playerType) {
                GobangDTO result = itemEvent(room, state, ITEM_FINISHER, slotIndex,
                        "妙手骨只能在自己的回合使用。", null, false);
                recordHistory("ITEM_HINT", user, room, null, result, state, historyExtra("itemId", ITEM_FINISHER));
                return result;
            }
            GameRoom.Player player = room.getUsers().get(playerKey);
            String slot = carriedItemSlot(player, ITEM_FINISHER, slotIndex);
            if (slot == null) {
                return null;
            }
            Cell threat = findWinningHandCell(state.board, playerType);
            if (threat == null) {
                GobangDTO result = itemEvent(room, state, ITEM_FINISHER, slotIndex,
                        "妙手骨暂未发现对手一手挡不完的妙手，道具未消耗。", null, false);
                recordHistory("ITEM_HINT", user, room, null, result, state, historyExtra("itemId", ITEM_FINISHER));
                return result;
            }
            if (!PetGameItemDeclarationService.ensureReservedForUse(room, playerKey, ITEM_FINISHER, slot)) {
                return null;
            }
            PetGameItemDeclarationService.settleConsumed(room, playerKey, ITEM_FINISHER, slot);
            clearCarriedItem(player, slot);
            GobangDTO result = itemEvent(room, state, ITEM_FINISHER, slotIndex,
                    String.format("妙手骨触发，已高亮你的妙手 (%d,%d)，道具已消耗。", threat.x, threat.y),
                    threat,
                    true);
            recordHistory("ITEM_HINT", user, room, null, result, state, historyExtra("itemId", ITEM_FINISHER));
            return result;
        }
    }

    public static void setMiniGameRewardsForTest(MiniGameRewards testMiniGameRewards) {
        miniGameRewards = testMiniGameRewards == null ? MiniGameRewards.petService() : testMiniGameRewards;
    }

    public static void resetMiniGameRewards() {
        miniGameRewards = MiniGameRewards.petService();
    }

    public static void setNowSupplierForTest(LongSupplier testNowSupplier) {
        nowSupplier = testNowSupplier == null ? System::currentTimeMillis : testNowSupplier;
    }

    public static void resetNowSupplier() {
        nowSupplier = System::currentTimeMillis;
    }

    private static boolean shouldSkipOpeningColorMessage(User user, GameRoom room, GobangDTO dto, RoomState state) {
        return !state.started
                && state.moveCount() == 0
                && dto.getX() == 0
                && dto.getY() == 0
                && room.isHomeowner(user);
    }

    private static void recordOpeningTypes(User user, GameRoom room, GobangDTO dto, RoomState state) {
        if (dto.getType() != 1 && dto.getType() != 2) {
            return;
        }
        String homeownerKey = user.getIdentityKey();
        state.playerTypes.put(homeownerKey, 3 - dto.getType());
        String opponentKey = firstOtherPlayerKey(room, homeownerKey);
        if (opponentKey != null) {
            state.playerTypes.put(opponentKey, dto.getType());
        }
    }

    private static GobangDTO copyMove(GameRoom room, GobangDTO source) {
        GobangDTO dto = new GobangDTO(source.getX(), source.getY(), source.getType());
        dto.setRoomId(room.getId());
        dto.setGame(Game.GOBANG);
        dto.setEvent(source.getEvent());
        dto.setPhase(source.getPhase());
        dto.setTurn(source.getTurn());
        dto.setWinner(source.getWinner());
        dto.setMoveSeq(source.getMoveSeq());
        dto.setPetItemNotice(source.getPetItemNotice());
        dto.setPetItemId(source.getPetItemId());
        dto.setPetItemSlotIndex(source.getPetItemSlotIndex());
        dto.setPetItemConsumed(source.getPetItemConsumed());
        dto.setPetItemGuardX(source.getPetItemGuardX());
        dto.setPetItemGuardY(source.getPetItemGuardY());
        return dto;
    }

    private static void applyAuthoritativeFields(GobangDTO dto, RoomState state, String event) {
        dto.setEvent(event);
        dto.setPhase(state.phase);
        dto.setTurn(state.turn);
        dto.setWinner(state.winner);
        dto.setMoveSeq(state.moveSeq);
    }

    private static void settlePredictionForMove(GameRoom room, String playerKey, GobangDTO dto, RoomState state) {
        PendingPrediction prediction = state.pendingByTarget.remove(playerKey);
        if (prediction == null) {
            return;
        }
        GameRoom.Player carrier = room.getUsers().get(prediction.carrierKey);
        if (carrier == null) {
            return;
        }
        clearCarriedItem(carrier, prediction.slot);
        if (prediction.x == dto.getX() && prediction.y == dto.getY()) {
            int reward = PetGameItemDeclarationService.settleSucceededWithInteractionReward(
                    room,
                    prediction.carrierKey,
                    ITEM_PREDICTION,
                    prediction.slot,
                    PREDICTION_REWARD_BONES);
            appendNotice(dto, successNotice(carrier, prediction, reward));
            return;
        }
        PetGameItemDeclarationService.settleFailed(room, prediction.carrierKey, ITEM_PREDICTION, prediction.slot);
        appendNotice(dto, String.format(
                "猜你落这儿未命中，%s 预测 (%d,%d)，实际落子 (%d,%d)，道具已消耗。",
                playerName(carrier),
                prediction.x,
                prediction.y,
                dto.getX(),
                dto.getY()));
    }

    private static void armPredictionAfterMove(GameRoom room, String playerKey, GobangDTO dto, RoomState state) {
        GameRoom.Player player = room.getUsers().get(playerKey);
        String slot = carriedItemSlot(player, ITEM_PREDICTION);
        if (slot == null) {
            return;
        }
        if (hasPendingPredictionForCarrier(state, playerKey)) {
            return;
        }
        String targetKey = firstOtherPlayerKey(room, playerKey);
        if (targetKey == null) {
            return;
        }
        Cell target = pickPredictionCell(state, dto.getX(), dto.getY());
        if (target == null) {
            PetGameItemDeclarationService.settleRefunded(room, playerKey, ITEM_PREDICTION, slot);
            clearCarriedItem(player, slot);
            return;
        }
        state.pendingByTarget.put(targetKey, new PendingPrediction(playerKey, slot, target.x, target.y));
    }

    private static void settleProphecies(GameRoom room, String winnerPlayerKey, GobangDTO dto) {
        for (GameRoom.Player player : room.getUsers().values()) {
            String playerKey = player.getId();
            String slot = carriedItemSlot(player, ITEM_PROPHECY);
            if (slot == null) {
                continue;
            }
            if (playerKey.equals(winnerPlayerKey)) {
                int reward = PetGameItemDeclarationService.settleSucceededWithInteractionReward(
                        room, playerKey, ITEM_PROPHECY, slot, PROPHECY_REWARD_BONES);
                if (reward > 0) {
                    appendNotice(dto, "胜负预言贴命中，" + playerName(player)
                            + " 成为唯一胜者，返还道具并获得 🦴" + reward + "。");
                } else {
                    appendNotice(dto, "胜负预言贴命中，" + playerName(player)
                            + " 成为唯一胜者，返还道具；今日互动奖励额度已用完。");
                }
            } else {
                PetGameItemDeclarationService.settleFailed(room, playerKey, ITEM_PROPHECY, slot);
                appendNotice(dto, "胜负预言贴未命中，" + playerName(player)
                        + " 未成为唯一胜者，道具已消耗。");
            }
            clearCarriedItem(player, slot);
        }
    }

    private static void applyMiniGameRewards(GameRoom room, String winnerPlayerKey, RoomState state) {
        if (state.miniGameRewardsApplied) {
            return;
        }
        state.miniGameRewardsApplied = true;
        long durationSeconds = Math.max(0L, (nowSupplier.getAsLong() - state.startedAt + 999L) / 1000L);
        List<Long> accountIds = new ArrayList<>();
        for (GameRoom.Player player : room.getUsers().values()) {
            if (player.getAccountId() <= 0) {
                continue;
            }
            accountIds.add(player.getAccountId());
            try {
                miniGameRewards.apply(player.getAccountId(), Game.GOBANG,
                        player.getId().equals(winnerPlayerKey), durationSeconds);
            } catch (RuntimeException e) {
                log.warn("五子棋小游戏产出结算失败 -> accountId: {}", player.getAccountId(), e);
            }
        }
        try {
            miniGameRewards.applyRoomBonus(Game.GOBANG, accountIds, durationSeconds);
        } catch (RuntimeException e) {
            log.warn("五子棋房间级彩蛋奖励结算失败 -> accountIds: {}", accountIds, e);
        }
    }

    private static void triggerGuardHints(GameRoom room, String moverKey, GobangDTO dto, RoomState state) {
        if (dto.getType() != 1 && dto.getType() != 2) {
            return;
        }
        GuardThreat threat = null;
        for (GameRoom.Player player : room.getUsers().values()) {
            String playerKey = player.getId();
            String slot = carriedItemSlot(player, ITEM_GUARD);
            if (playerKey.equals(moverKey) || slot == null) {
                continue;
            }
            if (threat == null) {
                threat = findGuardThreat(state.board, dto.getType());
            }
            if (threat == null) {
                return;
            }
            PetGameItemDeclarationService.settleConsumed(room, playerKey, ITEM_GUARD, slot);
            clearCarriedItem(player, slot);
            dto.setPetItemGuardX(threat.cell.x);
            dto.setPetItemGuardY(threat.cell.y);
            appendNotice(dto, String.format(
                    "守门骨触发，已为 %s 高亮对手隐蔽多重威胁点 (%d,%d)，道具已消耗。",
                    playerName(player),
                    threat.cell.x,
                    threat.cell.y));
        }
    }

    private static boolean hasPendingPredictionForCarrier(RoomState state, String playerKey) {
        for (PendingPrediction prediction : state.pendingByTarget.values()) {
            if (prediction.carrierKey.equals(playerKey)) {
                return true;
            }
        }
        return false;
    }

    private static String firstOtherPlayerKey(GameRoom room, String playerKey) {
        for (String key : room.getUsers().keySet()) {
            if (!key.equals(playerKey)) {
                return key;
            }
        }
        return null;
    }

    private static Cell pickPredictionCell(RoomState state, int x, int y) {
        int[][] preferred = new int[][]{
                {x + 1, y},
                {x - 1, y},
                {x, y + 1},
                {x, y - 1}
        };
        for (int[] cell : preferred) {
            if (isEmpty(state, cell[0], cell[1])) {
                return new Cell(cell[0], cell[1]);
            }
        }
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                if (isEmpty(state, col, row)) {
                    return new Cell(col, row);
                }
            }
        }
        return null;
    }

    private static boolean isEmpty(RoomState state, int x, int y) {
        return isValidCell(x, y) && state.board[y][x] == 0;
    }

    private static boolean isValidCell(int x, int y) {
        return x >= 0 && x < BOARD_SIZE && y >= 0 && y < BOARD_SIZE;
    }

    private static boolean isDraw(int[][] board) {
        for (int y = 0; y < BOARD_SIZE; y++) {
            for (int x = 0; x < BOARD_SIZE; x++) {
                if (board[y][x] == 0) {
                    return false;
                }
            }
        }
        return true;
    }

    private static GuardThreat findGuardThreat(int[][] board, int type) {
        if (type != 1 && type != 2) {
            return null;
        }
        List<Cell> winningCells = findWinningCells(board, type);
        int liveThreeCount = countLiveThreeLines(board, type);
        List<Cell> criticalCells = findGuardCriticalCells(board, type);
        if (winningCells.size() == 1 && (liveThreeCount > 0 || hasSecondaryForcingMove(board, type, winningCells.get(0)))) {
            return new GuardThreat(winningCells.get(0));
        }
        if (!winningCells.isEmpty()) {
            return null;
        }
        if (!criticalCells.isEmpty()) {
            Cell criticalCell = findGuardPreventiveCell(board, type, criticalCells);
            return criticalCell == null ? null : new GuardThreat(criticalCell);
        }
        if (liveThreeCount > 1) {
            Cell defenseCell = findGuardDefenseCell(board, type);
            return defenseCell == null ? null : new GuardThreat(defenseCell);
        }
        return null;
    }

    private static Cell findGuardPreventiveCell(int[][] board, int type, List<Cell> criticalCells) {
        int defenderType = type == 1 ? 2 : 1;
        for (int y = 0; y < BOARD_SIZE; y++) {
            for (int x = 0; x < BOARD_SIZE; x++) {
                if (board[y][x] != 0 || !containsCell(criticalCells, x, y)) {
                    continue;
                }
                board[y][x] = defenderType;
                boolean blocksThreat = findWinningCells(board, type).isEmpty()
                        && findGuardCriticalCell(board, type) == null;
                board[y][x] = 0;
                if (blocksThreat) {
                    return new Cell(x, y);
                }
            }
        }
        return null;
    }

    private static Cell findGuardDefenseCell(int[][] board, int type) {
        int defenderType = type == 1 ? 2 : 1;
        for (int y = 0; y < BOARD_SIZE; y++) {
            for (int x = 0; x < BOARD_SIZE; x++) {
                if (board[y][x] != 0) {
                    continue;
                }
                board[y][x] = defenderType;
                boolean blocksThreat = findWinningCells(board, type).isEmpty()
                        && countLiveThreeLines(board, type) <= 1;
                board[y][x] = 0;
                if (blocksThreat) {
                    return new Cell(x, y);
                }
            }
        }
        return null;
    }

    private static boolean containsCell(List<Cell> cells, int x, int y) {
        for (Cell cell : cells) {
            if (cell.x == x && cell.y == y) {
                return true;
            }
        }
        return false;
    }

    private static List<Cell> findGuardCriticalCells(int[][] board, int type) {
        List<Cell> cells = new ArrayList<>();
        for (int y = 0; y < BOARD_SIZE; y++) {
            for (int x = 0; x < BOARD_SIZE; x++) {
                if (board[y][x] != 0) {
                    continue;
                }
                board[y][x] = type;
                int liveFourDirections = countLiveFourDirections(board, x, y, type);
                int rushFourDirections = countRushFourDirections(board, x, y, type);
                int openThreeDirections = countOpenThreeDirections(board, x, y, type);
                boolean createsThreat = !isWinningMove(board, x, y, type)
                        && ((liveFourDirections > 0
                        && liveFourDirections + rushFourDirections + openThreeDirections > 1)
                        || createsMultiThreat(rushFourDirections, openThreeDirections));
                board[y][x] = 0;
                if (createsThreat) {
                    cells.add(new Cell(x, y));
                }
            }
        }
        return cells;
    }

    private static Cell findGuardCriticalCell(int[][] board, int type) {
        List<Cell> cells = findGuardCriticalCells(board, type);
        return cells.isEmpty() ? null : cells.get(0);
    }

    private static boolean hasSecondaryForcingMove(int[][] board, int type, Cell winningCell) {
        Set<String> winningLineKeys = winningLineKeys(board, type, winningCell);
        for (int y = 0; y < BOARD_SIZE; y++) {
            for (int x = 0; x < BOARD_SIZE; x++) {
                if (board[y][x] != 0) {
                    continue;
                }
                board[y][x] = type;
                boolean secondaryThreat = false;
                if (!isWinningMove(board, x, y, type)) {
                    for (int i = 0; i < DIRECTIONS.length; i++) {
                        String line = lineThrough(board, x, y, type, DIRECTIONS[i][0], DIRECTIONS[i][1]);
                        if (!hasLiveFour(line) && hasRushFour(line) && !winningLineKeys.contains(lineKey(i, x, y))) {
                            secondaryThreat = true;
                            break;
                        }
                    }
                }
                board[y][x] = 0;
                if (secondaryThreat) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Set<String> winningLineKeys(int[][] board, int type, Cell winningCell) {
        Set<String> keys = new HashSet<>();
        if (board[winningCell.y][winningCell.x] != 0) {
            return keys;
        }
        board[winningCell.y][winningCell.x] = type;
        for (int i = 0; i < DIRECTIONS.length; i++) {
            if (countLine(board, winningCell.x, winningCell.y, type, DIRECTIONS[i][0], DIRECTIONS[i][1]) >= 5) {
                keys.add(lineKey(i, winningCell.x, winningCell.y));
            }
        }
        board[winningCell.y][winningCell.x] = 0;
        return keys;
    }

    private static int countLiveThreeLines(int[][] board, int type) {
        Set<String> lines = new HashSet<>();
        for (int y = 0; y < BOARD_SIZE; y++) {
            for (int x = 0; x < BOARD_SIZE; x++) {
                if (board[y][x] != type) {
                    continue;
                }
                for (int i = 0; i < DIRECTIONS.length; i++) {
                    if (hasOpenThree(lineThrough(board, x, y, type, DIRECTIONS[i][0], DIRECTIONS[i][1]))) {
                        lines.add(lineKey(i, x, y));
                    }
                }
            }
        }
        return lines.size();
    }

    private static String lineKey(int directionIndex, int x, int y) {
        if (directionIndex == 0) {
            return "h:" + y;
        }
        if (directionIndex == 1) {
            return "v:" + x;
        }
        if (directionIndex == 2) {
            return "d1:" + (y - x);
        }
        return "d2:" + (x + y);
    }

    private static Cell findWinningCell(int[][] board, int type) {
        List<Cell> cells = findWinningCells(board, type);
        return cells.isEmpty() ? null : cells.get(0);
    }

    private static List<Cell> findWinningCells(int[][] board, int type) {
        List<Cell> cells = new ArrayList<>();
        if (type != 1 && type != 2) {
            return cells;
        }
        for (int y = 0; y < BOARD_SIZE; y++) {
            for (int x = 0; x < BOARD_SIZE; x++) {
                if (board[y][x] != 0) {
                    continue;
                }
                board[y][x] = type;
                boolean wins = isWinningMove(board, x, y, type);
                board[y][x] = 0;
                if (wins) {
                    cells.add(new Cell(x, y));
                }
            }
        }
        return cells;
    }

    private static Cell findWinningHandCell(int[][] board, int type) {
        if (type != 1 && type != 2) {
            return null;
        }
        for (int y = 0; y < BOARD_SIZE; y++) {
            for (int x = 0; x < BOARD_SIZE; x++) {
                if (board[y][x] != 0) {
                    continue;
                }
                board[y][x] = type;
                if (isWinningMove(board, x, y, type)) {
                    board[y][x] = 0;
                    continue;
                }
                boolean createsWinningHand = hasLiveFour(board, x, y, type)
                        || createsMultiThreat(board, x, y, type);
                board[y][x] = 0;
                if (createsWinningHand) {
                    return new Cell(x, y);
                }
            }
        }
        return null;
    }

    private static boolean isWinningMove(int[][] board, int x, int y, int type) {
        if (type != 1 && type != 2) {
            return false;
        }
        return countLine(board, x, y, type, 1, 0) >= 5
                || countLine(board, x, y, type, 0, 1) >= 5
                || countLine(board, x, y, type, 1, 1) >= 5
                || countLine(board, x, y, type, 1, -1) >= 5;
    }

    private static boolean createsMultiThreat(int[][] board, int x, int y, int type) {
        int rushFourDirections = countRushFourDirections(board, x, y, type);
        int openThreeDirections = countOpenThreeDirections(board, x, y, type);
        return createsMultiThreat(rushFourDirections, openThreeDirections);
    }

    private static boolean createsMultiThreat(int rushFourDirections, int openThreeDirections) {
        return rushFourDirections > 1
                || openThreeDirections > 1
                || (rushFourDirections > 0 && openThreeDirections > 0);
    }

    private static int countLine(int[][] board, int x, int y, int type, int dx, int dy) {
        return 1
                + countDirection(board, x, y, type, dx, dy)
                + countDirection(board, x, y, type, -dx, -dy);
    }

    private static boolean hasLiveFour(int[][] board, int x, int y, int type) {
        return countLiveFourDirections(board, x, y, type) > 0;
    }

    private static int countLiveFourDirections(int[][] board, int x, int y, int type) {
        int count = 0;
        for (int[] direction : DIRECTIONS) {
            if (hasLiveFour(lineThrough(board, x, y, type, direction[0], direction[1]))) {
                count++;
            }
        }
        return count;
    }

    private static int countOpenThreeDirections(int[][] board, int x, int y, int type) {
        int count = 0;
        for (int[] direction : DIRECTIONS) {
            String line = lineThrough(board, x, y, type, direction[0], direction[1]);
            if (!hasLiveFour(line) && !hasRushFour(line) && hasOpenThree(line)) {
                count++;
            }
        }
        return count;
    }

    private static int countRushFourDirections(int[][] board, int x, int y, int type) {
        int count = 0;
        for (int[] direction : DIRECTIONS) {
            String line = lineThrough(board, x, y, type, direction[0], direction[1]);
            if (!hasLiveFour(line) && hasRushFour(line)) {
                count++;
            }
        }
        return count;
    }

    private static boolean hasRushFour(String line) {
        for (String pattern : RUSH_FOUR_PATTERNS) {
            if (hasCenteredPattern(line, pattern)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasLiveFour(String line) {
        return hasCenteredPattern(line, "_OOOO_");
    }

    private static boolean hasOpenThree(String line) {
        return hasCenteredPattern(line, "__OOO_")
                || hasCenteredPattern(line, "_OOO__")
                || hasCenteredPattern(line, "_OO_O_")
                || hasCenteredPattern(line, "_O_OO_");
    }

    private static String lineThrough(int[][] board, int x, int y, int type, int dx, int dy) {
        StringBuilder builder = new StringBuilder(9);
        for (int offset = -4; offset <= 4; offset++) {
            int nextX = x + dx * offset;
            int nextY = y + dy * offset;
            if (!isValidCell(nextX, nextY)) {
                builder.append('X');
                continue;
            }
            int value = board[nextY][nextX];
            builder.append(value == 0 ? '_' : value == type ? 'O' : 'X');
        }
        return builder.toString();
    }

    private static boolean hasCenteredPattern(String line, String pattern) {
        int center = 4;
        for (int start = 0; start <= line.length() - pattern.length(); start++) {
            if (start <= center && center < start + pattern.length()
                    && line.regionMatches(start, pattern, 0, pattern.length())) {
                return true;
            }
        }
        return false;
    }

    private static void resetBoard(RoomState state) {
        for (int y = 0; y < BOARD_SIZE; y++) {
            for (int x = 0; x < BOARD_SIZE; x++) {
                state.board[y][x] = 0;
            }
        }
    }

    private static int countDirection(int[][] board, int x, int y, int type, int dx, int dy) {
        int count = 0;
        int nextX = x + dx;
        int nextY = y + dy;
        while (isValidCell(nextX, nextY) && board[nextY][nextX] == type) {
            count++;
            nextX += dx;
            nextY += dy;
        }
        return count;
    }

    private static void appendNotice(GobangDTO dto, String notice) {
        if (dto == null || trimToNull(notice) == null) {
            return;
        }
        String current = trimToNull(dto.getPetItemNotice());
        dto.setPetItemNotice(current == null ? notice : current + " " + notice);
    }

    private static String successNotice(GameRoom.Player carrier, PendingPrediction prediction, int reward) {
        String rewardText = reward > 0
                ? "返还道具并获得 🦴" + reward
                : "返还道具，今日互动奖金已达上限";
        return String.format(
                "猜你落这儿命中，%s 预测对手下一手 (%d,%d)，%s。",
                playerName(carrier),
                prediction.x,
                prediction.y,
                rewardText);
    }

    private static String playerName(GameRoom.Player player) {
        String nickname = trimToNull(player.getNickname());
        if (nickname != null) {
            return nickname;
        }
        String username = trimToNull(player.getUsername());
        return username == null ? "玩家" : username;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String carriedItemSlot(GameRoom.Player player, String itemId) {
        return carriedItemSlot(player, itemId, null);
    }

    private static String carriedItemSlot(GameRoom.Player player, String itemId, Integer slotIndex) {
        if (player == null || itemId == null) {
            return null;
        }
        if (slotIndex != null) {
            if (slotIndex <= 0 && itemId.equals(player.getPetPlayItemId())) {
                return SLOT_GAMEPLAY;
            }
            if (slotIndex == 1 && itemId.equals(player.getPetInteractionItemId())) {
                return SLOT_INTERACTION;
            }
        }
        if (itemId.equals(player.getPetPlayItemId())) {
            return SLOT_GAMEPLAY;
        }
        if (itemId.equals(player.getPetInteractionItemId())) {
            return SLOT_INTERACTION;
        }
        return null;
    }

    private static void clearCarriedItem(GameRoom.Player player, String slot) {
        if (SLOT_GAMEPLAY.equals(slot)) {
            player.setPetPlayItemId(null);
        } else if (SLOT_INTERACTION.equals(slot)) {
            player.setPetInteractionItemId(null);
        }
    }

    private static GobangDTO itemEvent(GameRoom room, RoomState state, String itemId, Integer slotIndex,
                                       String notice, Cell hint, boolean consumed) {
        GobangDTO dto = new GobangDTO(0, 0, 0);
        dto.setRoomId(room.getId());
        dto.setGame(Game.GOBANG);
        dto.setEvent("ITEM_HINT");
        dto.setPhase(state.phase);
        dto.setTurn(state.turn);
        dto.setWinner(state.winner);
        dto.setMoveSeq(state.moveSeq);
        dto.setPetItemNotice(notice);
        dto.setPetItemId(itemId);
        dto.setPetItemSlotIndex(normalizeSlotIndex(slotIndex));
        dto.setPetItemConsumed(consumed);
        if (hint != null) {
            dto.setPetItemGuardX(hint.x);
            dto.setPetItemGuardY(hint.y);
        }
        return dto;
    }

    private static int normalizeSlotIndex(Integer slotIndex) {
        return slotIndex != null && slotIndex > 0 ? 1 : 0;
    }

    private static void recordHistory(String event, User actor, GameRoom room, GobangDTO request,
                                      GobangDTO response, RoomState state, Map<String, Object> extra) {
        String roomId = room == null ? null : room.getId();
        if (roomId == null && request != null) {
            roomId = request.getRoomId();
        }
        if (roomId == null && response != null) {
            roomId = response.getRoomId();
        }
        recordHistory(event, actor, room, roomId, request, response, state, extra);
    }

    private static void recordHistory(String event, User actor, GameRoom room, String roomId,
                                      GobangDTO request, GobangDTO response, RoomState state,
                                      Map<String, Object> extra) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", event);
        payload.put("roomId", roomId);
        payload.put("actor", actorSnapshot(actor));
        payload.put("room", roomSnapshot(room));
        payload.put("request", dtoSnapshot(request));
        payload.put("response", dtoSnapshot(response));
        payload.put("state", stateSnapshot(state));
        if (extra != null && !extra.isEmpty()) {
            payload.put("extra", extra);
        }
        GobangHistoryService.record(roomId, payload);
    }

    private static Map<String, Object> historyExtra(String key, Object value) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put(key, value);
        return extra;
    }

    private static Map<String, Object> actorSnapshot(User user) {
        if (user == null) {
            return null;
        }
        Map<String, Object> actor = new LinkedHashMap<>();
        actor.put("identityKey", user.getIdentityKey());
        actor.put("channelId", user.getId());
        actor.put("accountId", user.getAccountId() > 0 ? user.getAccountId() : null);
        actor.put("account", trimToNull(user.getAccount()));
        actor.put("username", trimToNull(user.getUsername()));
        actor.put("nickname", trimToNull(user.getNickname()));
        return actor;
    }

    private static Map<String, Object> roomSnapshot(GameRoom room) {
        if (room == null) {
            return null;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", room.getId());
        snapshot.put("game", room.getGame() == null ? null : room.getGame().name());
        snapshot.put("nums", room.getNums());
        snapshot.put("homeowner", actorSnapshot(room.getHomeowner()));
        List<Map<String, Object>> players = new ArrayList<>();
        for (GameRoom.Player player : room.getUsers().values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("identityKey", player.getId());
            item.put("channelId", player.getChannelId());
            item.put("accountId", player.getAccountId() > 0 ? player.getAccountId() : null);
            item.put("account", trimToNull(player.getAccount()));
            item.put("username", trimToNull(player.getUsername()));
            item.put("nickname", trimToNull(player.getNickname()));
            item.put("readied", player.isReadied());
            item.put("petPlayItemId", trimToNull(player.getPetPlayItemId()));
            item.put("petInteractionItemId", trimToNull(player.getPetInteractionItemId()));
            players.add(item);
        }
        snapshot.put("players", players);
        return snapshot;
    }

    private static Map<String, Object> stateSnapshot(RoomState state) {
        if (state == null) {
            return null;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("phase", state.phase);
        snapshot.put("turn", state.turn);
        snapshot.put("winner", state.winner);
        snapshot.put("moveSeq", state.moveSeq);
        snapshot.put("started", state.started);
        snapshot.put("startedAt", state.startedAt);
        snapshot.put("moveCount", state.moveCount());
        snapshot.put("miniGameRewardsApplied", state.miniGameRewardsApplied);
        snapshot.put("gameOverReleased", state.gameOverReleased);
        snapshot.put("playerTypes", new LinkedHashMap<>(state.playerTypes));
        snapshot.put("pendingPredictions", pendingPredictionSnapshot(state));
        snapshot.put("moveHistory", moveHistorySnapshot(state.moveHistory));
        snapshot.put("board", boardSnapshot(state.board));
        return snapshot;
    }

    private static List<Map<String, Object>> pendingPredictionSnapshot(RoomState state) {
        List<Map<String, Object>> predictions = new ArrayList<>();
        for (Map.Entry<String, PendingPrediction> entry : state.pendingByTarget.entrySet()) {
            PendingPrediction prediction = entry.getValue();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("targetKey", entry.getKey());
            item.put("carrierKey", prediction.carrierKey);
            item.put("slot", prediction.slot);
            item.put("x", prediction.x);
            item.put("y", prediction.y);
            predictions.add(item);
        }
        return predictions;
    }

    private static List<Map<String, Object>> moveHistorySnapshot(List<GobangDTO> moves) {
        List<Map<String, Object>> history = new ArrayList<>();
        if (moves == null) {
            return history;
        }
        for (GobangDTO move : moves) {
            history.add(dtoSnapshot(move));
        }
        return history;
    }

    private static List<List<Integer>> boardSnapshot(int[][] board) {
        List<List<Integer>> rows = new ArrayList<>();
        if (board == null) {
            return rows;
        }
        for (int y = 0; y < BOARD_SIZE; y++) {
            List<Integer> row = new ArrayList<>();
            for (int x = 0; x < BOARD_SIZE; x++) {
                row.add(board[y][x]);
            }
            rows.add(row);
        }
        return rows;
    }

    private static Map<String, Object> dtoSnapshot(GobangDTO dto) {
        if (dto == null) {
            return null;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("roomId", dto.getRoomId());
        snapshot.put("game", dto.getGame() == null ? null : dto.getGame().name());
        snapshot.put("x", dto.getX());
        snapshot.put("y", dto.getY());
        snapshot.put("type", dto.getType());
        snapshot.put("event", dto.getEvent());
        snapshot.put("phase", dto.getPhase());
        snapshot.put("turn", dto.getTurn());
        snapshot.put("winner", dto.getWinner());
        snapshot.put("moveSeq", dto.getMoveSeq());
        snapshot.put("petItemNotice", trimToNull(dto.getPetItemNotice()));
        snapshot.put("petItemId", trimToNull(dto.getPetItemId()));
        snapshot.put("petItemSlotIndex", dto.getPetItemSlotIndex());
        snapshot.put("petItemConsumed", dto.getPetItemConsumed());
        snapshot.put("petItemGuardX", dto.getPetItemGuardX());
        snapshot.put("petItemGuardY", dto.getPetItemGuardY());
        return snapshot;
    }

    private static final class RoomState {
        private final int[][] board = new int[BOARD_SIZE][BOARD_SIZE];
        private final Map<String, PendingPrediction> pendingByTarget = new ConcurrentHashMap<>();
        private final Map<String, Integer> playerTypes = new ConcurrentHashMap<>();
        private final List<GobangDTO> moveHistory = new ArrayList<>();
        private final long startedAt;
        private String phase = "idle";
        private int turn = 1;
        private int winner;
        private int moveSeq;
        private boolean started;
        private boolean miniGameRewardsApplied;
        private boolean gameOverReleased;

        private RoomState(long startedAt) {
            this.startedAt = startedAt;
        }

        private int moveCount() {
            int count = 0;
            for (int y = 0; y < BOARD_SIZE; y++) {
                for (int x = 0; x < BOARD_SIZE; x++) {
                    if (board[y][x] != 0) {
                        count++;
                    }
                }
            }
            return count;
        }
    }

    private static final class PendingPrediction {
        private final String carrierKey;
        private final String slot;
        private final int x;
        private final int y;

        private PendingPrediction(String carrierKey, String slot, int x, int y) {
            this.carrierKey = carrierKey;
            this.slot = slot;
            this.x = x;
            this.y = y;
        }
    }

    private static final class GuardThreat {
        private final Cell cell;

        private GuardThreat(Cell cell) {
            this.cell = cell;
        }
    }

    private static final class Cell {
        private final int x;
        private final int y;

        private Cell(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
}
