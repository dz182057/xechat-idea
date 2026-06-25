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
            case ITEM_USE_REQUEST:
                handleRoomItemUse(user, room, dto);
                return true;
            case SHARED_MARK:
                handleSharedMark(user, room, dto);
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
        if (dto.getEvent() == MinesweeperDTO.Event.ITEM_USE_REQUEST) {
            handleSingleItemUse(user, dto);
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
        ItemUse mineShield = findUsableItem(room, state, actorKey, ITEM_MINE_SHIELD,
                state.usedMineShieldPlayerKeys, state.settledMineShieldPlayerKeys);
        OpenResult result = applyAction(state, dto, mineShield != null);
        if (result.shieldedMine) {
            state.assisted = true;
            result.petItemId = ITEM_MINE_SHIELD;
            result.petItemName = mineItemName(ITEM_MINE_SHIELD);
            result.petItemDescription = mineItemDescription(ITEM_MINE_SHIELD);
            result.petItemTriggerLabel = "被动触发";
            result.petItemNotice = "排雷护盾挡下了本次踩雷，并把该雷格标记为共享旗。";
            if (mineShield != null) {
                markUsedAndSettled(mineShield, state.usedMineShieldPlayerKeys, state.settledMineShieldPlayerKeys);
                gameItemSettler.settle(room, actorKey, ITEM_MINE_SHIELD, mineShield.slot, "consumed");
            }
        }
        settleGameplayItemsIfGameOver(room, state);
        applyMiniGameRewardsIfGameOver(room, state);
        if (result.openedCount > 0 && state.phase == MinesweeperDTO.Phase.playing) {
            state.nextTurnPlayerKey = otherPlayerKey(room, actorKey);
        }
        sendToRoom(room, ResponseBuilder.build(user, stateEvent(state, result, false, actorKey, dto.getX(), dto.getY()), MessageType.GAME));
    }

    private static void handleSharedMark(User user, GameRoom room, MinesweeperDTO dto) {
        RoomState state = STATES.get(roomKey(room.getId()));
        if (state == null || state.phase != MinesweeperDTO.Phase.playing || dto.getCells() == null) {
            return;
        }
        for (MinesweeperCellDTO cell : dto.getCells()) {
            if (cell == null || !inBounds(state, cell.getX(), cell.getY())) {
                continue;
            }
            if (!state.opened[cell.getY()][cell.getX()]) {
                state.sharedMarked[cell.getY()][cell.getX()] = Boolean.TRUE.equals(cell.getSharedMarked());
            }
        }
        String actorKey = dto.getActorKey() == null ? user.getIdentityKey() : dto.getActorKey();
        MinesweeperDTO response = stateEvent(state, new OpenResult(), false, actorKey, dto.getX(), dto.getY());
        response.setEvent(MinesweeperDTO.Event.SHARED_MARK);
        sendToRoom(room, ResponseBuilder.build(user, response, MessageType.GAME));
    }

    private static void handleRoomItemUse(User user, GameRoom room, MinesweeperDTO dto) {
        String actorKey = dto.getActorKey() == null ? user.getIdentityKey() : dto.getActorKey();
        String itemId = trimToNull(dto.getPetItemId());
        RoomState state = STATES.get(roomKey(room.getId()));
        if (state == null || state.phase != MinesweeperDTO.Phase.playing) {
            sendToRoom(room, ResponseBuilder.build(user, itemEffectEvent(null, dto, false, actorKey,
                    failedEffect(itemId, "先翻开一格后再使用扫雷道具")), MessageType.GAME));
            return;
        }
        ItemUse itemUse = findUsableActiveItem(room, state, actorKey, itemId);
        if (itemUse == null) {
            sendToRoom(room, ResponseBuilder.build(user, itemEffectEvent(state, dto, false, actorKey,
                    failedEffect(itemId, "该道具未携带或本局已经使用过")), MessageType.GAME));
            return;
        }
        ItemEffect effect = applyActiveItem(state, dto, itemUse.itemId);
        if (effect.consumed) {
            state.assisted = true;
            markUsedAndSettled(itemUse, usedSetForItem(state, itemUse.itemId), settledSetForItem(state, itemUse.itemId));
            gameItemSettler.settle(room, actorKey, itemUse.itemId, itemUse.slot, "consumed");
        }
        sendToRoom(room, ResponseBuilder.build(user, itemEffectEvent(state, dto, false, actorKey, effect), MessageType.GAME));
    }

    private static void handleSingleItemUse(User user, MinesweeperDTO dto) {
        String actorKey = dto.getActorKey() == null ? user.getIdentityKey() : dto.getActorKey();
        String itemId = trimToNull(dto.getPetItemId());
        RoomState state = STATES.get(singleKey(user));
        if (state == null || state.phase != MinesweeperDTO.Phase.playing) {
            user.send(ResponseBuilder.build(null, itemEffectEvent(null, dto, true, actorKey,
                    failedEffect(itemId, "先翻开一格后再使用扫雷道具")), MessageType.GAME));
            return;
        }
        if (!isActiveMineItem(itemId)) {
            user.send(ResponseBuilder.build(null, itemEffectEvent(state, dto, true, actorKey,
                    failedEffect(itemId, "该道具无需主动使用")), MessageType.GAME));
            return;
        }
        Set<String> usedKeys = usedSetForItem(state, itemId);
        String singleUseKey = usageKey(actorKey, "single", itemId);
        if (usedKeys.contains(singleUseKey)) {
            user.send(ResponseBuilder.build(null, itemEffectEvent(state, dto, true, actorKey,
                    failedEffect(itemId, "该道具本局已经使用过")), MessageType.GAME));
            return;
        }
        ItemEffect effect = applyActiveItem(state, dto, itemId);
        if (effect.consumed) {
            state.assisted = true;
            usedKeys.add(singleUseKey);
        }
        user.send(ResponseBuilder.build(null, itemEffectEvent(state, dto, true, actorKey, effect), MessageType.GAME));
    }

    private static ItemEffect applyActiveItem(RoomState state, MinesweeperDTO dto, String itemId) {
        ItemEffect effect = new ItemEffect();
        effect.itemId = itemId;
        if (ITEM_MINE_MARK.equals(itemId)) {
            List<NoGuessMinesweeper.Point> marked = markUnmarkedMines(state, 1);
            effect.consumed = !marked.isEmpty();
            effect.notice = effect.consumed
                    ? "探雷骨头标记了 1 个未标记的真实雷格。"
                    : "当前没有可标记的雷格";
            if (!marked.isEmpty()) {
                effect.target = marked.get(0);
            }
            return effect;
        }
        if (ITEM_MINE_DETECTOR.equals(itemId)) {
            List<NoGuessMinesweeper.Point> marked = markUnmarkedMines(state, 2);
            effect.consumed = !marked.isEmpty();
            effect.notice = effect.consumed
                    ? "探雷器标记了 " + marked.size() + " 个未标记的真实雷格。"
                    : "当前没有可标记的雷格";
            if (!marked.isEmpty()) {
                effect.target = marked.get(0);
            }
            return effect;
        }
        if (ITEM_MINE_SAFE_PING.equals(itemId)) {
            NoGuessMinesweeper.Point target = findSafePingTarget(state);
            effect.consumed = target != null;
            effect.target = target;
            effect.expiresAt = nowSupplier.getAsLong() + 3000L;
            effect.notice = target == null
                    ? "当前没有可提示的安全格"
                    : "安全提示指出了 1 个可安全尝试的未打开格子。";
            return effect;
        }
        if (ITEM_MINE_COUNTER.equals(itemId)) {
            if (dto.getX() == null || dto.getY() == null || !isCounterCenter(state, dto.getX(), dto.getY())) {
                effect.notice = "请选择完整 3x3 区域的中心格";
                return effect;
            }
            effect.consumed = true;
            effect.target = new NoGuessMinesweeper.Point(dto.getX(), dto.getY());
            effect.counterMines = countMinesInCounterArea(state, dto.getX(), dto.getY());
            effect.notice = "九宫格计数显示目标 3x3 区域共有 " + effect.counterMines + " 颗雷。";
            return effect;
        }
        effect.notice = "该道具无需主动使用";
        return effect;
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
        dto.setAssisted(state.assisted);
        if (result.petItemId != null) {
            dto.setPetItemId(result.petItemId);
            dto.setPetItemName(result.petItemName);
            dto.setPetItemDescription(result.petItemDescription);
            dto.setPetItemTriggerLabel(result.petItemTriggerLabel);
            dto.setPetItemNotice(result.petItemNotice);
        }
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

    private static MinesweeperDTO itemEffectEvent(RoomState state, MinesweeperDTO request, boolean single,
                                                  String actorKey, ItemEffect effect) {
        MinesweeperDTO dto = state == null
                ? new MinesweeperDTO(request.getRoomId())
                : stateEvent(state, new OpenResult(), single, actorKey, request.getX(), request.getY());
        dto.setEvent(MinesweeperDTO.Event.ITEM_EFFECT);
        dto.setActorKey(actorKey);
        dto.setActorName(request.getActorName());
        dto.setPetItemId(effect.itemId != null ? effect.itemId : request.getPetItemId());
        dto.setPetItemName(firstNonBlank(request.getPetItemName(), mineItemName(dto.getPetItemId())));
        dto.setPetItemDescription(firstNonBlank(request.getPetItemDescription(), mineItemDescription(dto.getPetItemId())));
        dto.setPetItemIconSrc(request.getPetItemIconSrc());
        dto.setPetItemTriggerLabel(firstNonBlank(request.getPetItemTriggerLabel(), "主动使用"));
        dto.setPetItemNotice(effect.notice);
        dto.setAssisted(state != null && state.assisted);
        if (effect.target != null) {
            dto.setPetItemTargetX(effect.target.getX());
            dto.setPetItemTargetY(effect.target.getY());
        }
        dto.setPetItemCounterMines(effect.counterMines);
        dto.setPetItemExpiresAt(effect.expiresAt);
        return dto;
    }

    private static ItemEffect failedEffect(String itemId, String notice) {
        ItemEffect effect = new ItemEffect();
        effect.itemId = itemId;
        effect.notice = notice;
        return effect;
    }

    private static ItemUse findUsableActiveItem(GameRoom room, RoomState state, String actorKey, String itemId) {
        if (!isActiveMineItem(itemId)) {
            return null;
        }
        return findUsableItem(room, state, actorKey, itemId, usedSetForItem(state, itemId), settledSetForItem(state, itemId));
    }

    private static ItemUse findUsableItem(GameRoom room, RoomState state, String actorKey, String itemId,
                                          Set<String> usedKeys, Set<String> settledKeys) {
        if (room == null || state == null || actorKey == null || itemId == null) {
            return null;
        }
        GameRoom.Player player = room.getUsers().get(actorKey);
        if (player == null) {
            return null;
        }
        ItemUse gameplay = carriedItemUse(actorKey, player.getPetPlayItemId(), itemId, "gameplay", usedKeys, settledKeys);
        if (gameplay != null) {
            return gameplay;
        }
        return carriedItemUse(actorKey, player.getPetInteractionItemId(), itemId, "interaction", usedKeys, settledKeys);
    }

    private static ItemUse carriedItemUse(String playerKey, String carriedItemId, String itemId, String slot,
                                          Set<String> usedKeys, Set<String> settledKeys) {
        if (!itemId.equals(carriedItemId)) {
            return null;
        }
        String key = usageKey(playerKey, slot, itemId);
        return usedKeys.contains(key) || settledKeys.contains(key) ? null : new ItemUse(playerKey, itemId, slot);
    }

    private static void markUsedAndSettled(ItemUse itemUse, Set<String> usedKeys, Set<String> settledKeys) {
        String key = usageKey(itemUse.playerKey, itemUse.slot, itemUse.itemId);
        usedKeys.add(key);
        settledKeys.add(key);
    }

    private static String usageKey(String playerKey, String slot, String itemId) {
        return playerKey + "|" + slot + "|" + itemId;
    }

    private static Set<String> usedSetForItem(RoomState state, String itemId) {
        if (ITEM_MINE_MARK.equals(itemId)) {
            return state.usedMineMarkPlayerKeys;
        }
        if (ITEM_MINE_SAFE_PING.equals(itemId)) {
            return state.usedMineSafePingPlayerKeys;
        }
        if (ITEM_MINE_COUNTER.equals(itemId)) {
            return state.usedMineCounterPlayerKeys;
        }
        if (ITEM_MINE_DETECTOR.equals(itemId)) {
            return state.usedMineDetectorPlayerKeys;
        }
        return state.usedMineShieldPlayerKeys;
    }

    private static Set<String> settledSetForItem(RoomState state, String itemId) {
        if (ITEM_MINE_MARK.equals(itemId)) {
            return state.settledMineMarkPlayerKeys;
        }
        if (ITEM_MINE_SAFE_PING.equals(itemId)) {
            return state.settledMineSafePingPlayerKeys;
        }
        if (ITEM_MINE_COUNTER.equals(itemId)) {
            return state.settledMineCounterPlayerKeys;
        }
        if (ITEM_MINE_DETECTOR.equals(itemId)) {
            return state.settledMineDetectorPlayerKeys;
        }
        return state.settledMineShieldPlayerKeys;
    }

    private static List<NoGuessMinesweeper.Point> markUnmarkedMines(RoomState state, int maxMarks) {
        List<NoGuessMinesweeper.Point> marked = new ArrayList<>();
        for (int y = 0; y < state.rows; y++) {
            for (int x = 0; x < state.cols; x++) {
                if (state.board.cell(x, y).isMine() && !state.opened[y][x] && !state.sharedMarked[y][x]) {
                    state.sharedMarked[y][x] = true;
                    marked.add(new NoGuessMinesweeper.Point(x, y));
                    if (marked.size() >= maxMarks) {
                        return marked;
                    }
                }
            }
        }
        return marked;
    }

    private static NoGuessMinesweeper.Point findSafePingTarget(RoomState state) {
        List<NoGuessMinesweeper.Point> candidates = new ArrayList<>();
        for (int y = 0; y < state.rows; y++) {
            for (int x = 0; x < state.cols; x++) {
                NoGuessMinesweeper.Cell cell = state.board.cell(x, y);
                if (!cell.isMine() && !state.opened[y][x] && !state.sharedMarked[y][x]) {
                    candidates.add(new NoGuessMinesweeper.Point(x, y));
                }
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(new Random().nextInt(candidates.size()));
    }

    private static boolean isCounterCenter(RoomState state, int x, int y) {
        return x > 0 && y > 0 && x < state.cols - 1 && y < state.rows - 1;
    }

    private static int countMinesInCounterArea(RoomState state, int x, int y) {
        int count = 0;
        for (int row = y - 1; row <= y + 1; row++) {
            for (int col = x - 1; col <= x + 1; col++) {
                if (state.board.cell(col, row).isMine()) {
                    count++;
                }
            }
        }
        return count;
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
            settleGameplaySlotIfGameOver(room, player, itemId, "gameplay", player.getPetPlayItemId(), settledPlayerKeys);
            settleGameplaySlotIfGameOver(room, player, itemId, "interaction", player.getPetInteractionItemId(), settledPlayerKeys);
        }
    }

    private static void settleGameplaySlotIfGameOver(GameRoom room, GameRoom.Player player, String itemId,
                                                     String slot, String carriedItemId, Set<String> settledKeys) {
        String playerKey = player.getId();
        String key = usageKey(playerKey, slot, itemId);
        if (!itemId.equals(carriedItemId) || settledKeys.contains(key)) {
            return;
        }
        settledKeys.add(key);
        gameItemSettler.settle(room, playerKey, itemId, slot, "refunded");
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

    private static boolean isActiveMineItem(String itemId) {
        return ITEM_MINE_MARK.equals(itemId)
                || ITEM_MINE_SAFE_PING.equals(itemId)
                || ITEM_MINE_COUNTER.equals(itemId)
                || ITEM_MINE_DETECTOR.equals(itemId);
    }

    private static String mineItemName(String itemId) {
        if (ITEM_MINE_MARK.equals(itemId)) {
            return "探雷骨头";
        }
        if (ITEM_MINE_SAFE_PING.equals(itemId)) {
            return "安全提示";
        }
        if (ITEM_MINE_SHIELD.equals(itemId)) {
            return "排雷护盾";
        }
        if (ITEM_MINE_DETECTOR.equals(itemId)) {
            return "探雷器";
        }
        if (ITEM_MINE_COUNTER.equals(itemId)) {
            return "九宫格计数";
        }
        return "扫雷道具";
    }

    private static String mineItemDescription(String itemId) {
        if (ITEM_MINE_MARK.equals(itemId)) {
            return "随机选择 1 个仍未标记的真实雷格，并正确插上共享旗。";
        }
        if (ITEM_MINE_SAFE_PING.equals(itemId)) {
            return "随机指出 1 个安全的未打开格子，提示会短暂高亮。";
        }
        if (ITEM_MINE_SHIELD.equals(itemId)) {
            return "本局首次踩雷时自动挡下，并把该雷格标为共享旗。";
        }
        if (ITEM_MINE_DETECTOR.equals(itemId)) {
            return "随机标记最多 2 个仍未标记的真实雷格。";
        }
        if (ITEM_MINE_COUNTER.equals(itemId)) {
            return "选择一个完整 3x3 区域的中心格，显示该区域雷数。";
        }
        return "扫雷小游戏道具。";
    }

    private static String firstNonBlank(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
        private final Set<String> usedMineSafePingPlayerKeys = new HashSet<>();
        private final Set<String> settledMineSafePingPlayerKeys = new HashSet<>();
        private final Set<String> usedMineCounterPlayerKeys = new HashSet<>();
        private final Set<String> settledMineCounterPlayerKeys = new HashSet<>();
        private final Set<String> usedMineDetectorPlayerKeys = new HashSet<>();
        private final Set<String> settledMineDetectorPlayerKeys = new HashSet<>();
        private String nextTurnPlayerKey;
        private MinesweeperDTO.Phase phase;
        private long startedAt;
        private boolean miniGameRewardsApplied;
        private boolean assisted;
    }

    private static class OpenResult {
        private int openedCount;
        private boolean hitMine;
        private boolean shieldedMine;
        private boolean won;
        private MinesweeperDTO.Phase phase = MinesweeperDTO.Phase.playing;
        private String petItemId;
        private String petItemName;
        private String petItemDescription;
        private String petItemTriggerLabel;
        private String petItemNotice;
    }

    private static class ItemUse {
        private final String playerKey;
        private final String itemId;
        private final String slot;

        private ItemUse(String playerKey, String itemId, String slot) {
            this.playerKey = playerKey;
            this.itemId = itemId;
            this.slot = slot;
        }
    }

    private static class ItemEffect {
        private String itemId;
        private boolean consumed;
        private String notice;
        private NoGuessMinesweeper.Point target;
        private Integer counterMines;
        private Long expiresAt;
    }

    interface GameItemSettler {
        void settle(GameRoom room, String playerKey, String itemId, String slot, String status);
    }

    interface BoardGenerator {
        NoGuessMinesweeper.Board generate(int rows, int cols, int mines, NoGuessMinesweeper.Point firstClick);
    }

}
