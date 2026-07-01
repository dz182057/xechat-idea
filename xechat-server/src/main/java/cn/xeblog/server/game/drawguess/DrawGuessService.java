package cn.xeblog.server.game.drawguess;

import cn.hutool.json.JSONUtil;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.GameDTO;
import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.game.drawguess.DrawGuessDTO;
import cn.xeblog.commons.enums.Game;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.cache.GameRoomCache;
import cn.xeblog.server.cache.UserCache;
import cn.xeblog.server.pet.PetGameItemDeclarationService;
import cn.xeblog.server.pet.PetGameItemRules;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * 你画我猜服务端权威状态机。
 */
@Slf4j
public final class DrawGuessService {

    private static final int MIN_PLAYERS = 2;
    private static final int MAX_PLAYERS = 8;
    private static final int MAX_ROUNDS = 10;
    private static final int DEFAULT_ROUNDS = 1;
    private static final int DEFAULT_TIME_LIMIT_SECONDS = 90;
    private static final int BASE_SCORE = 3;
    private static final String SLOT_GAMEPLAY = "gameplay";
    private static final String SLOT_INTERACTION = "interaction";
    private static final Map<String, RoomState> ROOM_STATES = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService TIMEOUT_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "draw-guess-timeout");
        thread.setDaemon(true);
        return thread;
    });
    private static LongSupplier nowSupplier = System::currentTimeMillis;

    private DrawGuessService() {
    }

    public static void handle(User user, GameRoom room, GameDTO body) {
        DrawGuessDTO request = toDrawGuessDTO(body);
        if (request == null || request.getEvent() == null) {
            return;
        }
        try {
            List<DrawGuessDTO> events = applyRequest(room, user.getIdentityKey(), displayName(user), request, now(), true);
            for (DrawGuessDTO event : events) {
                broadcast(room, user, event);
            }
        } catch (IllegalArgumentException e) {
            user.send(ResponseBuilder.system(e.getMessage()));
        }
    }

    public static List<DrawGuessDTO> applyRequestForTest(
            GameRoom room,
            String playerKey,
            String playerName,
            DrawGuessDTO request,
            long nowMs) {
        return applyRequest(room, playerKey, playerName, request, nowMs, false);
    }

    public static DrawGuessDTO finishRoundForTest(GameRoom room, String reason, long nowMs) {
        RoomState state = stateOf(room);
        synchronized (state) {
            return finishRound(room, state, normalizeRoundEndReason(reason), nowMs);
        }
    }

    public static DrawGuessDTO visibleStartForTest(DrawGuessDTO dto, boolean drawer) {
        return visibleStart(dto, drawer);
    }

    public static void clearRoom(String roomId) {
        RoomState state = ROOM_STATES.remove(roomId);
        if (state != null) {
            synchronized (state) {
                state.closed = true;
                cancelTimeout(state);
            }
        }
        DrawGuessRewardService.clearRoom(roomId);
    }

    public static void clearRoom(GameRoom room) {
        if (room != null) {
            clearRoom(room.getId());
        }
    }

    static void setNowSupplierForTest(LongSupplier testNowSupplier) {
        nowSupplier = testNowSupplier == null ? System::currentTimeMillis : testNowSupplier;
    }

    static void resetNowSupplier() {
        nowSupplier = System::currentTimeMillis;
    }

    private static List<DrawGuessDTO> applyRequest(
            GameRoom room,
            String playerKey,
            String playerName,
            DrawGuessDTO request,
            long nowMs,
            boolean scheduleTimeout) {
        switch (request.getEvent()) {
            case START_ROUND:
                return Collections.singletonList(startRound(room, playerKey, playerName, request, nowMs, scheduleTimeout));
            case DRAW:
                validateDrawerInput(room, playerKey);
                return Collections.singletonList(drawEvent(room, playerKey, playerName, request));
            case CLEAR:
                validateDrawerInput(room, playerKey);
                return Collections.singletonList(baseEvent(stateOf(room), DrawGuessDTO.Event.CLEAR));
            case GUESS:
                return handleGuess(room, playerKey, playerName, request, nowMs);
            case CORRECT:
            case ROUND_END:
                throw new IllegalArgumentException("你画我猜判定由服务端处理，请不要直接提交结果事件");
            default:
                return Collections.emptyList();
        }
    }

    private static DrawGuessDTO startRound(
            GameRoom room,
            String playerKey,
            String playerName,
            DrawGuessDTO request,
            long nowMs,
            boolean scheduleTimeout) {
        RoomState state = stateOf(room);
        synchronized (state) {
            if (state.closed || state.matchFinished) {
                throw new IllegalArgumentException("本局你画我猜已经结束");
            }
            if (state.playing) {
                throw new IllegalArgumentException("当前题目仍在进行中");
            }
            String drawerKey = currentDrawerKey(state);
            if (!drawerKey.equals(playerKey)) {
                throw new IllegalArgumentException("还没轮到你出题");
            }
            String word = trimToNull(request.getWord());
            if (word == null) {
                throw new IllegalArgumentException("请先填写题目答案");
            }

            state.playing = true;
            state.drawerId = drawerKey;
            state.drawerName = firstNonBlank(state.playerNames.get(drawerKey), playerName, "画手");
            state.word = word;
            state.maskedWord = maskWord(word);
            state.wordLength = countWordLength(word);
            state.hint = firstNonBlank(request.getHint(), "");
            state.startedAt = nowMs;
            state.deadlineAt = nowMs + state.timeLimitSeconds * 1000L;
            state.answeredPlayerIds.clear();
            state.correctPlayers.clear();
            state.lines.clear();
            applyPetAssist(room, state);
            DrawGuessRewardService.handleStart(room);
            consumeDrawGuessGuesserItems(room, drawerKey);
            if (scheduleTimeout) {
                scheduleTimeout(room.getId(), state);
            }
            return baseEvent(state, DrawGuessDTO.Event.START_ROUND);
        }
    }

    private static List<DrawGuessDTO> handleGuess(
            GameRoom room,
            String playerKey,
            String playerName,
            DrawGuessDTO request,
            long nowMs) {
        RoomState state = stateOf(room);
        synchronized (state) {
            if (!state.playing) {
                throw new IllegalArgumentException("当前没有进行中的题目");
            }
            if (nowMs > state.deadlineAt) {
                return Collections.singletonList(finishRound(room, state, "timeout", nowMs));
            }
            if (playerKey.equals(state.drawerId)) {
                throw new IllegalArgumentException("画手不能参与猜答案");
            }
            if (state.answeredPlayerIds.contains(playerKey)) {
                throw new IllegalArgumentException("你已经猜对了，请等待下一题");
            }
            String text = trimToNull(request.getText());
            if (text == null) {
                throw new IllegalArgumentException("请输入答案");
            }

            List<DrawGuessDTO> events = new ArrayList<>();
            events.add(guessEvent(state, playerKey, playerName, text));
            if (!normalizeText(text).equals(normalizeText(state.word))) {
                return events;
            }

            int rank = state.correctPlayers.size() + 1;
            int scoreDelta = scoreDelta(rank);
            int totalScore = state.scores.getOrDefault(playerKey, 0) + scoreDelta;
            state.scores.put(playerKey, totalScore);
            state.playerNames.put(playerKey, firstNonBlank(playerName, state.playerNames.get(playerKey), "玩家"));
            state.answeredPlayerIds.add(playerKey);
            DrawGuessDTO.CorrectPlayer correctPlayer = new DrawGuessDTO.CorrectPlayer(
                    playerKey,
                    state.playerNames.get(playerKey),
                    rank,
                    scoreDelta);
            state.correctPlayers.add(correctPlayer);

            DrawGuessDTO correct = baseEvent(state, DrawGuessDTO.Event.CORRECT);
            correct.setGuesserId(playerKey);
            correct.setGuesserName(correctPlayer.getPlayerName());
            correct.setCorrectRank(rank);
            correct.setScoreDelta(scoreDelta);
            events.add(correct);
            if (rank == 1) {
                refundRemainingDrawGuessItems(room);
                DrawGuessRewardService.handleCorrect(room, correct);
            }
            if (allGuessersAnswered(state)) {
                events.add(finishRound(room, state, "all_correct", nowMs));
            }
            return events;
        }
    }

    private static DrawGuessDTO finishRound(GameRoom room, RoomState state, String reason, long nowMs) {
        if (!state.playing && state.word == null) {
            throw new IllegalArgumentException("当前没有可结束的题目");
        }
        cancelTimeout(state);
        String answer = state.word;
        int nextTurnIndex = state.turnIndex + 1;
        boolean matchFinished = nextTurnIndex >= state.totalTurns;

        DrawGuessDTO dto = baseEvent(state, DrawGuessDTO.Event.ROUND_END);
        dto.setWord(answer);
        dto.setRoundEndReason(reason);
        dto.setTurnIndex(nextTurnIndex);
        dto.setRoundNo(matchFinished ? state.totalRounds : roundNo(nextTurnIndex, state.playerOrder.size()));
        dto.setMatchFinished(matchFinished);
        dto.setStartedAt(nowMs);
        dto.setDeadlineAt(nowMs);

        state.playing = false;
        state.matchFinished = matchFinished;
        state.turnIndex = nextTurnIndex;
        state.roundNo = dto.getRoundNo();
        state.word = null;
        state.startedAt = 0L;
        state.deadlineAt = 0L;
        state.lines.clear();
        if (matchFinished) {
            DrawGuessRewardService.clearRoom(room.getId());
        }
        return dto;
    }

    private static void validateDrawerInput(GameRoom room, String playerKey) {
        RoomState state = stateOf(room);
        synchronized (state) {
            if (!state.playing) {
                throw new IllegalArgumentException("当前没有进行中的题目");
            }
            if (!playerKey.equals(state.drawerId)) {
                throw new IllegalArgumentException("只有当前画手可以操作画板");
            }
        }
    }

    private static DrawGuessDTO drawEvent(RoomState state, String playerKey, String playerName, DrawGuessDTO request) {
        DrawGuessDTO dto = baseEvent(state, request.getEvent());
        dto.setDrawerId(playerKey);
        dto.setDrawerName(firstNonBlank(playerName, state.drawerName, "画手"));
        dto.setLine(request.getLine());
        return dto;
    }

    private static DrawGuessDTO drawEvent(GameRoom room, String playerKey, String playerName, DrawGuessDTO request) {
        RoomState state = stateOf(room);
        synchronized (state) {
            DrawGuessDTO dto = drawEvent(state, playerKey, playerName, request);
            if (request.getLine() != null) {
                state.lines.add(request.getLine());
            }
            return dto;
        }
    }

    private static DrawGuessDTO guessEvent(RoomState state, String playerKey, String playerName, String text) {
        DrawGuessDTO dto = baseEvent(state, DrawGuessDTO.Event.GUESS);
        dto.setGuesserId(playerKey);
        dto.setGuesserName(firstNonBlank(playerName, state.playerNames.get(playerKey), "玩家"));
        dto.setText(text);
        return dto;
    }

    private static RoomState stateOf(GameRoom room) {
        if (room == null || room.getId() == null) {
            throw new IllegalArgumentException("游戏房间不存在");
        }
        return ROOM_STATES.computeIfAbsent(room.getId(), id -> createState(room));
    }

    private static RoomState createState(GameRoom room) {
        List<String> playerOrder = new ArrayList<>();
        Map<String, String> playerNames = new LinkedHashMap<>();
        Map<String, Integer> scores = new LinkedHashMap<>();
        room.getUsers().forEach((playerKey, player) -> {
            playerOrder.add(playerKey);
            playerNames.put(playerKey, displayName(player));
            scores.put(playerKey, 0);
        });
        if (playerOrder.size() < MIN_PLAYERS || playerOrder.size() > MAX_PLAYERS) {
            throw new IllegalArgumentException("你画我猜只支持 2-8 人");
        }
        RoomState state = new RoomState();
        state.roomId = room.getId();
        state.playerOrder = playerOrder;
        state.playerNames = playerNames;
        state.scores = scores;
        state.totalRounds = normalizeRoundCount(room.getDrawGuessRoundCount());
        state.timeLimitSeconds = normalizeTimeLimitSeconds(room.getDrawGuessTimeLimitSeconds());
        state.totalTurns = state.totalRounds * playerOrder.size();
        state.roundNo = 1;
        return state;
    }

    private static DrawGuessDTO baseEvent(RoomState state, DrawGuessDTO.Event event) {
        DrawGuessDTO dto = new DrawGuessDTO();
        dto.setRoomId(state.roomId);
        dto.setGame(Game.DRAW_GUESS);
        dto.setEvent(event);
        dto.setDrawerId(state.drawerId);
        dto.setDrawerName(state.drawerName);
        dto.setMaskedWord(state.maskedWord);
        dto.setWordLength(state.wordLength);
        dto.setHint(state.hint);
        dto.setWord(event == DrawGuessDTO.Event.START_ROUND ? state.word : null);
        dto.setRoundNo(state.roundNo);
        dto.setTotalRounds(state.totalRounds);
        dto.setTurnIndex(state.turnIndex);
        dto.setTotalTurns(state.totalTurns);
        dto.setTimeLimitSeconds(state.timeLimitSeconds);
        dto.setStartedAt(state.startedAt);
        dto.setDeadlineAt(state.deadlineAt);
        dto.setAnsweredPlayerIds(new ArrayList<>(state.answeredPlayerIds));
        dto.setCorrectPlayers(new ArrayList<>(state.correctPlayers));
        dto.setScores(new LinkedHashMap<>(state.scores));
        dto.setPlayerNames(new LinkedHashMap<>(state.playerNames));
        dto.setMatchFinished(state.matchFinished);
        dto.setPetItemId(state.petItemId);
        dto.setPetItemNotice(state.petItemNotice);
        dto.setPetItemPattern(state.petItemPattern);
        dto.setPetItemRevealedMask(state.petItemRevealedMask);
        dto.setPetItemHintDelaySeconds(state.petItemHintDelaySeconds);
        return dto;
    }

    private static void broadcast(GameRoom room, User actor, DrawGuessDTO event) {
        if (event.getEvent() == DrawGuessDTO.Event.START_ROUND) {
            room.getUsers().forEach((playerKey, player) -> {
                User target = UserCache.get(player.getChannelId());
                if (target != null) {
                    boolean drawer = playerKey.equals(event.getDrawerId());
                    target.send(ResponseBuilder.build(actor, visibleStart(event, drawer), MessageType.GAME));
                }
            });
            return;
        }
        room.getUsers().forEach((playerKey, player) -> {
            User target = UserCache.get(player.getChannelId());
            if (target != null) {
                target.send(ResponseBuilder.build(actor, event, MessageType.GAME));
            }
        });
    }

    private static DrawGuessDTO visibleStart(DrawGuessDTO source, boolean drawer) {
        DrawGuessDTO dto = copy(source);
        if (!drawer) {
            dto.setWord(null);
        }
        return dto;
    }

    private static DrawGuessDTO copy(DrawGuessDTO source) {
        return JSONUtil.toBean(JSONUtil.toJsonStr(source), DrawGuessDTO.class);
    }

    private static void scheduleTimeout(String roomId, RoomState state) {
        cancelTimeout(state);
        int version = ++state.timeoutVersion;
        long delay = Math.max(0L, state.deadlineAt - now());
        state.timeoutTask = TIMEOUT_EXECUTOR.schedule(() -> timeoutRound(roomId, version), delay, TimeUnit.MILLISECONDS);
    }

    private static void timeoutRound(String roomId, int version) {
        GameRoom room = GameRoomCache.getGameRoom(roomId);
        RoomState state = ROOM_STATES.get(roomId);
        if (room == null || state == null) {
            clearRoom(roomId);
            return;
        }
        DrawGuessDTO event = null;
        synchronized (state) {
            if (!state.playing || state.closed || state.timeoutVersion != version || now() < state.deadlineAt) {
                return;
            }
            try {
                event = finishRound(room, state, "timeout", now());
            } catch (RuntimeException e) {
                log.warn("你画我猜超时结算失败 -> roomId: {}", roomId, e);
            }
        }
        if (event != null) {
            broadcast(room, null, event);
        }
    }

    private static void cancelTimeout(RoomState state) {
        state.timeoutVersion++;
        if (state.timeoutTask != null) {
            state.timeoutTask.cancel(false);
            state.timeoutTask = null;
        }
    }

    private static void applyPetAssist(GameRoom room, RoomState state) {
        state.petItemId = null;
        state.petItemNotice = null;
        state.petItemPattern = null;
        state.petItemRevealedMask = null;
        state.petItemHintDelaySeconds = null;
        for (String itemId : resolveGuesserCarryItems(room, state.drawerId)) {
            applyPetAssistItem(state, itemId);
        }
    }

    private static void applyPetAssistItem(RoomState state, String itemId) {
        if (state.petItemId == null) {
            state.petItemId = itemId;
        }
        if ("item_draw_advance_hint".equals(itemId)) {
            appendPetItemNotice(state, trimToNull(state.hint) == null
                    ? "抢先闻闻已触发：本题没有额外提示。"
                    : "抢先闻闻已触发：系统提示立即显示。");
            state.petItemHintDelaySeconds = 0;
            return;
        }
        if ("item_draw_pattern".equals(itemId)) {
            appendPetItemNotice(state, "字形骨牌已触发：展示答案字形结构。");
            state.petItemPattern = buildPattern(state.word);
            return;
        }
        if ("item_draw_overlap".equals(itemId)) {
            appendPetItemNotice(state, "沾边铃已触发：下一次猜错时会提示是否至少有一个相同汉字。");
            return;
        }
        if ("item_draw_replay".equals(itemId)) {
            appendPetItemNotice(state, "画迹回放已触发：本轮可辅助回看笔画顺序。");
            return;
        }
        if ("item_draw_reveal_char".equals(itemId)) {
            appendPetItemNotice(state, "漏字饼干已触发：揭示答案中的 1 个字。");
            state.petItemRevealedMask = revealMask(state.word, 1);
        }
    }

    private static List<String> resolveGuesserCarryItems(GameRoom room, String drawerKey) {
        List<String> itemIds = new ArrayList<>();
        for (Map.Entry<String, GameRoom.Player> entry : room.getUsers().entrySet()) {
            if (entry.getKey().equals(drawerKey)) {
                continue;
            }
            addDrawGuessCarryItem(itemIds, entry.getValue().getPetPlayItemId());
            addDrawGuessCarryItem(itemIds, entry.getValue().getPetInteractionItemId());
        }
        return itemIds;
    }

    private static void addDrawGuessCarryItem(List<String> itemIds, String itemId) {
        if (PetGameItemRules.isCarryItem(Game.DRAW_GUESS, itemId)) {
            itemIds.add(itemId);
        }
    }

    private static void consumeDrawGuessGuesserItems(GameRoom room, String drawerKey) {
        room.getUsers().forEach((playerKey, player) -> {
            if (!drawerKey.equals(playerKey)) {
                settleDrawGuessCarryItem(room, player, player.getPetPlayItemId(), SLOT_GAMEPLAY, true);
                settleDrawGuessCarryItem(room, player, player.getPetInteractionItemId(), SLOT_INTERACTION, true);
            }
        });
    }

    private static void refundRemainingDrawGuessItems(GameRoom room) {
        room.getUsers().forEach((playerKey, player) -> {
            settleDrawGuessCarryItem(room, player, player == null ? null : player.getPetPlayItemId(), SLOT_GAMEPLAY, false);
            settleDrawGuessCarryItem(room, player, player == null ? null : player.getPetInteractionItemId(), SLOT_INTERACTION, false);
        });
    }

    private static void settleDrawGuessCarryItem(GameRoom room, GameRoom.Player player, String itemId, String slot,
                                                 boolean consumed) {
        if (player == null || !PetGameItemRules.isCarryItem(Game.DRAW_GUESS, itemId)) {
            return;
        }
        if (consumed) {
            PetGameItemDeclarationService.settleConsumed(room, player.getId(), itemId, slot);
        } else {
            PetGameItemDeclarationService.settleRefunded(room, player.getId(), itemId, slot);
        }
        clearCarriedItem(player, slot);
    }

    private static void clearCarriedItem(GameRoom.Player player, String slot) {
        if (SLOT_GAMEPLAY.equals(slot)) {
            player.setPetPlayItemId(null);
        } else if (SLOT_INTERACTION.equals(slot)) {
            player.setPetInteractionItemId(null);
        }
    }

    private static void appendPetItemNotice(RoomState state, String notice) {
        String next = trimToNull(notice);
        if (next == null) {
            return;
        }
        state.petItemNotice = trimToNull(state.petItemNotice) == null ? next : state.petItemNotice + " " + next;
    }

    private static boolean allGuessersAnswered(RoomState state) {
        int guesserCount = 0;
        for (String playerId : state.playerOrder) {
            if (!playerId.equals(state.drawerId)) {
                guesserCount++;
            }
        }
        return guesserCount > 0 && state.answeredPlayerIds.size() >= guesserCount;
    }

    private static String currentDrawerKey(RoomState state) {
        if (state.playerOrder.isEmpty()) {
            throw new IllegalArgumentException("当前房间没有玩家");
        }
        return state.playerOrder.get(state.turnIndex % state.playerOrder.size());
    }

    private static int scoreDelta(int rank) {
        if (rank == 1) {
            return BASE_SCORE + 3;
        }
        if (rank == 2) {
            return BASE_SCORE + 2;
        }
        if (rank == 3) {
            return BASE_SCORE + 1;
        }
        return BASE_SCORE;
    }

    private static int roundNo(int turnIndex, int playerCount) {
        return Math.min(MAX_ROUNDS, turnIndex / Math.max(1, playerCount) + 1);
    }

    private static int normalizeRoundCount(int roundCount) {
        return roundCount > 0 ? Math.min(roundCount, MAX_ROUNDS) : DEFAULT_ROUNDS;
    }

    private static int normalizeTimeLimitSeconds(int seconds) {
        return seconds == 60 || seconds == 90 || seconds == 120 ? seconds : DEFAULT_TIME_LIMIT_SECONDS;
    }

    private static String normalizeRoundEndReason(String reason) {
        return "timeout".equals(reason) ? "timeout" : "all_correct";
    }

    private static String normalizeText(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "").trim().toLowerCase();
    }

    private static String maskWord(String word) {
        StringBuilder builder = new StringBuilder();
        word.codePoints().forEach(codePoint -> {
            if (Character.isWhitespace(codePoint)) {
                builder.appendCodePoint(codePoint);
            } else {
                builder.append('＿');
            }
        });
        return builder.toString();
    }

    private static int countWordLength(String word) {
        final int[] count = {0};
        word.codePoints().forEach(codePoint -> {
            if (!Character.isWhitespace(codePoint)) {
                count[0]++;
            }
        });
        return count[0];
    }

    private static String buildPattern(String word) {
        String[] labels = {"①", "②", "③", "④", "⑤", "⑥", "⑦", "⑧", "⑨"};
        Map<String, String> seen = new LinkedHashMap<>();
        StringBuilder builder = new StringBuilder();
        word.codePoints().forEach(codePoint -> {
            if (Character.isWhitespace(codePoint)) {
                builder.appendCodePoint(codePoint);
                return;
            }
            String key = new String(Character.toChars(codePoint));
            String label = seen.computeIfAbsent(key, ignored -> {
                int index = seen.size();
                return index < labels.length ? labels[index] : String.valueOf(index + 1);
            });
            builder.append(label);
        });
        return builder.toString();
    }

    private static String revealMask(String word, int revealCount) {
        final int[] revealed = {0};
        StringBuilder builder = new StringBuilder();
        word.codePoints().forEach(codePoint -> {
            if (Character.isWhitespace(codePoint)) {
                builder.appendCodePoint(codePoint);
                return;
            }
            if (revealed[0] < revealCount) {
                builder.appendCodePoint(codePoint);
                revealed[0]++;
            } else {
                builder.append('＿');
            }
        });
        return builder.toString();
    }

    private static DrawGuessDTO toDrawGuessDTO(GameDTO body) {
        if (body instanceof DrawGuessDTO) {
            return (DrawGuessDTO) body;
        }
        return JSONUtil.toBean(JSONUtil.toJsonStr(body), DrawGuessDTO.class);
    }

    private static long now() {
        return nowSupplier.getAsLong();
    }

    private static String displayName(User user) {
        return firstNonBlank(user == null ? null : user.getNickname(), user == null ? null : user.getUsername(), "玩家");
    }

    private static String displayName(GameRoom.Player player) {
        return firstNonBlank(player == null ? null : player.getNickname(), player == null ? null : player.getUsername(), "玩家");
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return "";
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static final class RoomState {
        private String roomId;
        private List<String> playerOrder = new ArrayList<>();
        private Map<String, String> playerNames = new LinkedHashMap<>();
        private Map<String, Integer> scores = new LinkedHashMap<>();
        private Set<String> answeredPlayerIds = new LinkedHashSet<>();
        private List<DrawGuessDTO.CorrectPlayer> correctPlayers = new ArrayList<>();
        private List<DrawGuessDTO.Line> lines = new ArrayList<>();
        private String drawerId;
        private String drawerName;
        private String word;
        private String maskedWord;
        private int wordLength;
        private String hint;
        private int roundNo;
        private int totalRounds;
        private int turnIndex;
        private int totalTurns;
        private int timeLimitSeconds;
        private long startedAt;
        private long deadlineAt;
        private boolean playing;
        private boolean matchFinished;
        private boolean closed;
        private String petItemId;
        private String petItemNotice;
        private String petItemPattern;
        private String petItemRevealedMask;
        private Integer petItemHintDelaySeconds;
        private int timeoutVersion;
        private ScheduledFuture<?> timeoutTask;
    }
}
