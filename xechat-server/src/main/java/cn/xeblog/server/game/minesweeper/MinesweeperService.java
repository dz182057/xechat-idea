package cn.xeblog.server.game.minesweeper;

import cn.hutool.json.JSONUtil;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.GameDTO;
import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.game.minesweeper.MinesweeperCellDTO;
import cn.xeblog.commons.entity.game.minesweeper.MinesweeperDTO;
import cn.xeblog.commons.enums.Game;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.commons.game.minesweeper.NoGuessMinesweeper;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.cache.UserCache;
import cn.xeblog.server.pet.MiniGameRewards;
import cn.xeblog.server.pet.PetGameItemDeclarationService;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 扫雷服务端权威生成与操作处理。客户端不可用时仍可走本地 no-guess 兜底。
 */
@Slf4j
public final class MinesweeperService {

    private static final String ITEM_MINE_SHIELD = "item_mine_shield";
    private static final String ITEM_MINE_MARK = "item_mine_mark";
    private static final String ITEM_MINE_SAFE_PING = "item_mine_safe_ping";
    private static final String ITEM_MINE_COUNTER = "item_mine_counter";
    private static final String ITEM_MINE_DETECTOR = "item_mine_detector";
    private static final Map<String, RoomState> STATES = new ConcurrentHashMap<>();
    private static GameItemSettler gameItemSettler = MinesweeperService::settleGameItem;
    private static BoardGenerator boardGenerator = MinesweeperService::generateBoard;
    private static MiniGameRewards miniGameRewards = MiniGameRewards.petService();
    private static LongSupplier nowSupplier = System::currentTimeMillis;

    private MinesweeperService() {
    }

    public static boolean handleRoom(User user, GameRoom room, GameDTO body) {
        MinesweeperDTO dto = body instanceof MinesweeperDTO
                ? (MinesweeperDTO) body
                : JSONUtil.toBean(JSONUtil.toJsonStr(body), MinesweeperDTO.class);
        if (dto == null || dto.getEvent() == null) {
            return false;
        }
        switch (dto.getEvent()) {
            case SERVER_START_REQUEST:
                if (!room.isHomeowner(user)) {
                    user.send(ResponseBuilder.system("只有房主可以开始服务端扫雷"));
                    return true;
                }
                STATES.remove(roomKey(room.getId()));
                sendToRoom(room, ResponseBuilder.build(null, startResponse(dto, room.getId(), firstPlayerKey(room)), MessageType.GAME));
                return true;
            case SERVER_ACTION_REQUEST:
                handleRoomAction(user, room, dto);
                return true;
            case RESTART_RESPONSE:
                if (Boolean.TRUE.equals(dto.getRestartApproved())) {
                    STATES.remove(roomKey(room.getId()));
                }
                return false;
            default:
                return false;
        }
    }

    public static void handleSingle(User user, MinesweeperDTO dto) {
        if (dto == null || dto.getEvent() == null) {
            return;
        }
        if (dto.getEvent() == MinesweeperDTO.Event.SERVER_START_REQUEST) {
            STATES.remove(singleKey(user));
            user.send(ResponseBuilder.build(null, startResponse(dto, singleKey(user), null), MessageType.GAME));
            return;
        }
        if (dto.getEvent() != MinesweeperDTO.Event.SERVER_ACTION_REQUEST) {
            return;
        }
        RoomState state = STATES.compute(singleKey(user), (key, oldState) ->
                shouldRecreateState(oldState, dto) ? createState(dto, key, null) : oldState);
        OpenResult result = applyAction(state, dto, false);
        user.send(ResponseBuilder.build(null, stateEvent(state, result, true, null, null, null), MessageType.GAME));
    }

    public static void clearRoom(String roomId) {
        STATES.remove(roomKey(roomId));
    }

    static void setGameItemSettlerForTest(GameItemSettler settler) {
        gameItemSettler = settler == null ? MinesweeperService::settleGameItem : settler;
    }

    static void resetGameItemSettler() {
        gameItemSettler = MinesweeperService::settleGameItem;
    }

    static void setBoardGeneratorForTest(BoardGenerator generator) {
        boardGenerator = generator == null ? MinesweeperService::generateBoard : generator;
    }

    static void resetBoardGenerator() {
        boardGenerator = MinesweeperService::generateBoard;
    }

    static void setMiniGameRewardsForTest(MiniGameRewards testMiniGameRewards) {
        miniGameRewards = testMiniGameRewards == null ? MiniGameRewards.petService() : testMiniGameRewards;
    }

    static void resetMiniGameRewards() {
        miniGameRewards = MiniGameRewards.petService();
    }

    static void setNowSupplierForTest(LongSupplier testNowSupplier) {
        nowSupplier = testNowSupplier == null ? System::currentTimeMillis : testNowSupplier;
    }

    static void resetNowSupplier() {
        nowSupplier = System::currentTimeMillis;
    }

    private static void handleRoomAction(User user, GameRoom room, MinesweeperDTO dto) {
        String key = roomKey(room.getId());
        RoomState state = STATES.compute(key, (ignored, oldState) ->
                shouldRecreateState(oldState, dto) ? createState(dto, room.getId(), firstPlayerKey(room)) : oldState);
        String actorKey = dto.getActorKey() == null ? user.getIdentityKey() : dto.getActorKey();
        if (state.nextTurnPlayerKey != null && !state.nextTurnPlayerKey.equals(actorKey)) {
            return;
        }
        OpenResult result = applyAction(state, dto, hasUnusedMineShield(room, state, actorKey));
        if (result.shieldedMine) {
            state.usedMineShieldPlayerKeys.add(actorKey);
            state.settledMineShieldPlayerKeys.add(actorKey);
            gameItemSettler.settle(room, actorKey, ITEM_MINE_SHIELD, "gameplay", "consumed");
        }
        if (applyMineAutoMarkIfAvailable(room, state, actorKey, result, ITEM_MINE_MARK,
                state.usedMineMarkPlayerKeys, 1)) {
            state.usedMineMarkPlayerKeys.add(actorKey);
            state.settledMineMarkPlayerKeys.add(actorKey);
            gameItemSettler.settle(room, actorKey, ITEM_MINE_MARK, "gameplay", "consumed");
        }
        if (applyMineAutoMarkIfAvailable(room, state, actorKey, result, ITEM_MINE_DETECTOR,
                state.usedMineDetectorPlayerKeys, 2)) {
            state.usedMineDetectorPlayerKeys.add(actorKey);
            state.settledMineDetectorPlayerKeys.add(actorKey);
            gameItemSettler.settle(room, actorKey, ITEM_MINE_DETECTOR, "gameplay", "consumed");
        }
        settleGameplayItemsIfGameOver(room, state);
        applyMiniGameRewardsIfGameOver(room, state);
        if (result.openedCount > 0 && state.phase == MinesweeperDTO.Phase.playing) {
            state.nextTurnPlayerKey = otherPlayerKey(room, actorKey);
        }
        sendToRoom(room, ResponseBuilder.build(user, stateEvent(state, result, false, actorKey, dto.getX(), dto.getY()), MessageType.GAME));
    }

    private static void settleGameItem(GameRoom room, String playerKey, String itemId, String slot, String status) {
        if ("consumed".equals(status)) {
            PetGameItemDeclarationService.settleConsumed(room, playerKey, itemId, slot);
        } else if ("refunded".equals(status)) {
            PetGameItemDeclarationService.settleRefunded(room, playerKey, itemId, slot);
        }
    }

    private static NoGuessMinesweeper.Board generateBoard(int rows, int cols, int mines,
                                                          NoGuessMinesweeper.Point firstClick) {
        return NoGuessMinesweeper.generate(rows, cols, mines, firstClick, new Random());
    }

    private static MinesweeperDTO startResponse(MinesweeperDTO request, String roomId, String nextTurnPlayerKey) {
        MinesweeperDTO dto = new MinesweeperDTO(roomId);
        dto.setEvent(MinesweeperDTO.Event.SERVER_START_RESPONSE);
        dto.setRows(normalizeRows(request.getRows()));
        dto.setCols(normalizeCols(request.getCols()));
        dto.setMines(normalizeMines(dto.getRows(), dto.getCols(), request.getMines()));
        dto.setPhase(MinesweeperDTO.Phase.playing);
        dto.setNextTurnPlayerKey(nextTurnPlayerKey);
        dto.setCells(new ArrayList<>());
        return dto;
    }

    private static RoomState createState(MinesweeperDTO dto, String roomId, String nextTurnPlayerKey) {
        int rows = normalizeRows(dto.getRows());
        int cols = normalizeCols(dto.getCols());
        int mines = normalizeMines(rows, cols, dto.getMines());
        int x = dto.getX() == null ? cols / 2 : clamp(dto.getX(), 0, cols - 1);
        int y = dto.getY() == null ? rows / 2 : clamp(dto.getY(), 0, rows - 1);
        NoGuessMinesweeper.Board board = boardGenerator.generate(
                rows, cols, mines, new NoGuessMinesweeper.Point(x, y));
        RoomState state = new RoomState();
        state.roomId = roomId;
        state.rows = rows;
        state.cols = cols;
        state.mines = mines;
        state.board = board;
        state.opened = new boolean[rows][cols];
        state.sharedMarked = new boolean[rows][cols];
        state.exploded = new boolean[rows][cols];
        state.phase = MinesweeperDTO.Phase.playing;
        state.nextTurnPlayerKey = nextTurnPlayerKey;
        state.startedAt = nowSupplier.getAsLong();
        return state;
    }

    private static boolean shouldRecreateState(RoomState state, MinesweeperDTO dto) {
        if (state == null || state.phase != MinesweeperDTO.Phase.playing) {
            return true;
        }
        int rows = normalizeRows(dto.getRows());
        int cols = normalizeCols(dto.getCols());
        int mines = normalizeMines(rows, cols, dto.getMines());
        return state.rows != rows || state.cols != cols || state.mines != mines;
    }

    private static OpenResult applyAction(RoomState state, MinesweeperDTO dto, boolean mineShieldAvailable) {
        OpenResult result = new OpenResult();
        result.phase = state.phase;
        if (state.phase != MinesweeperDTO.Phase.playing || dto.getX() == null || dto.getY() == null) {
            return result;
        }
        int x = dto.getX();
        int y = dto.getY();
        if (!inBounds(state, x, y)) {
            return result;
        }
        if (dto.getAction() == MinesweeperDTO.ActionType.OPEN_AROUND) {
            openAround(state, x, y, result, mineShieldAvailable);
        } else {
            result.openedCount = openCell(state, x, y, result, mineShieldAvailable);
        }
        if (result.hitMine) {
            state.phase = MinesweeperDTO.Phase.lost;
            result.phase = state.phase;
            return result;
        }
        if (isWon(state)) {
            state.phase = MinesweeperDTO.Phase.won;
            result.phase = state.phase;
            result.won = true;
        }
        return result;
    }

    private static int openCell(RoomState state, int x, int y, OpenResult result, boolean mineShieldAvailable) {
        if (state.opened[y][x] || state.sharedMarked[y][x]) {
            return 0;
        }
        NoGuessMinesweeper.Cell cell = state.board.cell(x, y);
        if (cell.isMine()) {
            if (mineShieldAvailable) {
                state.sharedMarked[y][x] = true;
                result.shieldedMine = true;
                return 1;
            }
            state.opened[y][x] = true;
            state.exploded[y][x] = true;
            result.hitMine = true;
            return 1;
        }
        return openSafeArea(state, x, y);
    }

    private static void openAround(RoomState state, int x, int y, OpenResult result, boolean mineShieldAvailable) {
        if (!state.opened[y][x]) {
            return;
        }
        NoGuessMinesweeper.Cell source = state.board.cell(x, y);
        if (source.getAdjacent() <= 0 || countAdjacentMarks(state, x, y) != source.getAdjacent()) {
            return;
        }
        for (NoGuessMinesweeper.Cell cell : state.board.neighbors(x, y)) {
            OpenResult cellResult = new OpenResult();
            result.openedCount += openCell(state, cell.getX(), cell.getY(), cellResult, mineShieldAvailable);
            if (cellResult.shieldedMine) {
                result.shieldedMine = true;
                return;
            }
            if (cellResult.hitMine) {
                result.hitMine = true;
                state.phase = MinesweeperDTO.Phase.lost;
                return;
            }
        }
    }

    private static int openSafeArea(RoomState state, int x, int y) {
        int count = 0;
        Queue<NoGuessMinesweeper.Point> queue = new ArrayDeque<>();
        queue.add(new NoGuessMinesweeper.Point(x, y));
        while (!queue.isEmpty()) {
            NoGuessMinesweeper.Point point = queue.remove();
            if (!inBounds(state, point.getX(), point.getY()) || state.opened[point.getY()][point.getX()]) {
                continue;
            }
            if (state.sharedMarked[point.getY()][point.getX()]) {
                continue;
            }
            NoGuessMinesweeper.Cell cell = state.board.cell(point.getX(), point.getY());
            if (cell.isMine()) {
                continue;
            }
            state.opened[point.getY()][point.getX()] = true;
            count++;
            if (cell.getAdjacent() == 0) {
                for (NoGuessMinesweeper.Cell neighbor : state.board.neighbors(point.getX(), point.getY())) {
                    if (!neighbor.isMine()) {
                        queue.add(new NoGuessMinesweeper.Point(neighbor.getX(), neighbor.getY()));
                    }
                }
            }
        }
        return count;
    }

    private static MinesweeperDTO stateEvent(RoomState state, OpenResult result, boolean single,
                                             String actorKey, Integer x, Integer y) {
        MinesweeperDTO dto = new MinesweeperDTO(state.roomId);
        dto.setEvent(state.phase == MinesweeperDTO.Phase.playing
                ? MinesweeperDTO.Event.STATE_PATCH
                : MinesweeperDTO.Event.GAME_RESULT);
        dto.setActorKey(actorKey);
        dto.setX(x);
        dto.setY(y);
        dto.setRows(state.rows);
        dto.setCols(state.cols);
        dto.setMines(state.mines);
        dto.setPhase(state.phase);
        dto.setOpenedCount(result.openedCount);
        dto.setHitMine(result.hitMine);
        dto.setWon(result.won);
        dto.setNextTurnPlayerKey(single ? null : state.nextTurnPlayerKey);
        dto.setCells(toCells(state, state.phase != MinesweeperDTO.Phase.playing));
        return dto;
    }

    private static List<MinesweeperCellDTO> toCells(RoomState state, boolean revealMines) {
        List<MinesweeperCellDTO> result = new ArrayList<>();
        for (int y = 0; y < state.rows; y++) {
            for (int x = 0; x < state.cols; x++) {
                NoGuessMinesweeper.Cell source = state.board.cell(x, y);
                MinesweeperCellDTO cell = new MinesweeperCellDTO();
                cell.setX(x);
                cell.setY(y);
                cell.setOpened(state.opened[y][x] || revealMines && source.isMine());
                if (cell.isOpened() || revealMines) {
                    cell.setAdjacentMines(source.getAdjacent());
                }
                cell.setSharedMarked(state.sharedMarked[y][x]);
                cell.setExploded(state.exploded[y][x]);
                if (revealMines && source.isMine()) {
                    cell.setHasMine(true);
                }
                result.add(cell);
            }
        }
        return result;
    }

    private static boolean isWon(RoomState state) {
        for (int y = 0; y < state.rows; y++) {
            for (int x = 0; x < state.cols; x++) {
                if (!state.board.cell(x, y).isMine() && !state.opened[y][x]) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int countAdjacentMarks(RoomState state, int x, int y) {
        int count = 0;
        for (NoGuessMinesweeper.Cell cell : state.board.neighbors(x, y)) {
            if (state.sharedMarked[cell.getY()][cell.getX()]) {
                count++;
            }
        }
        return count;
    }

    private static void sendToRoom(GameRoom room, cn.xeblog.commons.entity.Response response) {
        room.getUsers().forEach((k, v) -> {
            User player = UserCache.get(v.getChannelId());
            if (player != null) {
                player.send(response);
            }
        });
    }

    private static String firstPlayerKey(GameRoom room) {
        return room.getHomeowner() == null ? null : room.getHomeowner().getIdentityKey();
    }

    private static String otherPlayerKey(GameRoom room, String actorKey) {
        for (GameRoom.Player player : room.getUsers().values()) {
            if (!player.getId().equals(actorKey)) {
                return player.getId();
            }
        }
        return actorKey;
    }

    private static boolean hasUnusedMineShield(GameRoom room, RoomState state, String actorKey) {
        if (room == null || state == null || actorKey == null || state.usedMineShieldPlayerKeys.contains(actorKey)) {
            return false;
        }
        GameRoom.Player player = room.getUsers().get(actorKey);
        return player != null && ITEM_MINE_SHIELD.equals(player.getPetPlayItemId());
    }

    private static boolean applyMineAutoMarkIfAvailable(GameRoom room, RoomState state, String actorKey,
                                                        OpenResult result, String itemId,
                                                        Set<String> usedPlayerKeys, int maxMarks) {
        if (!hasUnusedGameplayItem(room, actorKey, itemId, usedPlayerKeys) || result.openedCount <= 0
                || state.phase != MinesweeperDTO.Phase.playing) {
            return false;
        }
        return markUnmarkedMines(state, maxMarks) > 0;
    }

    private static boolean hasUnusedGameplayItem(GameRoom room, String actorKey, String itemId,
                                                 Set<String> usedPlayerKeys) {
        if (room == null || actorKey == null || usedPlayerKeys.contains(actorKey)) {
            return false;
        }
        GameRoom.Player player = room.getUsers().get(actorKey);
        return player != null && itemId.equals(player.getPetPlayItemId());
    }

    private static int markUnmarkedMines(RoomState state, int maxMarks) {
        int marked = 0;
        for (int y = 0; y < state.rows; y++) {
            for (int x = 0; x < state.cols; x++) {
                if (state.board.cell(x, y).isMine() && !state.opened[y][x] && !state.sharedMarked[y][x]) {
                    state.sharedMarked[y][x] = true;
                    marked++;
                    if (marked >= maxMarks) {
                        return marked;
                    }
                }
            }
        }
        return marked;
    }

    private static void settleGameplayItemsIfGameOver(GameRoom room, RoomState state) {
        if (room == null || state == null || state.phase == MinesweeperDTO.Phase.playing) {
            return;
        }
        settleGameplayItemsIfGameOver(room, state, ITEM_MINE_MARK, state.settledMineMarkPlayerKeys);
        settleGameplayItemsIfGameOver(room, state, ITEM_MINE_SAFE_PING, state.settledMineSafePingPlayerKeys);
        settleGameplayItemsIfGameOver(room, state, ITEM_MINE_COUNTER, state.settledMineCounterPlayerKeys);
        settleGameplayItemsIfGameOver(room, state, ITEM_MINE_DETECTOR, state.settledMineDetectorPlayerKeys);
        settleGameplayItemsIfGameOver(room, state, ITEM_MINE_SHIELD, state.settledMineShieldPlayerKeys);
    }

    private static void settleGameplayItemsIfGameOver(GameRoom room, RoomState state, String itemId,
                                                      Set<String> settledPlayerKeys) {
        for (GameRoom.Player player : room.getUsers().values()) {
            String playerKey = player.getId();
            if (!itemId.equals(player.getPetPlayItemId()) || settledPlayerKeys.contains(playerKey)) {
                continue;
            }
            settledPlayerKeys.add(playerKey);
            gameItemSettler.settle(room, playerKey, itemId, "gameplay", "refunded");
        }
    }

    private static void applyMiniGameRewardsIfGameOver(GameRoom room, RoomState state) {
        if (room == null || state == null || state.phase == MinesweeperDTO.Phase.playing
                || state.miniGameRewardsApplied) {
            return;
        }
        state.miniGameRewardsApplied = true;
        boolean won = state.phase == MinesweeperDTO.Phase.won;
        long durationSeconds = Math.max(0L, (nowSupplier.getAsLong() - state.startedAt + 999L) / 1000L);
        List<Long> accountIds = new ArrayList<>();
        for (GameRoom.Player player : room.getUsers().values()) {
            if (player.getAccountId() <= 0) {
                continue;
            }
            accountIds.add(player.getAccountId());
            try {
                miniGameRewards.apply(player.getAccountId(), Game.MINESWEEPER, won, durationSeconds);
            } catch (RuntimeException e) {
                log.warn("扫雷小游戏产出结算失败 -> accountId: {}", player.getAccountId(), e);
            }
        }
        try {
            miniGameRewards.applyRoomBonus(Game.MINESWEEPER, accountIds, durationSeconds);
        } catch (RuntimeException e) {
            log.warn("扫雷房间级彩蛋奖励结算失败 -> accountIds: {}", accountIds, e);
        }
    }

    private static String singleKey(User user) {
        return "single:" + user.getIdentityKey();
    }

    private static String roomKey(String roomId) {
        return "room:" + roomId;
    }

    private static boolean inBounds(RoomState state, int x, int y) {
        return x >= 0 && x < state.cols && y >= 0 && y < state.rows;
    }

    private static int normalizeRows(Integer rows) {
        return clamp(rows == null ? 9 : rows, 5, 24);
    }

    private static int normalizeCols(Integer cols) {
        return clamp(cols == null ? 9 : cols, 5, 40);
    }

    private static int normalizeMines(int rows, int cols, Integer mines) {
        return clamp(mines == null ? 10 : mines, 1, rows * cols - 1);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static class RoomState {
        private String roomId;
        private int rows;
        private int cols;
        private int mines;
        private NoGuessMinesweeper.Board board;
        private boolean[][] opened;
        private boolean[][] sharedMarked;
        private boolean[][] exploded;
        private final Set<String> usedMineShieldPlayerKeys = new HashSet<>();
        private final Set<String> settledMineShieldPlayerKeys = new HashSet<>();
        private final Set<String> usedMineMarkPlayerKeys = new HashSet<>();
        private final Set<String> settledMineMarkPlayerKeys = new HashSet<>();
        private final Set<String> settledMineSafePingPlayerKeys = new HashSet<>();
        private final Set<String> settledMineCounterPlayerKeys = new HashSet<>();
        private final Set<String> usedMineDetectorPlayerKeys = new HashSet<>();
        private final Set<String> settledMineDetectorPlayerKeys = new HashSet<>();
        private String nextTurnPlayerKey;
        private MinesweeperDTO.Phase phase;
        private long startedAt;
        private boolean miniGameRewardsApplied;
    }

    private static class OpenResult {
        private int openedCount;
        private boolean hitMine;
        private boolean shieldedMine;
        private boolean won;
        private MinesweeperDTO.Phase phase = MinesweeperDTO.Phase.playing;
    }

    interface GameItemSettler {
        void settle(GameRoom room, String playerKey, String itemId, String slot, String status);
    }

    interface BoardGenerator {
        NoGuessMinesweeper.Board generate(int rows, int cols, int mines, NoGuessMinesweeper.Point firstClick);
    }

}
