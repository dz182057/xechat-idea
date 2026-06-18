package cn.xeblog.server.game.tacitquiz;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.game.tacitquiz.TacitQuizAnswerViewDTO;
import cn.xeblog.commons.entity.game.tacitquiz.TacitQuizQuestionDTO;
import cn.xeblog.commons.entity.game.tacitquiz.TacitQuizRecordDTO;
import cn.xeblog.commons.entity.game.tacitquiz.TacitQuizSubmitAnswerDTO;
import cn.xeblog.server.cache.UserCache;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TacitQuizServiceTest {

    @Test
    public void staleSubmitShouldNotWriteOldAnswerIntoAutoStartedNextQuestion() throws Exception {
        String roomId = "quick-quiz-stale-submit-" + System.nanoTime();
        GameRoom room = new GameRoom();
        room.setId(roomId);
        room.setTacitQuizQuestionCount(2);
        List<User> players = Arrays.asList(
                user("channel-alice-stale", 1L, "Alice"),
                user("channel-bob-stale", 2L, "Bob")
        );
        room.getUsers().put(players.get(0).getIdentityKey(), new GameRoom.Player(players.get(0)));
        room.getUsers().put(players.get(1).getIdentityKey(), new GameRoom.Player(players.get(1)));
        UserCache.add(players.get(0).getId(), players.get(0));
        UserCache.add(players.get(1).getId(), players.get(1));

        try {
            TacitQuizQuestionDTO first = TacitQuizService.nextQuestion(
                    room, players, (playerAKey, playerBKey, usedQuestionIds) -> question(201L, "第一题"));
            PausingAnswerMap answers = new PausingAnswerMap(players.get(0).getIdentityKey());
            answers.put(players.get(0).getIdentityKey(), answer(players.get(0), 0, "A"));
            answers.put(players.get(1).getIdentityKey(), answer(players.get(1), 1, "B"));
            installAnswersMap(roomId, answers);
            TacitQuizService.QuestionPicker picker = (playerAKey, playerBKey, usedQuestionIds) ->
                    question(202L, "第二题");
            AtomicReference<Throwable> staleFailure = new AtomicReference<>();
            AtomicReference<Throwable> revealFailure = new AtomicReference<>();

            Thread staleSubmit = new Thread(() -> submitForTest(players.get(0), room, first, picker, staleFailure),
                    "quick-quiz-stale-submit");
            staleSubmit.start();
            assertTrue("旧题提交应先进入答案写入窗口", answers.awaitEntered());

            Thread reveal = new Thread(() -> submitForTest(players.get(1), room, first, picker, revealFailure),
                    "quick-quiz-reveal-submit");
            reveal.start();
            Thread.sleep(100);
            answers.release();
            staleSubmit.join(5000);
            reveal.join(5000);

            assertFalse("旧题提交线程应结束", staleSubmit.isAlive());
            assertFalse("触发揭示线程应结束", reveal.isAlive());
            if (staleFailure.get() != null) {
                throw new AssertionError("旧题重复提交不应污染新题，也不应抛异常", staleFailure.get());
            }
            if (revealFailure.get() != null && !(revealFailure.get() instanceof IllegalArgumentException)) {
                throw new AssertionError("触发揭示线程只允许因题目已变化而被拒绝", revealFailure.get());
            }
            TacitQuizQuestionDTO current = currentQuestion(roomId);
            assertEquals("旧题结算后应自动进入第二题", 202L, current.getId());
            assertEquals("旧题提交不得写入第二题答案状态", 0, TacitQuizService.answerCount(roomId));
        } finally {
            TacitQuizService.clearRoom(roomId);
            UserCache.clear();
        }
    }

    @Test
    public void clearRoomShouldCloseStateHeldByConcurrentSubmit() throws Exception {
        String roomId = "quick-quiz-clear-concurrent-" + System.nanoTime();
        GameRoom room = new GameRoom();
        room.setId(roomId);
        room.setTacitQuizQuestionCount(1);
        List<User> players = Arrays.asList(
                user("channel-alice-clear-concurrent", 1L, "Alice"),
                user("channel-bob-clear-concurrent", 2L, "Bob")
        );
        AtomicInteger saveCount = new AtomicInteger();

        try {
            TacitQuizQuestionDTO first = TacitQuizService.nextQuestion(
                    room, players, (playerAKey, playerBKey, usedQuestionIds) -> question(301L, "第一题"));
            PausingAnswerMap answers = new PausingAnswerMap(players.get(0).getIdentityKey());
            answers.put(players.get(1).getIdentityKey(), answer(players.get(1), 1, "B"));
            installAnswersMap(roomId, answers);
            TacitQuizService.AnswerRecorder recorder = (targetRoom, targetQuestion, savedAnswers) ->
                    saveCount.incrementAndGet();
            AtomicReference<Throwable> submitFailure = new AtomicReference<>();
            AtomicReference<Throwable> clearFailure = new AtomicReference<>();

            Thread submit = new Thread(() -> {
                try {
                    TacitQuizService.submitAnswer(players.get(0), room,
                            new TacitQuizSubmitAnswerDTO(roomId, first.getId(), 0, "A"),
                            (playerAKey, playerBKey, usedQuestionIds) -> question(302L, "第二题"),
                            recorder);
                } catch (Throwable e) {
                    submitFailure.compareAndSet(null, e);
                }
            }, "quick-quiz-submit-during-clear");
            submit.start();
            assertTrue("并发提交应先拿到清理前的状态引用", answers.awaitEntered());

            Thread clear = new Thread(() -> {
                try {
                    TacitQuizService.clearRoom(room, recorder);
                } catch (Throwable e) {
                    clearFailure.compareAndSet(null, e);
                }
            }, "quick-quiz-clear-room");
            clear.start();
            Thread.sleep(100);
            answers.release();
            submit.join(5000);
            clear.join(5000);

            assertFalse("并发提交线程应结束", submit.isAlive());
            assertFalse("清理线程应结束", clear.isAlive());
            if (submitFailure.get() != null) {
                throw new AssertionError("清理后的并发提交应被安全忽略", submitFailure.get());
            }
            if (clearFailure.get() != null) {
                throw new AssertionError("清理线程不应失败", clearFailure.get());
            }
            assertEquals("清理和并发提交不应二次保存同一题", 1, saveCount.get());
            assertNull("清理后应移除房间快问快答状态", roomState(roomId));
        } finally {
            TacitQuizService.clearRoom(roomId);
        }
    }

    @Test
    public void TacitQuizRecordDTOConstructorShouldKeepAnswersBeforeOpponentFields() {
        List<TacitQuizAnswerViewDTO> answers = Collections.singletonList(
                new TacitQuizAnswerViewDTO("alice", "Alice", 0, "A", 100L));

        TacitQuizRecordDTO dto = new TacitQuizRecordDTO(
                "room-1", 1L, "题目", Arrays.asList("A", "B"), 100L, answers, "bob", "Bob");

        assertEquals("answers 构造参数应保持在新增 opponent 字段之前", answers, dto.getAnswers());
        assertEquals("bob", dto.getOpponentKey());
        assertEquals("Bob", dto.getOpponentName());
    }

    @Test
    public void concurrentSubmittedAnswersShouldRevealOnlyOriginalQuestionOnce() throws Exception {
        String roomId = "quick-quiz-concurrent-" + System.nanoTime();
        GameRoom room = new GameRoom();
        room.setId(roomId);
        room.setTacitQuizQuestionCount(3);
        List<User> players = Arrays.asList(
                user("channel-alice-concurrent", 1L, "Alice"),
                user("channel-bob-concurrent", 2L, "Bob")
        );
        room.getUsers().put(players.get(0).getIdentityKey(), new GameRoom.Player(players.get(0)));
        room.getUsers().put(players.get(1).getIdentityKey(), new GameRoom.Player(players.get(1)));
        UserCache.add(players.get(0).getId(), players.get(0));
        UserCache.add(players.get(1).getId(), players.get(1));

        try {
            TacitQuizQuestionDTO first = TacitQuizService.nextQuestion(
                    room, players, (playerAKey, playerBKey, usedQuestionIds) -> question(101L, "第一题"));
            AtomicInteger nextQuestionId = new AtomicInteger(102);
            TacitQuizService.QuestionPicker picker = (playerAKey, playerBKey, usedQuestionIds) ->
                    question(nextQuestionId.getAndIncrement(), "自动下一题");
            AtomicReference<Throwable> failure = new AtomicReference<>();

            Thread alice = new Thread(() -> submitForTest(players.get(0), room, first, picker, failure),
                    "quick-quiz-alice-submit");
            Thread bob = new Thread(() -> submitForTest(players.get(1), room, first, picker, failure),
                    "quick-quiz-bob-submit");

            alice.start();
            bob.start();
            alice.join(5000);
            bob.join(5000);

            assertFalse("提交线程应在条件同步后结束", alice.isAlive());
            assertFalse("提交线程应在条件同步后结束", bob.isAlive());
            if (failure.get() != null) {
                throw new AssertionError("并发提交不应抛出异常", failure.get());
            }
            assertEquals("同一题双方答案并发到达时只应结算一次", 1, TacitQuizService.roundNo(roomId));
            assertEquals("自动下发的新题不应立刻被未作答结算", 0, TacitQuizService.answerCount(roomId));
            TacitQuizQuestionDTO current = currentQuestion(roomId);
            assertEquals("重复 reveal 不应跳过自动下发的新题", 102L, current.getId());
            assertEquals("新题轮次应只推进到第 2 题", 2, current.getRoundNo());
        } finally {
            TacitQuizService.clearRoom(roomId);
            UserCache.clear();
        }
    }

    @Test
    public void nextQuestionShouldReuseCurrentUnrevealedQuestionWithoutChangingState() {
        String roomId = "quick-quiz-repeat-" + System.nanoTime();
        GameRoom room = new GameRoom();
        room.setId(roomId);
        room.setTacitQuizQuestionCount(3);
        List<User> players = Arrays.asList(
                user("channel-alice", 1L, "Alice"),
                user("channel-bob", 2L, "Bob")
        );

        try {
            TacitQuizQuestionDTO first = TacitQuizService.nextQuestion(
                    room, players, (playerAKey, playerBKey, usedQuestionIds) -> question(11L, "第一题"));
            TacitQuizService.submitAnswer(players.get(0), room,
                    new TacitQuizSubmitAnswerDTO(roomId, first.getId(), 0, "A"));

            final boolean[] pickerCalled = {false};
            TacitQuizQuestionDTO repeated = TacitQuizService.nextQuestion(
                    room, players, (playerAKey, playerBKey, usedQuestionIds) -> {
                        pickerCalled[0] = true;
                        return question(22L, "第二题");
                    });

            assertEquals("已有未揭示题时应返回当前题", first.getId(), repeated.getId());
            assertEquals("已有未揭示题时不应覆盖题目内容", "第一题", repeated.getQuestion());
            assertFalse("已有未揭示题时不应查询下一题", pickerCalled[0]);
            assertEquals("重复 nextQuestion 不应清空已提交答案", 1, TacitQuizService.answerCount(roomId));
            assertEquals("重复 nextQuestion 不应推进轮次，否则后续结算会跳过当前记录", 0, TacitQuizService.roundNo(roomId));
        } finally {
            TacitQuizService.clearRoom(roomId);
        }
    }

    @Test
    public void clearRoomShouldSaveCurrentUnrevealedQuestionWithMissingAnswers() throws Exception {
        String roomId = "quick-quiz-clear-" + System.nanoTime();
        GameRoom room = new GameRoom();
        room.setId(roomId);
        room.setTacitQuizQuestionCount(3);
        List<User> players = Arrays.asList(
                user("channel-alice-clear", 1L, "Alice"),
                user("channel-bob-clear", 2L, "Bob")
        );
        AtomicReference<List<TacitQuizAnswerViewDTO>> savedAnswers = new AtomicReference<>();

        try {
            TacitQuizQuestionDTO first = TacitQuizService.nextQuestion(
                    room, players, (playerAKey, playerBKey, usedQuestionIds) -> question(31L, "第一题"));
            TacitQuizService.submitAnswer(players.get(0), room,
                    new TacitQuizSubmitAnswerDTO(roomId, first.getId(), 0, "A"));

            TacitQuizService.clearRoom(room, (targetRoom, targetQuestion, answers) -> savedAnswers.set(answers));

            assertEquals("清理未揭示题时应保存当前题双方记录", 2, savedAnswers.get().size());
            assertAnswer(savedAnswers.get(), players.get(0), 0, "A");
            assertAnswer(savedAnswers.get(), players.get(1), -1, "未作答");
            assertNull("清理后应移除房间快问快答状态", roomState(roomId));
        } finally {
            TacitQuizService.clearRoom(roomId);
        }
    }

    @Test
    public void submitAnswerShouldClearStateWhenAutoNextQuestionHasMissingPlayer() throws Exception {
        String roomId = "quick-quiz-offline-" + System.nanoTime();
        GameRoom room = new GameRoom();
        room.setId(roomId);
        room.setTacitQuizQuestionCount(3);
        List<User> players = Arrays.asList(
                user("channel-alice-offline", 1L, "Alice"),
                user("channel-bob-offline", 2L, "Bob")
        );
        room.getUsers().put(players.get(0).getIdentityKey(), new GameRoom.Player(players.get(0)));
        room.getUsers().put(players.get(1).getIdentityKey(), new GameRoom.Player(players.get(1)));
        UserCache.add(players.get(0).getId(), players.get(0));
        UserCache.add(players.get(1).getId(), players.get(1));

        try {
            TacitQuizQuestionDTO first = TacitQuizService.nextQuestion(
                    room, players, (playerAKey, playerBKey, usedQuestionIds) -> question(41L, "第一题"));
            TacitQuizService.submitAnswer(players.get(0), room,
                    new TacitQuizSubmitAnswerDTO(roomId, first.getId(), 0, "A"));
            UserCache.remove(players.get(1).getId());

            try {
                TacitQuizService.submitAnswer(players.get(1), room,
                        new TacitQuizSubmitAnswerDTO(roomId, first.getId(), 1, "B"),
                        (playerAKey, playerBKey, usedQuestionIds) -> question(42L, "第二题"),
                        (targetRoom, targetQuestion, answers) -> {
                        });
            } catch (RuntimeException e) {
                fail("自动下一题前玩家不足时不应抛异常: " + e.getMessage());
            }

            assertNull("自动下一题前玩家不足时应清理房间快问快答状态", roomState(roomId));
        } finally {
            TacitQuizService.clearRoom(roomId);
            UserCache.clear();
        }
    }

    @Test
    public void shouldExcludeQuestionOnlyWhenBothPlayersAnswered() {
        assertFalse("任一玩家未作答时，题目之后仍应可被抽出", TacitQuizService.shouldExcludeQuestion(Arrays.asList(
                record("alice", 0),
                record("bob", -1)
        )));

        assertTrue("双方都作答后，题目才算这对玩家已消耗", TacitQuizService.shouldExcludeQuestion(Arrays.asList(
                record("alice", 0),
                record("bob", 1)
        )));
    }

    @Test
    public void toRecordDTOsShouldFillOpponentForCurrentPlayer() {
        List<TacitQuizRecord> rows = Arrays.asList(
                record("room-1", 7L, "alice", "Alice", 0),
                record("room-1", 7L, "bob", "Bob", 1)
        );

        assertEquals("bob", TacitQuizService.toRecordDTOs(rows, "alice").get(0).getOpponentKey());
        assertEquals("Bob", TacitQuizService.toRecordDTOs(rows, "alice").get(0).getOpponentName());
    }

    private TacitQuizRecord record(String playerKey, int choiceIndex) {
        return TacitQuizRecord.builder()
                .playerKey(playerKey)
                .choiceIndex(choiceIndex)
                .build();
    }

    private TacitQuizRecord record(String roomId, long questionId, String playerKey, String username, int choiceIndex) {
        return TacitQuizRecord.builder()
                .roomId(roomId)
                .questionId(questionId)
                .playerKey(playerKey)
                .username(username)
                .choiceIndex(choiceIndex)
                .choiceText(String.valueOf(choiceIndex))
                .question("题目")
                .optionsJson("[\"A\",\"B\"]")
                .createdAt(100L + choiceIndex)
                .build();
    }

    private TacitQuizAnswerViewDTO answer(User user, int choiceIndex, String choiceText) {
        return new TacitQuizAnswerViewDTO(
                user.getIdentityKey(), user.getUsername(), choiceIndex, choiceText, System.currentTimeMillis());
    }

    private User user(String channelId, long accountId, String nickname) {
        User user = new User();
        user.setId(channelId);
        user.setAccountId(accountId);
        user.setNickname(nickname);
        return user;
    }

    private TacitQuizQuestion question(long id, String text) {
        return TacitQuizQuestion.builder()
                .id(id)
                .question(text)
                .optionsJson("[\"A\",\"B\"]")
                .build();
    }

    private void assertAnswer(List<TacitQuizAnswerViewDTO> answers, User user, int choiceIndex, String choiceText) {
        for (TacitQuizAnswerViewDTO answer : answers) {
            if (user.getIdentityKey().equals(answer.getPlayerKey())) {
                assertEquals("答案选项序号不符合预期", choiceIndex, answer.getChoiceIndex());
                assertEquals("答案文本不符合预期", choiceText, answer.getChoiceText());
                return;
            }
        }
        fail("未保存玩家答案: " + user.getUsername());
    }

    private void submitForTest(User user, GameRoom room, TacitQuizQuestionDTO question,
                               TacitQuizService.QuestionPicker picker, AtomicReference<Throwable> failure) {
        try {
            TacitQuizService.submitAnswer(user, room,
                    new TacitQuizSubmitAnswerDTO(room.getId(), question.getId(), 0, "A"), picker,
                    (targetRoom, targetQuestion, answers) -> {
                    });
        } catch (Throwable e) {
            failure.compareAndSet(null, e);
        }
    }

    @SuppressWarnings("unchecked")
    private void installAnswersMap(String roomId, Map<String, TacitQuizAnswerViewDTO> answers) throws Exception {
        Object state = roomState(roomId);
        Field field = state.getClass().getDeclaredField("answers");
        field.setAccessible(true);
        field.set(state, answers);
    }

    private TacitQuizQuestionDTO currentQuestion(String roomId) throws Exception {
        Object state = roomState(roomId);
        Field field = state.getClass().getDeclaredField("currentQuestion");
        field.setAccessible(true);
        return (TacitQuizQuestionDTO) field.get(state);
    }

    @SuppressWarnings("unchecked")
    private Object roomState(String roomId) throws Exception {
        Field field = TacitQuizService.class.getDeclaredField("ROOM_STATES");
        field.setAccessible(true);
        Map<String, ?> states = (Map<String, ?>) field.get(null);
        return states.get(roomId);
    }

    private static class PausingAnswerMap extends ConcurrentHashMap<String, TacitQuizAnswerViewDTO> {
        private final String pauseKey;
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private boolean paused;

        private PausingAnswerMap(String pauseKey) {
            this.pauseKey = pauseKey;
        }

        @Override
        public TacitQuizAnswerViewDTO putIfAbsent(String key, TacitQuizAnswerViewDTO value) {
            if (pauseKey.equals(key) && !paused) {
                paused = true;
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("等待释放答案写入超时");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("等待释放答案写入被中断", e);
                }
            }
            return super.putIfAbsent(key, value);
        }

        private boolean awaitEntered() throws InterruptedException {
            return entered.await(5, TimeUnit.SECONDS);
        }

        private void release() {
            release.countDown();
        }
    }
}

