package cn.xeblog.server.game.drawguess;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.game.drawguess.DrawGuessDTO;
import cn.xeblog.commons.enums.Game;
import cn.xeblog.server.cache.UserCache;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DrawGuessServiceTest {

    private final AtomicLong nowMs = new AtomicLong(1_000L);
    private final List<String> rewardEvents = new ArrayList<>();

    @Before
    public void setUp() {
        DrawGuessService.setNowSupplierForTest(nowMs::get);
        DrawGuessRewardService.setNowSupplierForTest(nowMs::get);
        DrawGuessRewardService.setMiniGameRewardsForTest((accountId, game, win, durationSeconds) ->
                rewardEvents.add(accountId + ":" + game + ":" + win + ":" + durationSeconds));
    }

    @After
    public void tearDown() {
        DrawGuessService.resetNowSupplier();
        DrawGuessRewardService.resetNowSupplier();
        DrawGuessRewardService.resetMiniGameRewards();
        DrawGuessService.clearRoom("draw-guess-start");
        DrawGuessService.clearRoom("draw-guess-score");
        DrawGuessService.clearRoom("draw-guess-timeout");
        DrawGuessService.clearRoom("draw-guess-reject");
        DrawGuessService.clearRoom("draw-guess-undo");
        UserCache.clear();
    }

    @Test
    public void startRoundShouldKeepAnswerVisibleOnlyForDrawer() {
        GameRoom room = room("draw-guess-start", 3, 2, 90);
        List<User> players = players(3);
        join(room, players);

        try {
            DrawGuessService.applyRequestForTest(room, key(2), "玩家2", start(room, "苹果"), nowMs.get());
            fail("非当前画手不应能开始题目");
        } catch (IllegalArgumentException e) {
            assertEquals("还没轮到你出题", e.getMessage());
        }

        DrawGuessDTO start = DrawGuessService.applyRequestForTest(
                room,
                key(1),
                "玩家1",
                start(room, "月亮 猫", "夜空"),
                nowMs.get()).get(0);

        assertEquals(DrawGuessDTO.Event.START_ROUND, start.getEvent());
        assertEquals(key(1), start.getDrawerId());
        assertEquals("月亮 猫", start.getWord());
        assertEquals("＿＿ ＿", start.getMaskedWord());
        assertEquals(Integer.valueOf(3), start.getWordLength());
        assertEquals(90_000L, start.getDeadlineAt() - start.getStartedAt());
        assertEquals("月亮 猫", DrawGuessService.visibleStartForTest(start, true).getWord());
        assertNull(DrawGuessService.visibleStartForTest(start, false).getWord());
    }

    @Test
    public void guessShouldScoreByRankAndFinishWhenAllGuessersCorrect() {
        GameRoom room = room("draw-guess-score", 3, 1, 60);
        List<User> players = players(3);
        join(room, players);
        DrawGuessService.applyRequestForTest(room, key(1), "玩家1", start(room, "风 筝"), nowMs.get());

        nowMs.set(5_000L);
        List<DrawGuessDTO> first = DrawGuessService.applyRequestForTest(
                room,
                key(2),
                "玩家2",
                guess(room, "风筝"),
                nowMs.get());
        assertEquals(2, first.size());
        assertEquals(DrawGuessDTO.Event.GUESS, first.get(0).getEvent());
        assertEquals(DrawGuessDTO.Event.CORRECT, first.get(1).getEvent());
        assertEquals(Integer.valueOf(1), first.get(1).getCorrectRank());
        assertEquals(Integer.valueOf(6), first.get(1).getScoreDelta());
        assertEquals(Integer.valueOf(6), first.get(1).getScores().get(key(2)));
        assertEquals(3, rewardEvents.size());

        nowMs.set(7_000L);
        List<DrawGuessDTO> second = DrawGuessService.applyRequestForTest(
                room,
                key(3),
                "玩家3",
                guess(room, "风筝"),
                nowMs.get());
        assertEquals(3, second.size());
        DrawGuessDTO correct = second.get(1);
        DrawGuessDTO roundEnd = second.get(2);
        assertEquals(Integer.valueOf(2), correct.getCorrectRank());
        assertEquals(Integer.valueOf(5), correct.getScoreDelta());
        assertEquals(DrawGuessDTO.Event.ROUND_END, roundEnd.getEvent());
        assertEquals("all_correct", roundEnd.getRoundEndReason());
        assertFalse(roundEnd.getMatchFinished());
        assertEquals(Integer.valueOf(1), roundEnd.getTurnIndex());
        assertEquals(Integer.valueOf(6), roundEnd.getScores().get(key(2)));
        assertEquals(Integer.valueOf(5), roundEnd.getScores().get(key(3)));

        DrawGuessDTO nextStart = DrawGuessService.applyRequestForTest(
                room,
                key(2),
                "玩家2",
                start(room, "树"),
                nowMs.get()).get(0);
        assertEquals(key(2), nextStart.getDrawerId());
    }

    @Test
    public void timeoutShouldEndWithoutScoreAndFinalTurnCanFinishMatch() {
        GameRoom room = room("draw-guess-timeout", 2, 1, 60);
        List<User> players = players(2);
        join(room, players);
        DrawGuessService.applyRequestForTest(room, key(1), "玩家1", start(room, "树"), nowMs.get());

        nowMs.set(62_000L);
        DrawGuessDTO timeout = DrawGuessService.applyRequestForTest(
                room,
                key(2),
                "玩家2",
                guess(room, "树"),
                nowMs.get()).get(0);
        assertEquals(DrawGuessDTO.Event.ROUND_END, timeout.getEvent());
        assertEquals("timeout", timeout.getRoundEndReason());
        assertEquals(Integer.valueOf(0), timeout.getScores().get(key(2)));
        assertFalse(timeout.getMatchFinished());

        DrawGuessService.applyRequestForTest(room, key(2), "玩家2", start(room, "山"), nowMs.get());
        nowMs.set(65_000L);
        List<DrawGuessDTO> finalTurn = DrawGuessService.applyRequestForTest(
                room,
                key(1),
                "玩家1",
                guess(room, "山"),
                nowMs.get());
        DrawGuessDTO roundEnd = finalTurn.get(2);
        assertTrue(roundEnd.getMatchFinished());
        assertEquals(Integer.valueOf(2), roundEnd.getTurnIndex());
        assertEquals(Integer.valueOf(6), roundEnd.getScores().get(key(1)));
    }

    @Test
    public void clientResultEventsShouldBeRejected() {
        GameRoom room = room("draw-guess-reject", 2, 1, 60);
        List<User> players = players(2);
        join(room, players);
        DrawGuessDTO correct = base(room, DrawGuessDTO.Event.CORRECT);

        try {
            DrawGuessService.applyRequestForTest(room, key(1), "玩家1", correct, nowMs.get());
            fail("客户端不应能提交判定结果");
        } catch (IllegalArgumentException e) {
            assertEquals("你画我猜判定由服务端处理，请不要直接提交结果事件", e.getMessage());
        }
    }

    @Test
    public void undoShouldRemoveTheLastWholeStrokeAndKeepEarlierStrokes() {
        GameRoom room = room("draw-guess-undo", 2, 1, 60);
        List<User> players = players(2);
        join(room, players);
        DrawGuessService.applyRequestForTest(room, key(1), "玩家1", start(room, "小猫"), nowMs.get());

        DrawGuessService.applyRequestForTest(room, key(1), "玩家1", draw(room, "stroke-1", 0), nowMs.get());
        DrawGuessService.applyRequestForTest(room, key(1), "玩家1", draw(room, "stroke-2", 10), nowMs.get());
        DrawGuessService.applyRequestForTest(room, key(1), "玩家1", draw(room, "stroke-2", 20), nowMs.get());

        DrawGuessDTO undo = DrawGuessService.applyRequestForTest(
                room,
                key(1),
                "玩家1",
                base(room, DrawGuessDTO.Event.UNDO),
                nowMs.get()).get(0);

        assertEquals(DrawGuessDTO.Event.UNDO, undo.getEvent());
        assertEquals(1, undo.getLines().size());
        assertEquals("stroke-1", undo.getLines().get(0).getStrokeId());
    }

    private GameRoom room(String id, int nums, int rounds, int timeLimitSeconds) {
        GameRoom room = new GameRoom();
        room.setId(id);
        room.setGame(Game.DRAW_GUESS);
        room.setNums(nums);
        room.setDrawGuessRoundCount(rounds);
        room.setDrawGuessTimeLimitSeconds(timeLimitSeconds);
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

    private DrawGuessDTO start(GameRoom room, String word) {
        return start(room, word, null);
    }

    private DrawGuessDTO start(GameRoom room, String word, String hint) {
        DrawGuessDTO dto = base(room, DrawGuessDTO.Event.START_ROUND);
        dto.setWord(word);
        dto.setHint(hint);
        return dto;
    }

    private DrawGuessDTO guess(GameRoom room, String text) {
        DrawGuessDTO dto = base(room, DrawGuessDTO.Event.GUESS);
        dto.setText(text);
        return dto;
    }

    private DrawGuessDTO draw(GameRoom room, String strokeId, double offset) {
        DrawGuessDTO dto = base(room, DrawGuessDTO.Event.DRAW);
        DrawGuessDTO.Line line = new DrawGuessDTO.Line();
        line.setX1(offset);
        line.setY1(offset);
        line.setX2(offset + 1);
        line.setY2(offset + 1);
        line.setColor("#111827");
        line.setSize(5);
        line.setStrokeId(strokeId);
        dto.setLine(line);
        return dto;
    }

    private DrawGuessDTO base(GameRoom room, DrawGuessDTO.Event event) {
        DrawGuessDTO dto = new DrawGuessDTO();
        dto.setRoomId(room.getId());
        dto.setGame(Game.DRAW_GUESS);
        dto.setEvent(event);
        return dto;
    }

    private String key(int accountId) {
        return "account:" + accountId;
    }
}
