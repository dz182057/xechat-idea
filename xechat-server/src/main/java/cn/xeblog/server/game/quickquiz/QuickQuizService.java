package cn.xeblog.server.game.quickquiz;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.xeblog.commons.entity.Response;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.game.quickquiz.*;
import cn.xeblog.commons.enums.Game;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.account.DbInitializer;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.cache.UserCache;
import cn.xeblog.server.pet.MiniGameRewards;
import cn.xeblog.server.pet.PetGameItemDeclarationService;
import cn.xeblog.server.pet.PetService;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSession;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 快问快答知识题、轮次计分和报名奖池。
 */
@Slf4j
public final class QuickQuizService {

    private static final int MAX_PLAYERS = 8;
    private static final String SLOT_GAMEPLAY = "gameplay";
    private static final String SLOT_INTERACTION = "interaction";
    private static final String ITEM_SCORE_PAD = "item_quiz_score_pad";
    private static final String ITEM_WRONG_OPTION = "item_quiz_wrong_option";
    private static final String ITEM_DUEL = "item_quiz_duel";
    private static final String ITEM_PROPHECY = "item_prophecy";
    private static final int SCORE_PAD_BLOCK_POINTS = 2;
    private static final int DUEL_REWARD_BONES = 30;

    private static final Map<String, RoomState> ROOM_STATES = new ConcurrentHashMap<>();

    private static Economy economy = PetService::changeBones;
    private static MiniGameRewards miniGameRewards = MiniGameRewards.petService();
    private static LongSupplier nowSupplier = System::currentTimeMillis;

    private QuickQuizService() {
    }

    public static List<QuickQuizQuestionDTO> listQuestions() {
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            List<QuickQuizQuestion> rows = session.getMapper(QuickQuizMapper.class).listActiveQuestions();
            List<QuickQuizQuestionDTO> result = new ArrayList<>(rows.size());
            for (QuickQuizQuestion row : rows) {
                result.add(toQuestionDTO(row, true));
            }
            return result;
        }
    }

    public static List<QuickQuizQuestionDTO> saveQuestions(List<QuickQuizQuestionDTO> questions) {
        List<QuickQuizQuestionDTO> normalized = normalizeQuestions(questions);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("快问快答题库不能为空");
        }

        long now = now();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            QuickQuizMapper mapper = session.getMapper(QuickQuizMapper.class);
            mapper.deactivateAllQuestions();
            for (int i = 0; i < normalized.size(); i++) {
                QuickQuizQuestionDTO dto = normalized.get(i);
                mapper.upsertQuestion(QuickQuizQuestion.builder()
                        .question(dto.getQuestion())
                        .optionsJson(JSONUtil.toJsonStr(dto.getOptions()))
                        .correctAnswerIndex(dto.getCorrectAnswerIndex())
                        .score(dto.getScore())
                        .sortOrder(i)
                        .active(1)
                        .createdAt(now)
                        .updatedAt(now)
                        .build());
            }
            session.commit();
            return normalized;
        }
    }

    public static int availableCount(GameRoom room) {
        RoomState state = room == null ? null : ROOM_STATES.get(room.getId());
        List<Long> usedQuestionIds = state == null ? Collections.emptyList() : new ArrayList<>(state.usedQuestionIds);
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            return session.getMapper(QuickQuizMapper.class).countAvailableQuestions(usedQuestionIds);
        }
    }

    public static QuickQuizQuestionDTO nextQuestion(User user, GameRoom room) {
        return nextQuestion(user, room, QuickQuizService::randomAvailableQuestion);
    }

    static QuickQuizQuestionDTO nextQuestion(User user, GameRoom room, QuestionPicker questionPicker) {
        if (!room.isHomeowner(user)) {
            throw new IllegalArgumentException("仅房主可以开始下一题");
        }
        List<User> players = getRoomUsers(room);
        validatePlayers(players);

        RoomState state = ROOM_STATES.computeIfAbsent(room.getId(), id -> new RoomState(room.getId()));
        synchronized (state) {
            chargeEntryFeeIfNeeded(room, state, players);
            long now = now();
            if (state.currentQuestion != null) {
                if (now <= state.currentQuestion.getDeadlineAt()) {
                    return copyQuestion(state.currentQuestion, false);
                }
                revealLocked(room, state, now);
            }
            if (state.roundNo >= room.getQuickQuizQuestionCount()) {
                throw new IllegalArgumentException("本局题目已经答完");
            }
            return startNextQuestion(room, state, players, questionPicker);
        }
    }

    public static QuickQuizAnswerResultDTO submitAnswer(User user, GameRoom room, QuickQuizSubmitAnswerDTO body) {
        RoomState state = ROOM_STATES.get(room.getId());
        if (state == null) {
            throw new IllegalArgumentException("当前没有可提交的题目");
        }
        synchronized (state) {
            if (state.closed || state.currentQuestion == null) {
                throw new IllegalArgumentException("当前没有可提交的题目");
            }
            if (body.getQuestionId() != state.currentQuestion.getId()) {
                throw new IllegalArgumentException("题目已变化，请等待下一题");
            }
            String key = playerKey(user);
            if (!state.expectedPlayerKeys.contains(key)) {
                throw new IllegalArgumentException("你不在当前答题房间内");
            }
            long now = now();
            if (now > state.currentQuestion.getDeadlineAt()) {
                return revealLocked(room, state, now);
            }
            QuickQuizAnswerViewDTO answer = buildAnswer(user, room, state.currentQuestion, body, now, state);
            state.answers.putIfAbsent(key, answer);
            if (state.answers.size() >= state.expectedPlayerKeys.size()) {
                return revealLocked(room, state, now);
            }
            return null;
        }
    }

    public static List<QuickQuizRecordDTO> myRecords(User user) {
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            String currentPlayerKey = playerKey(user);
            return toRecordDTOs(session.getMapper(QuickQuizMapper.class).listRecordsByPlayer(currentPlayerKey), currentPlayerKey);
        }
    }

    public static List<QuickQuizRecordDTO> allRecords() {
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            return toRecordDTOs(session.getMapper(QuickQuizMapper.class).listAllRecords());
        }
    }

    public static void clearRoom(String roomId) {
        RoomState state = ROOM_STATES.get(roomId);
        if (state != null) {
            synchronized (state) {
                state.closed = true;
                refundEntryFees(state);
                state.prizePool = state.chargedEntryFees.values().stream().mapToInt(Integer::intValue).sum();
                if (state.chargedEntryFees.isEmpty()) {
                    ROOM_STATES.remove(roomId, state);
                }
            }
        }
    }

    public static void clearRoom(GameRoom room) {
        if (room != null) {
            clearRoom(room.getId());
        }
    }

    public static String playerKey(User user) {
        return user.getIdentityKey();
    }

    static int poolOf(String roomId) {
        RoomState state = ROOM_STATES.get(roomId);
        return state == null ? 0 : state.prizePool;
    }

    static void setEconomyForTest(Economy testEconomy) {
        economy = testEconomy == null ? PetService::changeBones : testEconomy;
    }

    static void resetEconomy() {
        economy = PetService::changeBones;
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

    private static QuickQuizAnswerViewDTO buildAnswer(User user, GameRoom room, QuickQuizQuestionDTO question,
                                                       QuickQuizSubmitAnswerDTO body, long answeredAt,
                                                       RoomState state) {
        int choiceIndex = body.getChoiceIndex();
        boolean skipped = choiceIndex < 0;
        String choiceText = skipped ? "不作答" : optionText(question, choiceIndex);
        boolean correct = !skipped && choiceIndex == question.getCorrectAnswerIndex();
        int delta = skipped ? 0 : (correct ? question.getScore() : -wrongPenalty(question.getScore()));
        String key = playerKey(user);
        if (!correct && !skipped) {
            delta = applyScorePadIfNeeded(user, room, state, key, delta);
        }
        int totalScore = state.scores.getOrDefault(key, 0) + delta;
        state.scores.put(key, totalScore);
        return new QuickQuizAnswerViewDTO(key, user.getUsername(), choiceIndex, choiceText, answeredAt,
                correct, skipped, delta, totalScore);
    }

    private static int applyScorePadIfNeeded(User user, GameRoom room, RoomState state, String playerKey, int delta) {
        GameRoom.Player player = room.getUsers().get(playerKey);
        String slot = carriedItemSlot(player, ITEM_SCORE_PAD);
        if (slot == null || delta >= 0) {
            return delta;
        }
        int reducedPenalty = Math.max(0, Math.abs(delta) - SCORE_PAD_BLOCK_POINTS);
        PetGameItemDeclarationService.settleConsumed(room, playerKey, ITEM_SCORE_PAD, slot);
        clearCarriedItem(player, slot);
        if (reducedPenalty == 0) {
            state.petItemNotices.add("护分爪垫触发，" + displayName(user) + " 本题答错扣分被抵消。");
        } else {
            state.petItemNotices.add("护分爪垫触发，" + displayName(user) + " 本题答错少扣 " + SCORE_PAD_BLOCK_POINTS + " 分。");
        }
        return -reducedPenalty;
    }

    private static String optionText(QuickQuizQuestionDTO question, int choiceIndex) {
        List<String> options = question.getOptions();
        if (choiceIndex < 0 || choiceIndex >= options.size()) {
            throw new IllegalArgumentException("请选择有效答案");
        }
        return options.get(choiceIndex);
    }

    private static int wrongPenalty(int score) {
        return Math.max(1, score / 2);
    }

    private static QuickQuizAnswerResultDTO revealLocked(GameRoom room, RoomState state, long now) {
        if (state.closed || state.currentQuestion == null) {
            return null;
        }
        for (User player : state.players) {
            String key = playerKey(player);
            if (!state.answers.containsKey(key)) {
                QuickQuizSubmitAnswerDTO skip = new QuickQuizSubmitAnswerDTO(room.getId(), state.currentQuestion.getId(), -1, "不作答");
                state.answers.put(key, buildAnswer(player, room, state.currentQuestion, skip, now, state));
            }
        }

        List<QuickQuizAnswerViewDTO> answers = new ArrayList<>();
        for (User player : state.players) {
            answers.add(state.answers.get(playerKey(player)));
        }
        saveAnswers(room, state.currentQuestion, answers);

        state.roundNo++;
        boolean finished = state.roundNo >= room.getQuickQuizQuestionCount();
        List<QuickQuizPlayerScoreDTO> rankings = rankings(state, finished);
        if (finished) {
            applyMiniGameRewards(state, rankings, now);
            applyDuelSettlements(room, state, rankings);
            applyProphecySettlements(room, state, rankings);
            refundUntriggeredPlayItems(room, state);
        }
        int rewardPerWinner = finished ? applyRewards(state, rankings) : 0;
        QuickQuizQuestionDTO resultQuestion = copyQuestion(state.currentQuestion, true);
        QuickQuizAnswerResultDTO result = new QuickQuizAnswerResultDTO(room.getId(), resultQuestion, answers, rankings,
                state.roundNo, room.getQuickQuizQuestionCount(), finished, state.prizePool, rewardPerWinner,
                finished && state.economyApplied);
        result.setPetItemNotices(new ArrayList<>(state.petItemNotices));
        sendToRoom(room, ResponseBuilder.build(null, result,
                finished ? MessageType.QUICK_QUIZ_MATCH_RESULT : MessageType.QUICK_QUIZ_ROUND_RESULT));

        state.currentQuestion = null;
        state.answers.clear();
        state.expectedPlayerKeys.clear();
        state.disabledWrongOptionByPlayerKey.clear();
        state.petItemNotices.clear();
        if (finished) {
            state.closed = true;
            state.prizePool = 0;
            ROOM_STATES.remove(room.getId(), state);
        }
        return result;
    }

    private static QuickQuizQuestionDTO startNextQuestion(GameRoom room, RoomState state, List<User> players,
                                                          QuestionPicker questionPicker) {
        QuickQuizQuestion question = questionPicker.pick(new ArrayList<>(state.usedQuestionIds));
        if (question == null) {
            throw new IllegalArgumentException("剩余可用题数不足");
        }
        state.usedQuestionIds.add(question.getId());
        long startedAt = now();
        QuickQuizQuestionDTO dto = toQuestionDTO(question, true);
        dto.setStartedAt(startedAt);
        dto.setDeadlineAt(startedAt + resolveTimeLimitSeconds(room) * 1000L);
        dto.setRoundNo(state.roundNo + 1);
        dto.setTotalRounds(room.getQuickQuizQuestionCount());

        state.start(dto, players);
        applyWrongOptionItems(room, state);
        QuickQuizQuestionDTO callerQuestion = copyQuestion(dto, false);
        for (User player : players) {
            QuickQuizQuestionDTO visibleQuestion = visibleQuestionForPlayer(dto, state, playerKey(player));
            User target = UserCache.get(player.getId());
            if (target != null) {
                target.send(ResponseBuilder.build(null, visibleQuestion, MessageType.QUICK_QUIZ_QUESTION));
            }
            if (playerKey(player).equals(playerKey(room.getHomeowner()))) {
                callerQuestion = visibleQuestion;
            }
        }
        return callerQuestion;
    }

    private static void applyWrongOptionItems(GameRoom room, RoomState state) {
        QuickQuizQuestionDTO question = state.currentQuestion;
        if (question == null || question.getOptions() == null || question.getOptions().size() < 3) {
            return;
        }
        int disabledOptionIndex = firstWrongOptionIndex(question);
        if (disabledOptionIndex < 0) {
            return;
        }
        for (User player : state.players) {
            String key = playerKey(player);
            GameRoom.Player roomPlayer = room.getUsers().get(key);
            String slot = carriedItemSlot(roomPlayer, ITEM_WRONG_OPTION);
            if (slot == null) {
                continue;
            }
            state.disabledWrongOptionByPlayerKey.put(key, disabledOptionIndex);
            PetGameItemDeclarationService.settleConsumed(room, key, ITEM_WRONG_OPTION, slot);
            clearCarriedItem(roomPlayer, slot);
        }
    }

    private static int firstWrongOptionIndex(QuickQuizQuestionDTO question) {
        for (int i = 0; i < question.getOptions().size(); i++) {
            if (i != question.getCorrectAnswerIndex()) {
                return i;
            }
        }
        return -1;
    }

    private static QuickQuizQuestionDTO visibleQuestionForPlayer(QuickQuizQuestionDTO question, RoomState state,
                                                                 String playerKey) {
        QuickQuizQuestionDTO visible = copyQuestion(question, false);
        Integer disabledOptionIndex = state.disabledWrongOptionByPlayerKey.get(playerKey);
        if (disabledOptionIndex != null) {
            visible.setPetItemDisabledOptionIndex(disabledOptionIndex);
            visible.setPetItemNotice("错项嗅探触发，已为你排除一个错误选项。");
        }
        return visible;
    }

    private static int resolveTimeLimitSeconds(GameRoom room) {
        return room.getQuickQuizTimeLimitSeconds() > 0 ? room.getQuickQuizTimeLimitSeconds() : 15;
    }

    private static void validatePlayers(List<User> players) {
        if (players.size() < 2) {
            throw new IllegalArgumentException("快问快答至少需要 2 人");
        }
        if (players.size() > MAX_PLAYERS) {
            throw new IllegalArgumentException("快问快答最多支持 8 人");
        }
    }

    private static void chargeEntryFeeIfNeeded(GameRoom room, RoomState state, List<User> players) {
        if (state.economyApplied) {
            return;
        }
        int entryFee = Math.max(0, room.getQuickQuizEntryFee());
        if (entryFee == 0) {
            state.economyApplied = true;
            return;
        }
        List<User> charged = new ArrayList<>();
        try {
            for (User player : players) {
                economy.change(player.getAccountId(), -entryFee);
                charged.add(player);
                state.chargedEntryFees.put(player.getAccountId(), entryFee);
            }
        } catch (RuntimeException e) {
            for (User player : charged) {
                try {
                    economy.change(player.getAccountId(), entryFee);
                    state.chargedEntryFees.remove(player.getAccountId());
                } catch (RuntimeException rollbackError) {
                    log.error("快问快答报名费回滚失败 -> accountId: {}", player.getAccountId(), rollbackError);
                }
            }
            throw e;
        }
        state.prizePool = entryFee * players.size();
        state.economyApplied = true;
    }

    private static void refundEntryFees(RoomState state) {
        for (Map.Entry<Long, Integer> entry : new ArrayList<>(state.chargedEntryFees.entrySet())) {
            try {
                economy.change(entry.getKey(), entry.getValue());
                state.chargedEntryFees.remove(entry.getKey());
            } catch (RuntimeException e) {
                log.error("快问快答异常结束返还报名费失败 -> roomId: {}, accountId: {}",
                        state.roomId, entry.getKey(), e);
            }
        }
    }

    private static void applyMiniGameRewards(RoomState state, List<QuickQuizPlayerScoreDTO> rankings, long now) {
        if (rankings.isEmpty()) {
            return;
        }
        Set<String> winnerKeys = new HashSet<>();
        for (QuickQuizPlayerScoreDTO ranking : rankings) {
            if (ranking.isWinner()) {
                winnerKeys.add(ranking.getPlayerKey());
            }
        }
        long durationSeconds = Math.max(0L, (now - state.matchStartedAt + 999L) / 1000L);
        List<Long> accountIds = new ArrayList<>();
        for (User player : state.players) {
            long accountId = player.getAccountId();
            if (accountId <= 0L) {
                continue;
            }
            accountIds.add(accountId);
            try {
                miniGameRewards.apply(accountId, Game.QUICK_QUIZ,
                        winnerKeys.contains(playerKey(player)), durationSeconds);
            } catch (RuntimeException e) {
                log.error("快问快答小游戏产出结算失败 -> accountId: {}", accountId, e);
            }
        }
        try {
            miniGameRewards.applyRoomBonus(Game.QUICK_QUIZ, accountIds, durationSeconds);
        } catch (RuntimeException e) {
            log.error("快问快答房间级彩蛋奖励结算失败 -> accountIds: {}", accountIds, e);
        }
    }

    private static int applyRewards(RoomState state, List<QuickQuizPlayerScoreDTO> rankings) {
        if (!state.economyApplied || state.rewardApplied || state.prizePool <= 0 || rankings.isEmpty()) {
            return 0;
        }
        int winners = 0;
        for (QuickQuizPlayerScoreDTO ranking : rankings) {
            if (ranking.isWinner()) {
                winners++;
            }
        }
        if (winners <= 0) {
            return 0;
        }
        int reward = (state.prizePool + winners - 1) / winners;
        for (QuickQuizPlayerScoreDTO ranking : rankings) {
            if (ranking.isWinner()) {
                long accountId = state.accountIds.getOrDefault(ranking.getPlayerKey(), 0L);
                if (accountId > 0L) {
                    economy.change(accountId, reward);
                    ranking.setRewardBones(reward);
                }
            }
        }
        state.rewardApplied = true;
        state.chargedEntryFees.clear();
        return reward;
    }

    private static void applyDuelSettlements(GameRoom room, RoomState state, List<QuickQuizPlayerScoreDTO> rankings) {
        Map<String, QuickQuizPlayerScoreDTO> rankingsByPlayer = new HashMap<>();
        for (QuickQuizPlayerScoreDTO ranking : rankings) {
            rankingsByPlayer.put(ranking.getPlayerKey(), ranking);
        }
        for (GameRoom.Player player : room.getUsers().values()) {
            String playerKey = player.getId();
            String slot = carriedItemSlot(player, ITEM_DUEL);
            if (slot == null) {
                continue;
            }
            GameRoom.Player target = defaultDuelTarget(room, playerKey);
            QuickQuizPlayerScoreDTO carrierScore = rankingsByPlayer.get(playerKey);
            QuickQuizPlayerScoreDTO targetScore = target == null ? null : rankingsByPlayer.get(target.getId());
            if (target == null || carrierScore == null || targetScore == null || carrierScore.getScore() == targetScore.getScore()) {
                PetGameItemDeclarationService.settleRefunded(room, playerKey, ITEM_DUEL, slot);
                state.petItemNotices.add("点名对决未结算，" + displayName(player) + " 的道具已返还。");
            } else if (carrierScore.getScore() > targetScore.getScore()) {
                int reward = PetGameItemDeclarationService.settleSucceededWithInteractionReward(
                        room, playerKey, ITEM_DUEL, slot, DUEL_REWARD_BONES);
                if (reward > 0) {
                    state.petItemNotices.add("点名对决命中，" + displayName(player) + " 得分高于 "
                            + displayName(target) + "，返还道具并获得 🦴" + reward + "。");
                } else {
                    state.petItemNotices.add("点名对决命中，" + displayName(player) + " 得分高于 "
                            + displayName(target) + "，返还道具；今日互动奖励额度已用完。");
                }
            } else {
                PetGameItemDeclarationService.settleFailed(room, playerKey, ITEM_DUEL, slot);
                state.petItemNotices.add("点名对决未命中，" + displayName(player) + " 得分未高于 "
                        + displayName(target) + "，道具已消耗。");
            }
            clearCarriedItem(player, slot);
        }
    }

    private static GameRoom.Player defaultDuelTarget(GameRoom room, String playerKey) {
        if (room.getUsers().size() < 3) {
            return null;
        }
        for (GameRoom.Player candidate : room.getUsers().values()) {
            if (!Objects.equals(candidate.getId(), playerKey)) {
                return candidate;
            }
        }
        return null;
    }

    private static void applyProphecySettlements(GameRoom room, RoomState state,
                                                 List<QuickQuizPlayerScoreDTO> rankings) {
        List<QuickQuizPlayerScoreDTO> winners = new ArrayList<>();
        for (QuickQuizPlayerScoreDTO ranking : rankings) {
            if (ranking.isWinner()) {
                winners.add(ranking);
            }
        }
        boolean uniqueWinner = winners.size() == 1;
        String winnerKey = uniqueWinner ? winners.get(0).getPlayerKey() : null;
        int rewardBones = prophecyRewardBones(room.getUsers().size());
        for (GameRoom.Player player : room.getUsers().values()) {
            String playerKey = player.getId();
            String slot = carriedItemSlot(player, ITEM_PROPHECY);
            if (slot == null) {
                continue;
            }
            if (!uniqueWinner) {
                PetGameItemDeclarationService.settleRefunded(room, playerKey, ITEM_PROPHECY, slot);
                state.petItemNotices.add("胜负预言贴未结算，快问快答出现并列胜者，"
                        + displayName(player) + " 的道具已返还。");
            } else if (Objects.equals(playerKey, winnerKey)) {
                int reward = PetGameItemDeclarationService.settleSucceededWithInteractionReward(
                        room, playerKey, ITEM_PROPHECY, slot, rewardBones);
                if (reward > 0) {
                    state.petItemNotices.add("胜负预言贴命中，" + displayName(player)
                            + " 成为唯一胜者，返还道具并获得 🦴" + reward + "。");
                } else {
                    state.petItemNotices.add("胜负预言贴命中，" + displayName(player)
                            + " 成为唯一胜者，返还道具；今日互动奖励额度已用完。");
                }
            } else {
                PetGameItemDeclarationService.settleFailed(room, playerKey, ITEM_PROPHECY, slot);
                state.petItemNotices.add("胜负预言贴未命中，" + displayName(player)
                        + " 未成为唯一胜者，道具已消耗。");
            }
            clearCarriedItem(player, slot);
        }
    }

    private static int prophecyRewardBones(int candidateCount) {
        if (candidateCount <= 2) {
            return 20;
        }
        if (candidateCount <= 4) {
            return 35;
        }
        return 50;
    }

    private static void refundUntriggeredPlayItems(GameRoom room, RoomState state) {
        for (GameRoom.Player player : room.getUsers().values()) {
            refundUntriggeredQuizItem(room, state, player, player.getPetPlayItemId(), SLOT_GAMEPLAY);
            refundUntriggeredQuizItem(room, state, player, player.getPetInteractionItemId(), SLOT_INTERACTION);
        }
    }

    private static void refundUntriggeredQuizItem(GameRoom room, RoomState state, GameRoom.Player player,
                                                  String itemId, String slot) {
        if (!ITEM_SCORE_PAD.equals(itemId) && !ITEM_WRONG_OPTION.equals(itemId)) {
            return;
        }
        PetGameItemDeclarationService.settleRefunded(room, player.getId(), itemId, slot);
        state.petItemNotices.add(itemName(itemId) + "未触发，" + displayName(player) + " 的道具已返还。");
        clearCarriedItem(player, slot);
    }

    private static String carriedItemSlot(GameRoom.Player player, String itemId) {
        if (player == null || itemId == null) {
            return null;
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

    private static String itemName(String itemId) {
        if (ITEM_SCORE_PAD.equals(itemId)) {
            return "护分爪垫";
        }
        if (ITEM_WRONG_OPTION.equals(itemId)) {
            return "错项嗅探";
        }
        return "道具";
    }

    private static String displayName(User user) {
        if (user == null) {
            return "玩家";
        }
        if (StrUtil.isNotBlank(user.getNickname())) {
            return user.getNickname();
        }
        if (StrUtil.isNotBlank(user.getUsername())) {
            return user.getUsername();
        }
        return "玩家" + user.getAccountId();
    }

    private static String displayName(GameRoom.Player player) {
        if (player == null) {
            return "玩家";
        }
        if (StrUtil.isNotBlank(player.getNickname())) {
            return player.getNickname();
        }
        if (StrUtil.isNotBlank(player.getUsername())) {
            return player.getUsername();
        }
        return "玩家" + player.getAccountId();
    }

    private static List<QuickQuizPlayerScoreDTO> rankings(RoomState state, boolean markWinners) {
        int best = Integer.MIN_VALUE;
        for (Integer score : state.scores.values()) {
            best = Math.max(best, score == null ? 0 : score);
        }
        List<QuickQuizPlayerScoreDTO> rankings = new ArrayList<>();
        for (User player : state.players) {
            String key = playerKey(player);
            int score = state.scores.getOrDefault(key, 0);
            rankings.add(new QuickQuizPlayerScoreDTO(key, player.getUsername(), score, markWinners && score == best, 0));
        }
        rankings.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));
        return rankings;
    }

    private static void saveAnswers(GameRoom room, QuickQuizQuestionDTO question, List<QuickQuizAnswerViewDTO> answers) {
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            QuickQuizMapper mapper = session.getMapper(QuickQuizMapper.class);
            for (QuickQuizAnswerViewDTO answer : answers) {
                mapper.insertRecord(QuickQuizRecord.builder()
                        .roomId(room.getId())
                        .questionId(question.getId())
                        .playerKey(answer.getPlayerKey())
                        .username(answer.getUsername())
                        .choiceIndex(answer.getChoiceIndex())
                        .choiceText(answer.getChoiceText())
                        .correct(answer.isCorrect() ? 1 : 0)
                        .pointsDelta(answer.getPointsDelta())
                        .totalScore(answer.getTotalScore())
                        .createdAt(answer.getAnsweredAt())
                        .build());
            }
            session.commit();
        } catch (Exception e) {
            log.error("保存快问快答答题记录失败", e);
        }
    }

    private static QuickQuizQuestion randomAvailableQuestion(List<Long> usedQuestionIds) {
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            return session.getMapper(QuickQuizMapper.class).randomAvailableQuestion(usedQuestionIds);
        }
    }

    private static void sendToRoom(GameRoom room, Response response) {
        room.getUsers().forEach((k, v) -> {
            User player = UserCache.get(v.getChannelId());
            if (player != null) {
                player.send(response);
            }
        });
    }

    private static List<User> getRoomUsers(GameRoom room) {
        List<User> players = new ArrayList<>();
        room.getUsers().forEach((k, v) -> {
            User user = UserCache.get(v.getChannelId());
            if (user != null) {
                players.add(user);
            }
        });
        return players;
    }

    private static QuickQuizQuestionDTO toQuestionDTO(QuickQuizQuestion row, boolean includeAnswer) {
        return new QuickQuizQuestionDTO(
                row.getId(),
                row.getQuestion(),
                JSONUtil.toList(row.getOptionsJson(), String.class),
                includeAnswer ? row.getCorrectAnswerIndex() : -1,
                Math.max(1, row.getScore()),
                0,
                0,
                0,
                0);
    }

    private static QuickQuizQuestionDTO copyQuestion(QuickQuizQuestionDTO question, boolean includeAnswer) {
        QuickQuizQuestionDTO copy = new QuickQuizQuestionDTO(question.getId(), question.getQuestion(), question.getOptions(),
                includeAnswer ? question.getCorrectAnswerIndex() : -1, question.getScore(),
                question.getStartedAt(), question.getDeadlineAt(), question.getRoundNo(), question.getTotalRounds());
        copy.setPetItemNotice(question.getPetItemNotice());
        copy.setPetItemDisabledOptionIndex(question.getPetItemDisabledOptionIndex());
        return copy;
    }

    private static List<QuickQuizQuestionDTO> normalizeQuestions(List<QuickQuizQuestionDTO> questions) {
        Map<String, QuickQuizQuestionDTO> map = new LinkedHashMap<>();
        if (questions == null) {
            return new ArrayList<>();
        }
        for (QuickQuizQuestionDTO item : questions) {
            if (item == null || StrUtil.isBlank(item.getQuestion()) || item.getOptions() == null) {
                continue;
            }
            String question = item.getQuestion().trim();
            if (map.containsKey(question)) {
                continue;
            }
            List<String> options = new ArrayList<>();
            for (String option : item.getOptions()) {
                if (StrUtil.isBlank(option)) {
                    continue;
                }
                String text = option.trim();
                if (!options.contains(text)) {
                    options.add(text);
                }
            }
            int correctAnswerIndex = item.getCorrectAnswerIndex();
            if (options.size() < 2 || options.size() > 4 || correctAnswerIndex < 0 || correctAnswerIndex >= options.size()) {
                continue;
            }
            map.put(question, new QuickQuizQuestionDTO(0, question, options, correctAnswerIndex,
                    Math.max(1, item.getScore()), 0, 0, 0, 0));
        }
        return new ArrayList<>(map.values());
    }

    private static List<QuickQuizRecordDTO> toRecordDTOs(List<QuickQuizRecord> rows) {
        return toRecordDTOs(rows, null);
    }

    static List<QuickQuizRecordDTO> toRecordDTOs(List<QuickQuizRecord> rows, String currentPlayerKey) {
        Map<String, QuickQuizRecordDTO> map = new LinkedHashMap<>();
        for (QuickQuizRecord row : rows) {
            String key = row.getRoomId() + "#" + row.getQuestionId();
            QuickQuizRecordDTO dto = map.get(key);
            if (dto == null) {
                dto = new QuickQuizRecordDTO(
                        row.getRoomId(),
                        row.getQuestionId(),
                        row.getQuestion(),
                        JSONUtil.toList(row.getOptionsJson(), String.class),
                        row.getCreatedAt(),
                        new ArrayList<>(),
                        null,
                        null);
                map.put(key, dto);
            }
            QuickQuizAnswerViewDTO answer = new QuickQuizAnswerViewDTO(
                    row.getPlayerKey(),
                    row.getUsername(),
                    row.getChoiceIndex(),
                    row.getChoiceText(),
                    row.getCreatedAt(),
                    row.getCorrect() > 0,
                    row.getChoiceIndex() < 0,
                    row.getPointsDelta(),
                    row.getTotalScore());
            dto.getAnswers().add(answer);
            if (currentPlayerKey != null && !currentPlayerKey.equals(answer.getPlayerKey())) {
                dto.setOpponentKey(answer.getPlayerKey());
                dto.setOpponentName(answer.getUsername());
            }
        }
        return new ArrayList<>(map.values());
    }

    interface QuestionPicker {
        QuickQuizQuestion pick(List<Long> usedQuestionIds);
    }

    interface Economy {
        void change(long accountId, int delta);
    }

    private static long now() {
        return nowSupplier.getAsLong();
    }

    private static class RoomState {
        private final String roomId;
        private final Set<Long> usedQuestionIds = new HashSet<>();
        private final Map<String, QuickQuizAnswerViewDTO> answers = new ConcurrentHashMap<>();
        private final Map<String, Integer> scores = new HashMap<>();
        private final Map<String, Long> accountIds = new HashMap<>();
        private final Map<Long, Integer> chargedEntryFees = new LinkedHashMap<>();
        private final Set<String> expectedPlayerKeys = new HashSet<>();
        private final List<User> players = new ArrayList<>();
        private final Map<String, Integer> disabledWrongOptionByPlayerKey = new HashMap<>();
        private final List<String> petItemNotices = new ArrayList<>();
        private QuickQuizQuestionDTO currentQuestion;
        private int roundNo;
        private int prizePool;
        private long matchStartedAt;
        private boolean economyApplied;
        private boolean rewardApplied;
        private boolean closed;

        private RoomState(String roomId) {
            this.roomId = roomId;
        }

        private void start(QuickQuizQuestionDTO question, List<User> users) {
            this.currentQuestion = question;
            if (this.matchStartedAt <= 0L) {
                this.matchStartedAt = question.getStartedAt();
            }
            this.answers.clear();
            this.expectedPlayerKeys.clear();
            this.players.clear();
            this.players.addAll(users);
            for (User user : users) {
                String key = playerKey(user);
                this.expectedPlayerKeys.add(key);
                this.scores.putIfAbsent(key, 0);
                this.accountIds.put(key, user.getAccountId());
            }
        }
    }

}
