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
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSession;

import java.util.*;
import java.util.concurrent.*;

/**
 * 快问快答题库、轮次和答题记录。
 */
@Slf4j
public final class QuickQuizService {

    private static final int ANSWER_SECONDS = 10;
    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "quick-quiz-timer");
        t.setDaemon(true);
        return t;
    });
    private static final Map<String, RoomState> ROOM_STATES = new ConcurrentHashMap<>();

    private QuickQuizService() {
    }

    public static List<QuickQuizQuestionDTO> listQuestions() {
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            List<QuickQuizQuestion> rows = session.getMapper(QuickQuizMapper.class).listActiveQuestions();
            List<QuickQuizQuestionDTO> result = new ArrayList<>(rows.size());
            for (QuickQuizQuestion row : rows) {
                result.add(toQuestionDTO(row));
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
            return session.getMapper(QuickQuizMapper.class).countAvailableQuestions(
                    playerKey(players.get(0)), playerKey(players.get(1)), usedQuestionIds);
        }
    }

    public static QuickQuizQuestionDTO nextQuestion(User user, GameRoom room) {
        if (!room.isHomeowner(user.getUsername())) {
            throw new IllegalArgumentException("仅房主可以开始下一题");
        }
        List<User> players = getRoomUsers(room);
        if (players.size() < 2) {
            throw new IllegalArgumentException("需要双方都在房间内才能出题");
        }

        RoomState state = ROOM_STATES.computeIfAbsent(room.getId(), id -> new RoomState(room.getId()));
        if (state.roundNo >= room.getQuickQuizQuestionCount()) {
            throw new IllegalArgumentException("本局题目已经答完");
        }

        QuickQuizQuestion question;
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            question = session.getMapper(QuickQuizMapper.class).randomAvailableQuestion(
                    playerKey(players.get(0)),
                    playerKey(players.get(1)),
                    new ArrayList<>(state.usedQuestionIds));
        }
        if (question == null) {
            throw new IllegalArgumentException("剩余可用题数不足");
        }

        long startedAt = System.currentTimeMillis();
        QuickQuizQuestionDTO dto = toQuestionDTO(question);
        dto.setStartedAt(startedAt);
        dto.setDeadlineAt(startedAt + ANSWER_SECONDS * 1000L);
        dto.setRoundNo(state.roundNo + 1);
        dto.setTotalRounds(room.getQuickQuizQuestionCount());

        state.start(dto, players);
        TIMER.schedule(() -> revealIfNeeded(room, state), ANSWER_SECONDS, TimeUnit.SECONDS);
        sendToRoom(room, ResponseBuilder.build(null, dto, MessageType.QUICK_QUIZ_QUESTION));
        return dto;
    }

    public static void submitAnswer(User user, GameRoom room, QuickQuizSubmitAnswerDTO body) {
        RoomState state = ROOM_STATES.get(room.getId());
        if (state == null || state.currentQuestion == null || state.revealed) {
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
        state.answers.putIfAbsent(key, new QuickQuizAnswerViewDTO(
                key, user.getUsername(), choiceIndex, options.get(choiceIndex), System.currentTimeMillis()));
        if (state.answers.size() >= state.expectedPlayerKeys.size()) {
            revealIfNeeded(room, state);
        }
    }

    public static List<QuickQuizRecordDTO> myRecords(User user) {
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            return toRecordDTOs(session.getMapper(QuickQuizMapper.class).listRecordsByPlayer(playerKey(user)));
        }
    }

    public static List<QuickQuizRecordDTO> allRecords() {
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            return toRecordDTOs(session.getMapper(QuickQuizMapper.class).listAllRecords());
        }
    }

    public static void clearRoom(String roomId) {
        ROOM_STATES.remove(roomId);
    }

    public static String playerKey(User user) {
        return user.getAccountId() > 0 ? String.valueOf(user.getAccountId()) : user.getId();
    }

    private static void revealIfNeeded(GameRoom room, RoomState state) {
        synchronized (state) {
            if (state.revealed || state.currentQuestion == null) {
                return;
            }
            long now = System.currentTimeMillis();
            List<QuickQuizAnswerViewDTO> answers = new ArrayList<>();
            for (User player : state.players) {
                String key = playerKey(player);
                QuickQuizAnswerViewDTO answer = state.answers.get(key);
                if (answer == null) {
                    answer = new QuickQuizAnswerViewDTO(key, player.getUsername(), -1, "未作答", now);
                    state.answers.put(key, answer);
                }
                answers.add(answer);
            }

            try (SqlSession session = DbInitializer.factory().openSession(false)) {
                QuickQuizMapper mapper = session.getMapper(QuickQuizMapper.class);
                for (QuickQuizAnswerViewDTO answer : answers) {
                    mapper.insertRecord(QuickQuizRecord.builder()
                            .roomId(room.getId())
                            .questionId(state.currentQuestion.getId())
                            .playerKey(answer.getPlayerKey())
                            .username(answer.getUsername())
                            .choiceIndex(answer.getChoiceIndex())
                            .choiceText(answer.getChoiceText())
                            .createdAt(answer.getAnsweredAt())
                            .build());
                }
                session.commit();
            } catch (Exception e) {
                log.error("保存快问快答答题记录失败", e);
            }

            state.revealed = true;
            state.usedQuestionIds.add(state.currentQuestion.getId());
            state.roundNo++;
            boolean finished = state.roundNo >= room.getQuickQuizQuestionCount();
            QuickQuizAnswerResultDTO result = new QuickQuizAnswerResultDTO(
                    room.getId(), state.currentQuestion, answers, state.roundNo,
                    room.getQuickQuizQuestionCount(), finished);
            sendToRoom(room, ResponseBuilder.build(null, result, MessageType.QUICK_QUIZ_ANSWER_RESULT));
            if (finished) {
                ROOM_STATES.remove(room.getId());
            }
        }
    }

    private static void sendToRoom(GameRoom room, Response response) {
        room.getUsers().forEach((k, v) -> {
            User player = UserCache.get(v.getId());
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
            User user = UserCache.get(v.getId());
            if (user != null) {
                players.add(user);
            }
        });
        return players;
    }

    private static QuickQuizQuestionDTO toQuestionDTO(QuickQuizQuestion row) {
        return new QuickQuizQuestionDTO(
                row.getId(),
                row.getQuestion(),
                JSONUtil.toList(row.getOptionsJson(), String.class),
                0,
                0,
                0,
                0);
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
            if (options.size() < 2) {
                continue;
            }
            map.put(question, new QuickQuizQuestionDTO(0, question, options, 0, 0, 0, 0));
        }
        return new ArrayList<>(map.values());
    }

    private static List<QuickQuizRecordDTO> toRecordDTOs(List<QuickQuizRecord> rows) {
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
                        new ArrayList<>());
                map.put(key, dto);
            }
            dto.getAnswers().add(new QuickQuizAnswerViewDTO(
                    row.getPlayerKey(),
                    row.getUsername(),
                    row.getChoiceIndex(),
                    row.getChoiceText(),
                    row.getCreatedAt()));
        }
        return new ArrayList<>(map.values());
    }

    private static class RoomState {
        private final String roomId;
        private final Set<Long> usedQuestionIds = new HashSet<>();
        private final Map<String, QuickQuizAnswerViewDTO> answers = new ConcurrentHashMap<>();
        private final Set<String> expectedPlayerKeys = new HashSet<>();
        private final List<User> players = new ArrayList<>();
        private QuickQuizQuestionDTO currentQuestion;
        private int roundNo;
        private boolean revealed;

        private RoomState(String roomId) {
            this.roomId = roomId;
        }

        private synchronized void start(QuickQuizQuestionDTO question, List<User> users) {
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
