package cn.xeblog.server.game.tacitquiz;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.xeblog.commons.entity.Response;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.game.tacitquiz.*;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.account.DbInitializer;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.cache.UserCache;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSession;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默契问答题库、轮次和答题记录。
 */
@Slf4j
public final class TacitQuizService {

    private static final Map<String, RoomState> ROOM_STATES = new ConcurrentHashMap<>();

    private TacitQuizService() {
    }

    public static List<TacitQuizQuestionDTO> listQuestions() {
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            List<TacitQuizQuestion> rows = session.getMapper(TacitQuizMapper.class).listActiveQuestions();
            List<TacitQuizQuestionDTO> result = new ArrayList<>(rows.size());
            for (TacitQuizQuestion row : rows) {
                result.add(toQuestionDTO(row));
            }
            return result;
        }
    }

    public static List<TacitQuizQuestionDTO> saveQuestions(List<TacitQuizQuestionDTO> questions) {
        List<TacitQuizQuestionDTO> normalized = normalizeQuestions(questions);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("默契问答题库不能为空");
        }

        long now = System.currentTimeMillis();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            TacitQuizMapper mapper = session.getMapper(TacitQuizMapper.class);
            mapper.deactivateAllQuestions();
            for (int i = 0; i < normalized.size(); i++) {
                TacitQuizQuestionDTO dto = normalized.get(i);
                mapper.upsertQuestion(TacitQuizQuestion.builder()
                        .question(dto.getQuestion())
                        .optionsJson(JSONUtil.toJsonStr(dto.getOptions()))
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
        List<User> players = getRoomUsers(room);
        if (players.size() < 2) {
            return totalActiveCount();
        }
        RoomState state = ROOM_STATES.get(room.getId());
        List<Long> usedQuestionIds = state == null ? Collections.emptyList() : new ArrayList<>(state.usedQuestionIds);
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            return session.getMapper(TacitQuizMapper.class).countAvailableQuestions(
                    playerKey(players.get(0)), playerKey(players.get(1)), usedQuestionIds);
        }
    }

    public static TacitQuizQuestionDTO nextQuestion(User user, GameRoom room) {
        if (!room.isHomeowner(user)) {
            throw new IllegalArgumentException("仅房主可以开始下一题");
        }
        List<User> players = getRoomUsers(room);
        if (players.size() < 2) {
            throw new IllegalArgumentException("需要双方都在房间内才能出题");
        }

        return nextQuestion(room, players, TacitQuizService::randomAvailableQuestion);
    }

    static TacitQuizQuestionDTO nextQuestion(GameRoom room, List<User> players, QuestionPicker questionPicker) {
        RoomState state = ROOM_STATES.computeIfAbsent(room.getId(), id -> new RoomState(room.getId()));
        synchronized (state) {
            if (state.currentQuestion != null && !state.revealed) {
                return state.currentQuestion;
            }
            if (state.roundNo >= room.getTacitQuizQuestionCount()) {
                throw new IllegalArgumentException("本局题目已经答完");
            }
            return startNextQuestion(room, state, players, questionPicker);
        }
    }

    public static void submitAnswer(User user, GameRoom room, TacitQuizSubmitAnswerDTO body) {
        submitAnswer(user, room, body, TacitQuizService::randomAvailableQuestion, TacitQuizService::saveAnswers);
    }

    static void submitAnswer(User user, GameRoom room, TacitQuizSubmitAnswerDTO body, QuestionPicker questionPicker) {
        submitAnswer(user, room, body, questionPicker, TacitQuizService::saveAnswers);
    }

    static void submitAnswer(User user, GameRoom room, TacitQuizSubmitAnswerDTO body, QuestionPicker questionPicker,
                             AnswerRecorder answerRecorder) {
        RoomState state = ROOM_STATES.get(room.getId());
        if (state == null) {
            throw new IllegalArgumentException("当前没有可提交的题目");
        }
        synchronized (state) {
            if (state.closed || state.currentQuestion == null || state.revealed) {
                throw new IllegalArgumentException("当前没有可提交的题目");
            }
            if (body.getQuestionId() != state.currentQuestion.getId()) {
                throw new IllegalArgumentException("题目已变化，请等待下一题");
            }
            String key = playerKey(user);
            if (!state.expectedPlayerKeys.contains(key)) {
                throw new IllegalArgumentException("你不在当前答题房间内");
            }
            int choiceIndex = body.getChoiceIndex();
            List<String> options = state.currentQuestion.getOptions();
            if (choiceIndex < 0 || choiceIndex >= options.size()) {
                throw new IllegalArgumentException("请选择有效答案");
            }
            state.answers.putIfAbsent(key, new TacitQuizAnswerViewDTO(
                    key, user.getUsername(), choiceIndex, options.get(choiceIndex), System.currentTimeMillis()));
            if (state.answers.size() >= state.expectedPlayerKeys.size()) {
                revealIfNeededLocked(room, state, body.getQuestionId(), questionPicker, answerRecorder);
            }
        }
    }

    public static List<TacitQuizRecordDTO> myRecords(User user) {
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            String currentPlayerKey = playerKey(user);
            return toRecordDTOs(session.getMapper(TacitQuizMapper.class).listRecordsByPlayer(currentPlayerKey), currentPlayerKey);
        }
    }

    public static List<TacitQuizRecordDTO> allRecords() {
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            return toRecordDTOs(session.getMapper(TacitQuizMapper.class).listAllRecords());
        }
    }

    public static void clearRoom(String roomId) {
        RoomState state = ROOM_STATES.remove(roomId);
        if (state != null) {
            synchronized (state) {
                state.closed = true;
            }
        }
    }

    public static void clearRoom(GameRoom room) {
        clearRoom(room, TacitQuizService::saveAnswers);
    }

    static void clearRoom(GameRoom room, AnswerRecorder answerRecorder) {
        if (room == null) {
            return;
        }
        RoomState state = ROOM_STATES.get(room.getId());
        if (state == null) {
            return;
        }
        synchronized (state) {
            if (!state.closed && state.currentQuestion != null && !state.revealed) {
                answerRecorder.save(room, state.currentQuestion, fillMissingAnswers(state));
            }
            state.closed = true;
            ROOM_STATES.remove(room.getId(), state);
        }
    }

    public static String playerKey(User user) {
        return user.getIdentityKey();
    }

    static boolean shouldExcludeQuestion(List<TacitQuizRecord> recordsForQuestion) {
        if (recordsForQuestion == null || recordsForQuestion.size() < 2) {
            return false;
        }
        Set<String> answeredPlayerKeys = new HashSet<>();
        for (TacitQuizRecord record : recordsForQuestion) {
            if (record != null && record.getChoiceIndex() >= 0) {
                answeredPlayerKeys.add(record.getPlayerKey());
            }
        }
        return answeredPlayerKeys.size() >= 2;
    }

    private static void revealIfNeeded(GameRoom room, RoomState state) {
        TacitQuizQuestionDTO currentQuestion = state.currentQuestion;
        if (currentQuestion == null) {
            return;
        }
        revealIfNeeded(room, state, currentQuestion.getId(), TacitQuizService::randomAvailableQuestion,
                TacitQuizService::saveAnswers);
    }

    private static void revealIfNeeded(GameRoom room, RoomState state, long expectedQuestionId,
                                       QuestionPicker questionPicker, AnswerRecorder answerRecorder) {
        synchronized (state) {
            revealIfNeededLocked(room, state, expectedQuestionId, questionPicker, answerRecorder);
        }
    }

    private static void revealIfNeededLocked(GameRoom room, RoomState state, long expectedQuestionId,
                                             QuestionPicker questionPicker, AnswerRecorder answerRecorder) {
        if (state.closed || state.revealed || state.currentQuestion == null
                || !Objects.equals(state.currentQuestion.getId(), expectedQuestionId)) {
            return;
        }
        long now = System.currentTimeMillis();
        List<TacitQuizAnswerViewDTO> answers = new ArrayList<>();
        for (User player : state.players) {
            String key = playerKey(player);
            TacitQuizAnswerViewDTO answer = state.answers.get(key);
            if (answer == null) {
                answer = new TacitQuizAnswerViewDTO(key, player.getUsername(), -1, "未作答", now);
                state.answers.put(key, answer);
            }
            answers.add(answer);
        }

        answerRecorder.save(room, state.currentQuestion, answers);

        state.revealed = true;
        if (shouldExcludeAnswers(answers)) {
            state.usedQuestionIds.add(state.currentQuestion.getId());
        }
        state.roundNo++;
        boolean finished = state.roundNo >= room.getTacitQuizQuestionCount();
        List<User> nextPlayers = Collections.emptyList();
        if (!finished) {
            nextPlayers = getRoomUsers(room);
            finished = nextPlayers.size() < 2;
        }
        TacitQuizAnswerResultDTO result = new TacitQuizAnswerResultDTO(
                room.getId(), state.currentQuestion, answers, state.roundNo,
                room.getTacitQuizQuestionCount(), finished);
        sendToRoom(room, ResponseBuilder.build(null, result, MessageType.TACIT_QUIZ_ANSWER_RESULT));
        if (finished) {
            state.closed = true;
            ROOM_STATES.remove(room.getId(), state);
        } else {
            startNextQuestion(room, state, nextPlayers, questionPicker);
        }
    }

    private static List<TacitQuizAnswerViewDTO> fillMissingAnswers(RoomState state) {
        long now = System.currentTimeMillis();
        List<TacitQuizAnswerViewDTO> answers = new ArrayList<>();
        for (User player : state.players) {
            String key = playerKey(player);
            TacitQuizAnswerViewDTO answer = state.answers.get(key);
            if (answer == null) {
                answer = new TacitQuizAnswerViewDTO(key, player.getUsername(), -1, "未作答", now);
            }
            answers.add(answer);
        }
        return answers;
    }

    private static void saveAnswers(GameRoom room, TacitQuizQuestionDTO question, List<TacitQuizAnswerViewDTO> answers) {
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            TacitQuizMapper mapper = session.getMapper(TacitQuizMapper.class);
            for (TacitQuizAnswerViewDTO answer : answers) {
                mapper.insertRecord(TacitQuizRecord.builder()
                        .roomId(room.getId())
                        .questionId(question.getId())
                        .playerKey(answer.getPlayerKey())
                        .username(answer.getUsername())
                        .choiceIndex(answer.getChoiceIndex())
                        .choiceText(answer.getChoiceText())
                        .createdAt(answer.getAnsweredAt())
                        .build());
            }
            session.commit();
        } catch (Exception e) {
            log.error("保存默契问答答题记录失败", e);
        }
    }

    private static TacitQuizQuestionDTO startNextQuestion(GameRoom room, RoomState state, List<User> players,
                                                          QuestionPicker questionPicker) {
        TacitQuizQuestion question = questionPicker.pick(
                playerKey(players.get(0)),
                playerKey(players.get(1)),
                new ArrayList<>(state.usedQuestionIds));
        if (question == null) {
            throw new IllegalArgumentException("剩余可用题数不足");
        }

        long startedAt = System.currentTimeMillis();
        TacitQuizQuestionDTO dto = toQuestionDTO(question);
        dto.setStartedAt(startedAt);
        dto.setDeadlineAt(0);
        dto.setRoundNo(state.roundNo + 1);
        dto.setTotalRounds(room.getTacitQuizQuestionCount());

        state.start(dto, players);
        sendToRoom(room, ResponseBuilder.build(null, dto, MessageType.TACIT_QUIZ_QUESTION));
        return dto;
    }

    private static TacitQuizQuestion randomAvailableQuestion(String playerAKey, String playerBKey,
                                                             List<Long> usedQuestionIds) {
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            return session.getMapper(TacitQuizMapper.class).randomAvailableQuestion(
                    playerAKey, playerBKey, usedQuestionIds);
        }
    }

    private static boolean shouldExcludeAnswers(List<TacitQuizAnswerViewDTO> answers) {
        if (answers == null || answers.size() < 2) {
            return false;
        }
        Set<String> answeredPlayerKeys = new HashSet<>();
        for (TacitQuizAnswerViewDTO answer : answers) {
            if (answer != null && answer.getChoiceIndex() >= 0) {
                answeredPlayerKeys.add(answer.getPlayerKey());
            }
        }
        return answeredPlayerKeys.size() >= 2;
    }

    private static void sendToRoom(GameRoom room, Response response) {
        room.getUsers().forEach((k, v) -> {
            User player = UserCache.get(v.getChannelId());
            if (player != null) {
                player.send(response);
            }
        });
    }

    private static int totalActiveCount() {
        return listQuestions().size();
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

    private static TacitQuizQuestionDTO toQuestionDTO(TacitQuizQuestion row) {
        return new TacitQuizQuestionDTO(
                row.getId(),
                row.getQuestion(),
                JSONUtil.toList(row.getOptionsJson(), String.class),
                0,
                0,
                0,
                0);
    }

    private static List<TacitQuizQuestionDTO> normalizeQuestions(List<TacitQuizQuestionDTO> questions) {
        Map<String, TacitQuizQuestionDTO> map = new LinkedHashMap<>();
        if (questions == null) {
            return new ArrayList<>();
        }
        for (TacitQuizQuestionDTO item : questions) {
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
            if (options.size() < 2) {
                continue;
            }
            map.put(question, new TacitQuizQuestionDTO(0, question, options, 0, 0, 0, 0));
        }
        return new ArrayList<>(map.values());
    }

    private static List<TacitQuizRecordDTO> toRecordDTOs(List<TacitQuizRecord> rows) {
        return toRecordDTOs(rows, null);
    }

    static List<TacitQuizRecordDTO> toRecordDTOs(List<TacitQuizRecord> rows, String currentPlayerKey) {
        Map<String, TacitQuizRecordDTO> map = new LinkedHashMap<>();
        for (TacitQuizRecord row : rows) {
            String key = row.getRoomId() + "#" + row.getQuestionId();
            TacitQuizRecordDTO dto = map.get(key);
            if (dto == null) {
                dto = new TacitQuizRecordDTO(
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
            TacitQuizAnswerViewDTO answer = new TacitQuizAnswerViewDTO(
                    row.getPlayerKey(),
                    row.getUsername(),
                    row.getChoiceIndex(),
                    row.getChoiceText(),
                    row.getCreatedAt());
            dto.getAnswers().add(answer);
            if (currentPlayerKey != null && !currentPlayerKey.equals(answer.getPlayerKey())) {
                dto.setOpponentKey(answer.getPlayerKey());
                dto.setOpponentName(answer.getUsername());
            }
        }
        return new ArrayList<>(map.values());
    }

    static int answerCount(String roomId) {
        RoomState state = ROOM_STATES.get(roomId);
        if (state == null) {
            return 0;
        }
        synchronized (state) {
            return state.answers.size();
        }
    }

    static int roundNo(String roomId) {
        RoomState state = ROOM_STATES.get(roomId);
        if (state == null) {
            return 0;
        }
        synchronized (state) {
            return state.roundNo;
        }
    }

    interface QuestionPicker {
        TacitQuizQuestion pick(String playerAKey, String playerBKey, List<Long> usedQuestionIds);
    }

    interface AnswerRecorder {
        void save(GameRoom room, TacitQuizQuestionDTO question, List<TacitQuizAnswerViewDTO> answers);
    }

    private static class RoomState {
        private final String roomId;
        private final Set<Long> usedQuestionIds = new HashSet<>();
        private final Map<String, TacitQuizAnswerViewDTO> answers = new ConcurrentHashMap<>();
        private final Set<String> expectedPlayerKeys = new HashSet<>();
        private final List<User> players = new ArrayList<>();
        private TacitQuizQuestionDTO currentQuestion;
        private int roundNo;
        private boolean revealed;
        private boolean closed;

        private RoomState(String roomId) {
            this.roomId = roomId;
        }

        private void start(TacitQuizQuestionDTO question, List<User> users) {
            this.currentQuestion = question;
            this.answers.clear();
            this.expectedPlayerKeys.clear();
            this.players.clear();
            this.players.addAll(users);
            for (User user : users) {
                this.expectedPlayerKeys.add(playerKey(user));
            }
            this.revealed = false;
        }
    }

}
