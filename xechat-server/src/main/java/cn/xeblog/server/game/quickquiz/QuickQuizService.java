package cn.xeblog.server.game.quickquiz;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.xeblog.commons.entity.Response;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.game.quickquiz.*;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.account.DbInitializer;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.cache.UserCache;
import cn.xeblog.server.pet.PetService;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSession;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 快问快答知识题、轮次计分和报名奖池。
 */
@Slf4j
public final class QuickQuizService {

    private static final int MAX_PLAYERS = 8;

    private static final Map<String, RoomState> ROOM_STATES = new ConcurrentHashMap<>();

    private static Economy economy = PetService::changeBones;

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

        long now = System.currentTimeMillis();
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
            long now = System.currentTimeMillis();
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
            long now = System.currentTimeMillis();
            if (now > state.currentQuestion.getDeadlineAt()) {
                return revealLocked(room, state, now);
            }
            QuickQuizAnswerViewDTO answer = buildAnswer(user, state.currentQuestion, body, now, state);
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
        RoomState state = ROOM_STATES.remove(roomId);
        if (state != null) {
            synchronized (state) {
                state.closed = true;
                state.prizePool = 0;
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

    private static QuickQuizAnswerViewDTO buildAnswer(User user, QuickQuizQuestionDTO question,
                                                       QuickQuizSubmitAnswerDTO body, long answeredAt,
                                                       RoomState state) {
        int choiceIndex = body.getChoiceIndex();
        boolean skipped = choiceIndex < 0;
        String choiceText = skipped ? "不作答" : optionText(question, choiceIndex);
        boolean correct = !skipped && choiceIndex == question.getCorrectAnswerIndex();
        int delta = skipped ? 0 : (correct ? question.getScore() : -wrongPenalty(question.getScore()));
        String key = playerKey(user);
        int totalScore = state.scores.getOrDefault(key, 0) + delta;
        state.scores.put(key, totalScore);
        return new QuickQuizAnswerViewDTO(key, user.getUsername(), choiceIndex, choiceText, answeredAt,
                correct, skipped, delta, totalScore);
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
                state.answers.put(key, buildAnswer(player, state.currentQuestion, skip, now, state));
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
        int rewardPerWinner = finished ? applyRewards(state, rankings) : 0;
        QuickQuizQuestionDTO resultQuestion = copyQuestion(state.currentQuestion, true);
        QuickQuizAnswerResultDTO result = new QuickQuizAnswerResultDTO(room.getId(), resultQuestion, answers, rankings,
                state.roundNo, room.getQuickQuizQuestionCount(), finished, state.prizePool, rewardPerWinner,
                finished && state.economyApplied);
        sendToRoom(room, ResponseBuilder.build(null, result,
                finished ? MessageType.QUICK_QUIZ_MATCH_RESULT : MessageType.QUICK_QUIZ_ROUND_RESULT));

        state.currentQuestion = null;
        state.answers.clear();
        state.expectedPlayerKeys.clear();
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
        long startedAt = System.currentTimeMillis();
        QuickQuizQuestionDTO dto = toQuestionDTO(question, true);
        dto.setStartedAt(startedAt);
        dto.setDeadlineAt(startedAt + resolveTimeLimitSeconds(room) * 1000L);
        dto.setRoundNo(state.roundNo + 1);
        dto.setTotalRounds(room.getQuickQuizQuestionCount());

        state.start(dto, players);
        QuickQuizQuestionDTO visibleQuestion = copyQuestion(dto, false);
        sendToRoom(room, ResponseBuilder.build(null, visibleQuestion, MessageType.QUICK_QUIZ_QUESTION));
        return visibleQuestion;
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
            }
        } catch (RuntimeException e) {
            for (User player : charged) {
                try {
                    economy.change(player.getAccountId(), entryFee);
                } catch (RuntimeException rollbackError) {
                    log.error("快问快答报名费回滚失败 -> accountId: {}", player.getAccountId(), rollbackError);
                }
            }
            throw e;
        }
        state.prizePool = entryFee * players.size();
        state.economyApplied = true;
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
        return reward;
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
        return new QuickQuizQuestionDTO(question.getId(), question.getQuestion(), question.getOptions(),
                includeAnswer ? question.getCorrectAnswerIndex() : -1, question.getScore(),
                question.getStartedAt(), question.getDeadlineAt(), question.getRoundNo(), question.getTotalRounds());
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

    private static class RoomState {
        private final String roomId;
        private final Set<Long> usedQuestionIds = new HashSet<>();
        private final Map<String, QuickQuizAnswerViewDTO> answers = new ConcurrentHashMap<>();
        private final Map<String, Integer> scores = new HashMap<>();
        private final Map<String, Long> accountIds = new HashMap<>();
        private final Set<String> expectedPlayerKeys = new HashSet<>();
        private final List<User> players = new ArrayList<>();
        private QuickQuizQuestionDTO currentQuestion;
        private int roundNo;
        private int prizePool;
        private boolean economyApplied;
        private boolean rewardApplied;
        private boolean closed;

        private RoomState(String roomId) {
            this.roomId = roomId;
        }

        private void start(QuickQuizQuestionDTO question, List<User> users) {
            this.currentQuestion = question;
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
