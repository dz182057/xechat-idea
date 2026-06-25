package cn.xeblog.server.game.gobang;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.game.gobang.GobangDTO;
import cn.xeblog.commons.enums.Game;
import cn.xeblog.server.pet.MiniGameRewards;
import cn.xeblog.server.pet.PetGameItemDeclarationService;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

@Slf4j
public final class GobangPetItemService {

    private static final int BOARD_SIZE = 15;
    private static final String SLOT_GAMEPLAY = "gameplay";
    private static final String SLOT_INTERACTION = "interaction";
    private static final String ITEM_GUARD = "item_gomoku_guard";
    private static final String ITEM_PREDICTION = "item_gomoku_prediction";
    private static final String ITEM_PROPHECY = "item_prophecy";
    private static final int PREDICTION_REWARD_BONES = 50;
    private static final int PROPHECY_REWARD_BONES = 20;
    private static final Map<String, RoomState> STATES = new ConcurrentHashMap<>();
    private static MiniGameRewards miniGameRewards = MiniGameRewards.petService();
    private static LongSupplier nowSupplier = System::currentTimeMillis;

    private GobangPetItemService() {
    }

    public static void handleMove(User user, GameRoom room, GobangDTO dto) {
        if (user == null || room == null || dto == null || !isValidCell(dto.getX(), dto.getY())) {
            return;
        }
        RoomState state = STATES.computeIfAbsent(room.getId(), id -> new RoomState(nowSupplier.getAsLong()));
        synchronized (state) {
            if (shouldSkipOpeningColorMessage(user, room, dto, state)) {
                state.started = true;
                recordOpeningTypes(user, room, dto, state);
                return;
            }
            String playerKey = user.getIdentityKey();
            settlePredictionForMove(room, playerKey, dto, state);
            if (state.board[dto.getY()][dto.getX()] != 0) {
                return;
            }
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
            state.moveHistory.add(copyMove(room, dto));
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
                snapshot.add(copyMove(room, new GobangDTO(0, 0, myType)));
            }
            state.moveHistory.forEach(move -> snapshot.add(copyMove(room, move)));
            return snapshot;
        }
    }

    public static void clearRoom(GameRoom room) {
        if (room != null) {
            clearRoom(room.getId());
        }
    }

    public static void clearRoom(String roomId) {
        if (roomId != null) {
            STATES.remove(roomId);
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
        dto.setPetItemNotice(source.getPetItemNotice());
        dto.setPetItemGuardX(source.getPetItemGuardX());
        dto.setPetItemGuardY(source.getPetItemGuardY());
        return dto;
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
        carrier.setPetInteractionItemId(null);
        if (prediction.x == dto.getX() && prediction.y == dto.getY()) {
            int reward = PetGameItemDeclarationService.settleSucceededWithInteractionReward(
                    room,
                    prediction.carrierKey,
                    ITEM_PREDICTION,
                    SLOT_INTERACTION,
                    PREDICTION_REWARD_BONES);
            appendNotice(dto, successNotice(carrier, prediction, reward));
            return;
        }
        PetGameItemDeclarationService.settleFailed(room, prediction.carrierKey, ITEM_PREDICTION, SLOT_INTERACTION);
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
        if (player == null || !ITEM_PREDICTION.equals(player.getPetInteractionItemId())) {
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
            PetGameItemDeclarationService.settleRefunded(room, playerKey, ITEM_PREDICTION, SLOT_INTERACTION);
            player.setPetInteractionItemId(null);
            return;
        }
        state.pendingByTarget.put(targetKey, new PendingPrediction(playerKey, target.x, target.y));
    }

    private static void settleProphecies(GameRoom room, String winnerPlayerKey, GobangDTO dto) {
        for (GameRoom.Player player : room.getUsers().values()) {
            String playerKey = player.getId();
            if (!ITEM_PROPHECY.equals(player.getPetInteractionItemId())) {
                continue;
            }
            if (playerKey.equals(winnerPlayerKey)) {
                int reward = PetGameItemDeclarationService.settleSucceededWithInteractionReward(
                        room, playerKey, ITEM_PROPHECY, SLOT_INTERACTION, PROPHECY_REWARD_BONES);
                if (reward > 0) {
                    appendNotice(dto, "胜负预言贴命中，" + playerName(player)
                            + " 成为唯一胜者，返还道具并获得 🦴" + reward + "。");
                } else {
                    appendNotice(dto, "胜负预言贴命中，" + playerName(player)
                            + " 成为唯一胜者，返还道具；今日互动奖励额度已用完。");
                }
            } else {
                PetGameItemDeclarationService.settleFailed(room, playerKey, ITEM_PROPHECY, SLOT_INTERACTION);
                appendNotice(dto, "胜负预言贴未命中，" + playerName(player)
                        + " 未成为唯一胜者，道具已消耗。");
            }
            player.setPetInteractionItemId(null);
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
        Cell threat = null;
        for (GameRoom.Player player : room.getUsers().values()) {
            String playerKey = player.getId();
            if (playerKey.equals(moverKey) || !ITEM_GUARD.equals(player.getPetPlayItemId())) {
                continue;
            }
            if (threat == null) {
                threat = findWinningCell(state.board, dto.getType());
            }
            if (threat == null) {
                return;
            }
            PetGameItemDeclarationService.settleConsumed(room, playerKey, ITEM_GUARD, SLOT_GAMEPLAY);
            player.setPetPlayItemId(null);
            dto.setPetItemGuardX(threat.x);
            dto.setPetItemGuardY(threat.y);
            appendNotice(dto, String.format(
                    "守门骨触发，已为 %s 高亮对手下一手五连胜点 (%d,%d)，道具已消耗。",
                    playerName(player),
                    threat.x,
                    threat.y));
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

    private static Cell findWinningCell(int[][] board, int type) {
        for (int y = 0; y < BOARD_SIZE; y++) {
            for (int x = 0; x < BOARD_SIZE; x++) {
                if (board[y][x] != 0) {
                    continue;
                }
                board[y][x] = type;
                boolean wins = isWinningMove(board, x, y, type);
                board[y][x] = 0;
                if (wins) {
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

    private static int countLine(int[][] board, int x, int y, int type, int dx, int dy) {
        return 1
                + countDirection(board, x, y, type, dx, dy)
                + countDirection(board, x, y, type, -dx, -dy);
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

    private static final class RoomState {
        private final int[][] board = new int[BOARD_SIZE][BOARD_SIZE];
        private final Map<String, PendingPrediction> pendingByTarget = new ConcurrentHashMap<>();
        private final Map<String, Integer> playerTypes = new ConcurrentHashMap<>();
        private final List<GobangDTO> moveHistory = new ArrayList<>();
        private final long startedAt;
        private boolean started;
        private boolean miniGameRewardsApplied;

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
        private final int x;
        private final int y;

        private PendingPrediction(String carrierKey, int x, int y) {
            this.carrierKey = carrierKey;
            this.x = x;
            this.y = y;
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
