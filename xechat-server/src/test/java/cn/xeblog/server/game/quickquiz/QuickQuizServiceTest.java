package cn.xeblog.server.game.quickquiz;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.game.quickquiz.QuickQuizAnswerResultDTO;
import cn.xeblog.commons.entity.game.quickquiz.QuickQuizQuestionDTO;
import cn.xeblog.commons.entity.game.quickquiz.QuickQuizSubmitAnswerDTO;
import cn.xeblog.commons.enums.Game;
import cn.xeblog.server.cache.UserCache;
import cn.xeblog.server.pet.MiniGameRewards;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class QuickQuizServiceTest {

    private final List<String> economyEvents = new ArrayList<>();
    private final List<String> miniGameEvents = new ArrayList<>();
    private final AtomicInteger nowSeconds = new AtomicInteger(1_000);

    @Before
    public void setUp() {
        QuickQuizService.setEconomyForTest((accountId, delta) -> economyEvents.add(accountId + ":" + delta));
        QuickQuizService.setMiniGameRewardsForTest((accountId, game, win, durationSeconds) ->
                miniGameEvents.add(accountId + ":" + game + ":" + win + ":" + durationSeconds));
        QuickQuizService.setNowSupplierForTest(() -> nowSeconds.get() * 1000L);
    }

    @After
    public void tearDown() {
        QuickQuizService.clearRoom("quick-quiz-test");
        QuickQuizService.resetEconomy();
        QuickQuizService.resetMiniGameRewards();
        QuickQuizService.resetNowSupplier();
        UserCache.clear();
    }

    @Test
    public void nextQuestionShouldSupportTwoToEightPlayersAndChargeEntryPoolOnce() {
        GameRoom room = room(3, 5, 10);
        List<User> players = players(3);
        join(room, players);

        QuickQuizQuestionDTO question = QuickQuizService.nextQuestion(
                players.get(0), room, (usedQuestionIds) -> question(101L, "2 + 2 = ?", 1, 10));
        QuickQuizQuestionDTO repeated = QuickQuizService.nextQuestion(
                players.get(0), room, (usedQuestionIds) -> question(102L, "不会被抽到", 0, 10));

        assertEquals("题目下发时不应泄露正确答案", -1, question.getCorrectAnswerIndex());
        assertEquals("限时应来自房间配置", 10_000L, question.getDeadlineAt() - question.getStartedAt());
        assertEquals("重复开始未结束题目时应复用当前题", question.getId(), repeated.getId());
        assertEquals("报名费应形成奖池", 15, QuickQuizService.poolOf(room.getId()));
        assertEquals(Arrays.asList("1:-5", "2:-5", "3:-5"), economyEvents);
    }

    @Test
    public void submitAnswerShouldScoreCorrectWrongAndSkipWithoutPenalty() {
        GameRoom room = room(3, 0, 20);
        List<User> players = players(3);
        join(room, players);
        QuickQuizQuestionDTO question = QuickQuizService.nextQuestion(
                players.get(0), room, (usedQuestionIds) -> question(201L, "首都?", 1, 5));

        assertNull(QuickQuizService.submitAnswer(players.get(0), room,
                new QuickQuizSubmitAnswerDTO(room.getId(), question.getId(), 1, "北京")));
        assertNull(QuickQuizService.submitAnswer(players.get(1), room,
                new QuickQuizSubmitAnswerDTO(room.getId(), question.getId(), 0, "上海")));
        QuickQuizAnswerResultDTO result = QuickQuizService.submitAnswer(players.get(2), room,
                new QuickQuizSubmitAnswerDTO(room.getId(), question.getId(), -1, "不作答"));

        assertEquals(5, result.getAnswers().get(0).getPointsDelta());
        assertEquals(-2, result.getAnswers().get(1).getPointsDelta());
        assertEquals(0, result.getAnswers().get(2).getPointsDelta());
        assertEquals(5, result.getRankings().get(0).getScore());
        assertEquals(0, result.getRankings().get(1).getScore());
        assertEquals(-2, result.getRankings().get(2).getScore());
        assertFalse(result.isFinished());
    }

    @Test
    public void finalResultShouldSplitPrizeByCeilingForTiedWinners() {
        GameRoom room = room(3, 5, 120);
        room.setQuickQuizQuestionCount(1);
        List<User> players = players(3);
        join(room, players);
        QuickQuizQuestionDTO question = QuickQuizService.nextQuestion(
                players.get(0), room, (usedQuestionIds) -> question(301L, "1 + 1 = ?", 0, 10));
        nowSeconds.addAndGet(61);

        QuickQuizService.submitAnswer(players.get(0), room,
                new QuickQuizSubmitAnswerDTO(room.getId(), question.getId(), 0, "2"));
        QuickQuizService.submitAnswer(players.get(1), room,
                new QuickQuizSubmitAnswerDTO(room.getId(), question.getId(), 0, "2"));
        QuickQuizAnswerResultDTO result = QuickQuizService.submitAnswer(players.get(2), room,
                new QuickQuizSubmitAnswerDTO(room.getId(), question.getId(), -1, "不作答"));
        QuickQuizService.clearRoom(room);

        assertTrue(result.isFinished());
        assertEquals(15, result.getPrizePool());
        assertEquals(8, result.getRewardPerWinner());
        assertEquals(Arrays.asList("1:-5", "2:-5", "3:-5", "1:8", "2:8"), economyEvents);
        assertEquals(Arrays.asList(
                "1:" + Game.QUICK_QUIZ + ":true:61",
                "2:" + Game.QUICK_QUIZ + ":true:61",
                "3:" + Game.QUICK_QUIZ + ":false:61"), miniGameEvents);
        assertEquals(0, QuickQuizService.poolOf(room.getId()));
    }

    @Test
    public void clearRoomShouldRefundChargedEntryFeesOnce() {
        GameRoom room = room(3, 5, 120);
        List<User> players = players(3);
        join(room, players);
        QuickQuizService.nextQuestion(
                players.get(0), room, (usedQuestionIds) -> question(303L, "异常退出题", 0, 10));

        QuickQuizService.clearRoom(room);
        QuickQuizService.clearRoom(room);

        assertEquals(Arrays.asList(
                "1:-5", "2:-5", "3:-5",
                "1:5", "2:5", "3:5"), economyEvents);
        assertEquals(0, QuickQuizService.poolOf(room.getId()));
    }

    @Test
    public void failedRefundShouldRemainPendingForNextRoomCleanup() {
        AtomicInteger refundFailures = new AtomicInteger();
        QuickQuizService.setEconomyForTest((accountId, delta) -> {
            if (accountId == 2L && delta > 0 && refundFailures.getAndIncrement() == 0) {
                economyEvents.add("2:5:失败");
                throw new IllegalStateException("模拟退款失败");
            }
            economyEvents.add(accountId + ":" + delta);
        });
        GameRoom room = room(3, 5, 120);
        List<User> players = players(3);
        join(room, players);
        QuickQuizService.nextQuestion(
                players.get(0), room, (usedQuestionIds) -> question(304L, "退款重试题", 0, 10));

        QuickQuizService.clearRoom(room);
        QuickQuizService.clearRoom(room);

        assertEquals(Arrays.asList(
                "1:-5", "2:-5", "3:-5",
                "1:5", "2:5:失败", "3:5", "2:5"), economyEvents);
        assertEquals(0, QuickQuizService.poolOf(room.getId()));
    }

    @Test
    public void twoPlayerFinalResultShouldApplyRoomBonusContext() {
        GameRoom room = room(2, 0, 120);
        room.setQuickQuizQuestionCount(1);
        List<User> players = players(2);
        join(room, players);
        miniGameEvents.clear();
        QuickQuizService.setMiniGameRewardsForTest(new MiniGameRewards() {
            @Override
            public void apply(long accountId, Game game, boolean win, long durationSeconds) {
                miniGameEvents.add(accountId + ":" + game + ":" + win + ":" + durationSeconds);
            }

            @Override
            public void applyRoomBonus(Game game, List<Long> accountIds, long durationSeconds) {
                miniGameEvents.add("room:" + game + ":" + accountIds + ":" + durationSeconds);
            }
        });
        QuickQuizQuestionDTO question = QuickQuizService.nextQuestion(
                players.get(0), room, (usedQuestionIds) -> question(302L, "1 + 1 = ?", 0, 10));
        nowSeconds.addAndGet(61);

        QuickQuizService.submitAnswer(players.get(0), room,
                new QuickQuizSubmitAnswerDTO(room.getId(), question.getId(), 0, "2"));
        QuickQuizService.submitAnswer(players.get(1), room,
                new QuickQuizSubmitAnswerDTO(room.getId(), question.getId(), 1, "北京"));

        assertEquals(Arrays.asList(
                "1:" + Game.QUICK_QUIZ + ":true:61",
                "2:" + Game.QUICK_QUIZ + ":false:61",
                "room:" + Game.QUICK_QUIZ + ":[1, 2]:61"), miniGameEvents);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nextQuestionShouldRejectRoomLargerThanEightPlayers() {
        GameRoom room = room(9, 0, 10);
        List<User> players = players(9);
        join(room, players);

        QuickQuizService.nextQuestion(players.get(0), room, (usedQuestionIds) -> question(401L, "超员题", 0, 5));
    }

    private GameRoom room(int nums, int entryFee, int timeLimitSeconds) {
        GameRoom room = new GameRoom();
        room.setId("quick-quiz-test");
        room.setNums(nums);
        room.setQuickQuizQuestionCount(2);
        room.setQuickQuizEntryFee(entryFee);
        room.setQuickQuizTimeLimitSeconds(timeLimitSeconds);
        return room;
    }

    private List<User> players(int count) {
        List<User> users = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            User user = new User();
            user.setId("channel-" + i);
            user.setAccountId(i);
            user.setNickname("玩家" + i);
            users.add(user);
        }
        return users;
    }

    private void join(GameRoom room, List<User> players) {
        room.setHomeowner(players.get(0));
        for (User user : players) {
            room.getUsers().put(user.getIdentityKey(), new GameRoom.Player(user));
            UserCache.add(user.getId(), user);
        }
    }

    private QuickQuizQuestion question(long id, String text, int correctAnswerIndex, int score) {
        return QuickQuizQuestion.builder()
                .id(id)
                .question(text)
                .optionsJson("[\"2\",\"北京\",\"上海\"]")
                .correctAnswerIndex(correctAnswerIndex)
                .score(score)
                .build();
    }
}
