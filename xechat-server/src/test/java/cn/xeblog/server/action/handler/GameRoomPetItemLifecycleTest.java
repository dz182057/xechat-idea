package cn.xeblog.server.action.handler;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.GameDTO;
import cn.xeblog.commons.entity.game.GamePlayerPetItemsDTO;
import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.game.GameRoomMsgDTO;
import cn.xeblog.commons.entity.game.drawguess.DrawGuessDTO;
import cn.xeblog.commons.entity.game.gobang.GobangDTO;
import cn.xeblog.commons.entity.game.quickquiz.QuickQuizAnswerResultDTO;
import cn.xeblog.commons.entity.game.quickquiz.QuickQuizQuestionDTO;
import cn.xeblog.commons.entity.game.quickquiz.QuickQuizSubmitAnswerDTO;
import cn.xeblog.commons.entity.game.tacitquiz.TacitQuizQuestionDTO;
import cn.xeblog.commons.entity.game.tacitquiz.TacitQuizSubmitAnswerDTO;
import cn.xeblog.commons.entity.game.turtlesoup.TurtleSoupDTO;
import cn.xeblog.commons.enums.Game;
import cn.xeblog.commons.enums.UserStatus;
import cn.xeblog.server.account.DbInitializer;
import cn.xeblog.server.cache.GameRoomCache;
import cn.xeblog.server.cache.UserCache;
import cn.xeblog.server.config.GlobalConfig;
import cn.xeblog.server.game.tacitquiz.TacitQuizService;
import cn.xeblog.server.game.quickquiz.QuickQuizService;
import cn.xeblog.server.game.drawguess.DrawGuessRewardService;
import cn.xeblog.server.game.gobang.GobangPetItemService;
import cn.xeblog.server.game.turtlesoup.TurtleSoupService;
import cn.xeblog.server.pet.MiniGameRewards;
import cn.xeblog.server.pet.PetGameItemDeclarationService;
import io.netty.channel.embedded.EmbeddedChannel;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class GameRoomPetItemLifecycleTest {

    @Before
    public void setUp() throws Exception {
        Path root = Files.createTempDirectory("xechat-game-room-pet-item-test");
        System.setProperty(GlobalConfig.DATA_PATH_PROPERTY, root.toString());
        GlobalConfig.initDataPath(null);
        resetFactory();
        DbInitializer.initIfNeeded();
        UserCache.clear();
    }

    @After
    public void tearDown() throws Exception {
        QuickQuizService.clearRoom("pet-item-end-room");
        DrawGuessRewardService.clearRoom("pet-item-end-room");
        DrawGuessRewardService.resetMiniGameRewards();
        DrawGuessRewardService.resetNowSupplier();
        GobangPetItemService.clearRoom("pet-item-end-room");
        GobangPetItemService.resetMiniGameRewards();
        GobangPetItemService.resetNowSupplier();
        GameRoomCache.removeRoom("pet-item-end-room");
        UserCache.clear();
        resetFactory();
        System.clearProperty(GlobalConfig.DATA_PATH_PROPERTY);
        GlobalConfig.initDataPath(null);
    }

    @Test
    public void gameRoomGameOverRefundsReservedPetItems() {
        User user = user(2010L);
        GameRoom room = room(user);
        insertPetItem(user.getAccountId(), "item_gomoku_guard", 1);
        PetGameItemDeclarationService.applyDeclarationForUser(
                user,
                room,
                new GamePlayerPetItemsDTO("item_gomoku_guard", null));

        GameRoomMsgDTO message = new GameRoomMsgDTO(
                room.getId(),
                Game.GOBANG,
                GameRoomMsgDTO.MsgType.GAME_OVER,
                null);
        new GameRoomActionHandler().process(user, room, message);

        Assert.assertEquals(1, countItem(user.getAccountId(), "item_gomoku_guard"));
        Assert.assertNull(room.getUsers().get(user.getIdentityKey()).getPetPlayItemId());
        Assert.assertEquals(1, countUsages(room.getId(), user.getAccountId(),
                "item_gomoku_guard", "gameplay", "refunded"));
    }

    @Test
    public void actionGameOverRefundsReservedPetItems() {
        User user = user(2011L);
        GameRoom room = room(user);
        insertPetItem(user.getAccountId(), "item_gomoku_guard", 1);
        PetGameItemDeclarationService.applyDeclarationForUser(
                user,
                room,
                new GamePlayerPetItemsDTO("item_gomoku_guard", null));

        GameDTO body = new GameDTO();
        body.setRoomId(room.getId());
        body.setGame(Game.GOBANG);
        new GameOverActionHandler().process(user, room, body);

        Assert.assertEquals(1, countItem(user.getAccountId(), "item_gomoku_guard"));
        Assert.assertNull(room.getUsers().get(user.getIdentityKey()).getPetPlayItemId());
        Assert.assertEquals(1, countUsages(room.getId(), user.getAccountId(),
                "item_gomoku_guard", "gameplay", "refunded"));
    }

    @Test
    public void drawGuessStartRoundConsumesGuesserPlayItem() {
        User drawer = user(2020L);
        User guesser = user(2021L);
        GameRoom room = room(Game.DRAW_GUESS, drawer, guesser);
        insertPetItem(guesser.getAccountId(), "item_hint", 1);
        PetGameItemDeclarationService.applyDeclarationForUser(
                guesser,
                room,
                new GamePlayerPetItemsDTO("item_hint", null));

        DrawGuessDTO body = new DrawGuessDTO();
        body.setRoomId(room.getId());
        body.setGame(Game.DRAW_GUESS);
        body.setEvent(DrawGuessDTO.Event.START_ROUND);
        body.setDrawerId(drawer.getIdentityKey());
        body.setDrawerName(drawer.getUsername());
        new GameActionHandler().process(drawer, room, body);

        Assert.assertEquals(0, countItem(guesser.getAccountId(), "item_hint"));
        Assert.assertNull(room.getUsers().get(guesser.getIdentityKey()).getPetPlayItemId());
        Assert.assertEquals(1, countUsages(room.getId(), guesser.getAccountId(),
                "item_hint", "gameplay", "consumed"));
    }

    @Test
    public void drawGuessCorrectRefundsUnusedDrawerPlayItem() {
        User drawer = user(2030L);
        User guesser = user(2031L);
        GameRoom room = room(Game.DRAW_GUESS, drawer, guesser);
        insertPetItem(drawer.getAccountId(), "item_hint", 1);
        PetGameItemDeclarationService.applyDeclarationForUser(
                drawer,
                room,
                new GamePlayerPetItemsDTO("item_hint", null));

        DrawGuessDTO body = new DrawGuessDTO();
        body.setRoomId(room.getId());
        body.setGame(Game.DRAW_GUESS);
        body.setEvent(DrawGuessDTO.Event.CORRECT);
        body.setGuesserId(guesser.getIdentityKey());
        body.setGuesserName(guesser.getUsername());
        body.setWord("小猫");
        new GameActionHandler().process(drawer, room, body);

        Assert.assertEquals(1, countItem(drawer.getAccountId(), "item_hint"));
        Assert.assertNull(room.getUsers().get(drawer.getIdentityKey()).getPetPlayItemId());
        Assert.assertEquals(1, countUsages(room.getId(), drawer.getAccountId(),
                "item_hint", "gameplay", "refunded"));
    }

    @Test
    public void drawGuessCorrectShouldApplyMiniGameRewardsToDrawerAndGuesser() {
        User drawer = user(2032L);
        User guesser = user(2033L);
        GameRoom room = room(Game.DRAW_GUESS, drawer, guesser);
        List<String> miniGameEvents = new ArrayList<>();
        long[] now = {2_000_000L, 2_061_000L};
        int[] nowIndex = {0};
        DrawGuessRewardService.setNowSupplierForTest(() -> now[Math.min(nowIndex[0]++, now.length - 1)]);
        DrawGuessRewardService.setMiniGameRewardsForTest(new MiniGameRewards() {
            @Override
            public void apply(long accountId, Game game, boolean win, long durationSeconds) {
                miniGameEvents.add(accountId + ":" + game + ":" + win + ":" + durationSeconds);
            }

            @Override
            public void applyRoomBonus(Game game, List<Long> accountIds, long durationSeconds) {
                miniGameEvents.add("room:" + game + ":" + accountIds + ":" + durationSeconds);
            }
        });

        DrawGuessDTO start = new DrawGuessDTO();
        start.setRoomId(room.getId());
        start.setGame(Game.DRAW_GUESS);
        start.setEvent(DrawGuessDTO.Event.START_ROUND);
        start.setDrawerId(drawer.getIdentityKey());
        start.setDrawerName(drawer.getUsername());
        new GameActionHandler().process(drawer, room, start);

        DrawGuessDTO correct = new DrawGuessDTO();
        correct.setRoomId(room.getId());
        correct.setGame(Game.DRAW_GUESS);
        correct.setEvent(DrawGuessDTO.Event.CORRECT);
        correct.setGuesserId(guesser.getIdentityKey());
        correct.setGuesserName(guesser.getUsername());
        new GameActionHandler().process(guesser, room, correct);

        Assert.assertEquals(3, miniGameEvents.size());
        Assert.assertTrue(miniGameEvents.contains("2032:DRAW_GUESS:false:61"));
        Assert.assertTrue(miniGameEvents.contains("2033:DRAW_GUESS:true:61"));
        Assert.assertTrue(miniGameEvents.contains("room:DRAW_GUESS:[2032, 2033]:61"));
    }

    @Test
    public void tacitQuizProphecyShouldRewardWhenAnswersMatch() {
        User alice = user(2040L);
        User bob = user(2041L);
        GameRoom room = room(Game.TACIT_QUIZ, alice, bob);
        room.setTacitQuizQuestionCount(1);
        insertTacitQuizQuestion("一起选一个");
        insertPetItem(alice.getAccountId(), "item_sync_prophecy", 1);
        PetGameItemDeclarationService.applyDeclarationForUser(
                alice,
                room,
                new GamePlayerPetItemsDTO(null, "item_sync_prophecy"));

        TacitQuizQuestionDTO question = TacitQuizService.nextQuestion(alice, room);
        TacitQuizService.submitAnswer(alice, room,
                new TacitQuizSubmitAnswerDTO(room.getId(), question.getId(), 0, "A"));
        TacitQuizService.submitAnswer(bob, room,
                new TacitQuizSubmitAnswerDTO(room.getId(), question.getId(), 0, "A"));

        Assert.assertEquals(1, countItem(alice.getAccountId(), "item_sync_prophecy"));
        Assert.assertEquals(1, countUsages(room.getId(), alice.getAccountId(),
                "item_sync_prophecy", "interaction", "succeeded"));
        Assert.assertEquals(20, maxRewardBones(room.getId(), alice.getAccountId(),
                "item_sync_prophecy", "interaction", "succeeded"));
    }

    @Test
    public void tacitQuizPerspectiveShouldConsumeWhenAnswersDiffer() {
        User alice = user(2050L);
        User bob = user(2051L);
        GameRoom room = room(Game.TACIT_QUIZ, alice, bob);
        room.setTacitQuizQuestionCount(1);
        insertTacitQuizQuestion("不要选一样");
        insertPetItem(alice.getAccountId(), "item_sync_perspective", 1);
        PetGameItemDeclarationService.applyDeclarationForUser(
                alice,
                room,
                new GamePlayerPetItemsDTO(null, "item_sync_perspective"));

        TacitQuizQuestionDTO question = TacitQuizService.nextQuestion(alice, room);
        TacitQuizService.submitAnswer(alice, room,
                new TacitQuizSubmitAnswerDTO(room.getId(), question.getId(), 0, "A"));
        TacitQuizService.submitAnswer(bob, room,
                new TacitQuizSubmitAnswerDTO(room.getId(), question.getId(), 1, "B"));

        Assert.assertEquals(0, countItem(alice.getAccountId(), "item_sync_perspective"));
        Assert.assertEquals(1, countUsages(room.getId(), alice.getAccountId(),
                "item_sync_perspective", "interaction", "failed"));
    }

    @Test
    public void quickQuizScorePadShouldConsumeWhenWrongAnswerIsProtected() {
        User alice = user(2060L);
        User bob = user(2061L);
        GameRoom room = room(Game.QUICK_QUIZ, alice, bob);
        room.setQuickQuizQuestionCount(1);
        room.setQuickQuizTimeLimitSeconds(20);
        insertQuickQuizQuestion("哪一个是正确答案？", 0, 5);
        insertPetItem(alice.getAccountId(), "item_quiz_score_pad", 1);
        PetGameItemDeclarationService.applyDeclarationForUser(
                alice,
                room,
                new GamePlayerPetItemsDTO("item_quiz_score_pad", null));

        QuickQuizQuestionDTO question = QuickQuizService.nextQuestion(alice, room);
        QuickQuizService.submitAnswer(alice, room,
                new QuickQuizSubmitAnswerDTO(room.getId(), question.getId(), 1, "B"));
        QuickQuizAnswerResultDTO result = QuickQuizService.submitAnswer(bob, room,
                new QuickQuizSubmitAnswerDTO(room.getId(), question.getId(), 0, "A"));

        Assert.assertEquals(0, result.getAnswers().get(0).getPointsDelta());
        Assert.assertEquals(0, result.getAnswers().get(0).getTotalScore());
        Assert.assertEquals(0, countItem(alice.getAccountId(), "item_quiz_score_pad"));
        Assert.assertNull(room.getUsers().get(alice.getIdentityKey()).getPetPlayItemId());
        Assert.assertEquals(1, countUsages(room.getId(), alice.getAccountId(),
                "item_quiz_score_pad", "gameplay", "consumed"));
        Assert.assertTrue(result.getPetItemNotices().contains("护分爪垫触发，玩家2060 本题答错扣分被抵消。"));
    }

    @Test
    public void quickQuizWrongOptionShouldMarkAndConsumeWrongOptionItem() {
        User alice = user(2070L);
        User bob = user(2071L);
        GameRoom room = room(Game.QUICK_QUIZ, alice, bob);
        room.setQuickQuizQuestionCount(1);
        room.setQuickQuizTimeLimitSeconds(20);
        insertQuickQuizQuestion("排除一个错误答案", 0, 5);
        insertPetItem(alice.getAccountId(), "item_quiz_wrong_option", 1);
        PetGameItemDeclarationService.applyDeclarationForUser(
                alice,
                room,
                new GamePlayerPetItemsDTO("item_quiz_wrong_option", null));

        QuickQuizQuestionDTO question = QuickQuizService.nextQuestion(alice, room);

        Assert.assertTrue(question.getPetItemDisabledOptionIndex() > 0);
        Assert.assertNotEquals(question.getCorrectAnswerIndex(), question.getPetItemDisabledOptionIndex().intValue());
        Assert.assertEquals("错项嗅探触发，已为你排除一个错误选项。", question.getPetItemNotice());
        Assert.assertEquals(0, countItem(alice.getAccountId(), "item_quiz_wrong_option"));
        Assert.assertNull(room.getUsers().get(alice.getIdentityKey()).getPetPlayItemId());
        Assert.assertEquals(1, countUsages(room.getId(), alice.getAccountId(),
                "item_quiz_wrong_option", "gameplay", "consumed"));
    }

    @Test
    public void quickQuizDuelShouldRewardWhenCarrierScoresAboveDefaultTarget() {
        User alice = user(2080L);
        User bob = user(2081L);
        User carl = user(2082L);
        GameRoom room = room(Game.QUICK_QUIZ, alice, bob, carl);
        room.setQuickQuizQuestionCount(1);
        room.setQuickQuizTimeLimitSeconds(20);
        insertQuickQuizQuestion("点名对决题", 0, 5);
        insertPetItem(alice.getAccountId(), "item_quiz_duel", 1);
        PetGameItemDeclarationService.applyDeclarationForUser(
                alice,
                room,
                new GamePlayerPetItemsDTO(null, "item_quiz_duel"));

        QuickQuizQuestionDTO question = QuickQuizService.nextQuestion(alice, room);
        QuickQuizService.submitAnswer(alice, room,
                new QuickQuizSubmitAnswerDTO(room.getId(), question.getId(), 0, "A"));
        QuickQuizService.submitAnswer(bob, room,
                new QuickQuizSubmitAnswerDTO(room.getId(), question.getId(), 1, "B"));
        QuickQuizAnswerResultDTO result = QuickQuizService.submitAnswer(carl, room,
                new QuickQuizSubmitAnswerDTO(room.getId(), question.getId(), -1, "不作答"));

        Assert.assertEquals(1, countItem(alice.getAccountId(), "item_quiz_duel"));
        Assert.assertEquals(1, countUsages(room.getId(), alice.getAccountId(),
                "item_quiz_duel", "interaction", "succeeded"));
        Assert.assertEquals(30, maxRewardBones(room.getId(), alice.getAccountId(),
                "item_quiz_duel", "interaction", "succeeded"));
        Assert.assertTrue(result.getPetItemNotices().contains("点名对决命中，玩家2080 得分高于 玩家2081，返还道具并获得 🦴30。"));
    }

    @Test
    public void gobangPredictionShouldRewardWhenOpponentHitsPredictedCell() {
        User alice = user(2090L);
        User bob = user(2091L);
        GameRoom room = room(Game.GOBANG, alice, bob);
        insertPetItem(alice.getAccountId(), "item_gomoku_prediction", 1);
        PetGameItemDeclarationService.applyDeclarationForUser(
                alice,
                room,
                new GamePlayerPetItemsDTO(null, "item_gomoku_prediction"));

        new GameActionHandler().process(alice, room, gobangMove(7, 7, 1));
        GobangDTO bobMove = gobangMove(8, 7, 2);
        new GameActionHandler().process(bob, room, bobMove);

        Assert.assertEquals(1, countItem(alice.getAccountId(), "item_gomoku_prediction"));
        Assert.assertNull(room.getUsers().get(alice.getIdentityKey()).getPetInteractionItemId());
        Assert.assertEquals(1, countUsages(room.getId(), alice.getAccountId(),
                "item_gomoku_prediction", "interaction", "succeeded"));
        Assert.assertEquals(50, maxRewardBones(room.getId(), alice.getAccountId(),
                "item_gomoku_prediction", "interaction", "succeeded"));
        Assert.assertEquals("猜你落这儿命中，玩家2090 预测对手下一手 (8,7)，返还道具并获得 🦴50。",
                bobMove.getPetItemNotice());
    }

    @Test
    public void gobangPredictionShouldConsumeWhenOpponentMissesPredictedCell() {
        User alice = user(2100L);
        User bob = user(2101L);
        GameRoom room = room(Game.GOBANG, alice, bob);
        insertPetItem(alice.getAccountId(), "item_gomoku_prediction", 1);
        PetGameItemDeclarationService.applyDeclarationForUser(
                alice,
                room,
                new GamePlayerPetItemsDTO(null, "item_gomoku_prediction"));

        new GameActionHandler().process(alice, room, gobangMove(7, 7, 1));
        GobangDTO bobMove = gobangMove(9, 7, 2);
        new GameActionHandler().process(bob, room, bobMove);

        Assert.assertEquals(0, countItem(alice.getAccountId(), "item_gomoku_prediction"));
        Assert.assertNull(room.getUsers().get(alice.getIdentityKey()).getPetInteractionItemId());
        Assert.assertEquals(1, countUsages(room.getId(), alice.getAccountId(),
                "item_gomoku_prediction", "interaction", "failed"));
        Assert.assertEquals("猜你落这儿未命中，玩家2100 预测 (8,7)，实际落子 (9,7)，道具已消耗。",
                bobMove.getPetItemNotice());
    }

    @Test
    public void quickQuizProphecyShouldRewardWhenCarrierIsUniqueWinner() {
        User alice = user(2110L);
        User bob = user(2111L);
        GameRoom room = room(Game.QUICK_QUIZ, alice, bob);
        room.setQuickQuizQuestionCount(1);
        room.setQuickQuizTimeLimitSeconds(20);
        insertQuickQuizQuestion("胜负预言贴题", 0, 5);
        insertPetItem(alice.getAccountId(), "item_prophecy", 1);
        PetGameItemDeclarationService.applyDeclarationForUser(
                alice,
                room,
                new GamePlayerPetItemsDTO(null, "item_prophecy"));

        QuickQuizQuestionDTO question = QuickQuizService.nextQuestion(alice, room);
        QuickQuizService.submitAnswer(alice, room,
                new QuickQuizSubmitAnswerDTO(room.getId(), question.getId(), 0, "A"));
        QuickQuizAnswerResultDTO result = QuickQuizService.submitAnswer(bob, room,
                new QuickQuizSubmitAnswerDTO(room.getId(), question.getId(), 1, "B"));

        Assert.assertEquals(1, countItem(alice.getAccountId(), "item_prophecy"));
        Assert.assertNull(room.getUsers().get(alice.getIdentityKey()).getPetInteractionItemId());
        Assert.assertEquals(1, countUsages(room.getId(), alice.getAccountId(),
                "item_prophecy", "interaction", "succeeded"));
        Assert.assertEquals(20, maxRewardBones(room.getId(), alice.getAccountId(),
                "item_prophecy", "interaction", "succeeded"));
        Assert.assertTrue(result.getPetItemNotices().contains("胜负预言贴命中，玩家2110 成为唯一胜者，返还道具并获得 🦴20。"));
    }

    @Test
    public void quickQuizProphecyShouldRefundWhenWinnersTie() {
        User alice = user(2120L);
        User bob = user(2121L);
        GameRoom room = room(Game.QUICK_QUIZ, alice, bob);
        room.setQuickQuizQuestionCount(1);
        room.setQuickQuizTimeLimitSeconds(20);
        insertQuickQuizQuestion("胜负预言贴并列题", 0, 5);
        insertPetItem(alice.getAccountId(), "item_prophecy", 1);
        PetGameItemDeclarationService.applyDeclarationForUser(
                alice,
                room,
                new GamePlayerPetItemsDTO(null, "item_prophecy"));

        QuickQuizQuestionDTO question = QuickQuizService.nextQuestion(alice, room);
        QuickQuizService.submitAnswer(alice, room,
                new QuickQuizSubmitAnswerDTO(room.getId(), question.getId(), 0, "A"));
        QuickQuizAnswerResultDTO result = QuickQuizService.submitAnswer(bob, room,
                new QuickQuizSubmitAnswerDTO(room.getId(), question.getId(), 0, "A"));

        Assert.assertEquals(1, countItem(alice.getAccountId(), "item_prophecy"));
        Assert.assertNull(room.getUsers().get(alice.getIdentityKey()).getPetInteractionItemId());
        Assert.assertEquals(1, countUsages(room.getId(), alice.getAccountId(),
                "item_prophecy", "interaction", "refunded"));
        Assert.assertTrue(result.getPetItemNotices().contains("胜负预言贴未结算，快问快答出现并列胜者，玩家2120 的道具已返还。"));
    }

    @Test
    public void gobangProphecyShouldRewardWhenCarrierWins() {
        User alice = user(2130L);
        User bob = user(2131L);
        GameRoom room = room(Game.GOBANG, alice, bob);
        insertPetItem(alice.getAccountId(), "item_prophecy", 1);
        PetGameItemDeclarationService.applyDeclarationForUser(
                alice,
                room,
                new GamePlayerPetItemsDTO(null, "item_prophecy"));

        new GameActionHandler().process(alice, room, gobangMove(0, 1, 1));
        new GameActionHandler().process(bob, room, gobangMove(0, 0, 2));
        new GameActionHandler().process(alice, room, gobangMove(1, 1, 1));
        new GameActionHandler().process(bob, room, gobangMove(1, 0, 2));
        new GameActionHandler().process(alice, room, gobangMove(2, 1, 1));
        new GameActionHandler().process(bob, room, gobangMove(2, 0, 2));
        new GameActionHandler().process(alice, room, gobangMove(3, 1, 1));
        new GameActionHandler().process(bob, room, gobangMove(3, 0, 2));
        GobangDTO winningMove = gobangMove(4, 1, 1);
        new GameActionHandler().process(alice, room, winningMove);

        Assert.assertEquals(1, countItem(alice.getAccountId(), "item_prophecy"));
        Assert.assertNull(room.getUsers().get(alice.getIdentityKey()).getPetInteractionItemId());
        Assert.assertEquals(1, countUsages(room.getId(), alice.getAccountId(),
                "item_prophecy", "interaction", "succeeded"));
        Assert.assertEquals(20, maxRewardBones(room.getId(), alice.getAccountId(),
                "item_prophecy", "interaction", "succeeded"));
        Assert.assertEquals("胜负预言贴命中，玩家2130 成为唯一胜者，返还道具并获得 🦴20。",
                winningMove.getPetItemNotice());
    }

    @Test
    public void gobangWinningMoveShouldApplyMiniGameRewardsToWinnerAndLoser() {
        User alice = user(2132L);
        User bob = user(2133L);
        GameRoom room = room(Game.GOBANG, alice, bob);
        List<String> miniGameEvents = new ArrayList<>();
        long[] now = {3_000_000L, 3_061_000L};
        int[] nowIndex = {0};
        GobangPetItemService.setNowSupplierForTest(() -> now[Math.min(nowIndex[0]++, now.length - 1)]);
        GobangPetItemService.setMiniGameRewardsForTest(new MiniGameRewards() {
            @Override
            public void apply(long accountId, Game game, boolean win, long durationSeconds) {
                miniGameEvents.add(accountId + ":" + game + ":" + win + ":" + durationSeconds);
            }

            @Override
            public void applyRoomBonus(Game game, List<Long> accountIds, long durationSeconds) {
                miniGameEvents.add("room:" + game + ":" + accountIds + ":" + durationSeconds);
            }
        });

        new GameActionHandler().process(alice, room, gobangMove(0, 1, 1));
        new GameActionHandler().process(bob, room, gobangMove(0, 0, 2));
        new GameActionHandler().process(alice, room, gobangMove(1, 1, 1));
        new GameActionHandler().process(bob, room, gobangMove(1, 0, 2));
        new GameActionHandler().process(alice, room, gobangMove(2, 1, 1));
        new GameActionHandler().process(bob, room, gobangMove(2, 0, 2));
        new GameActionHandler().process(alice, room, gobangMove(3, 1, 1));
        new GameActionHandler().process(bob, room, gobangMove(3, 0, 2));
        new GameActionHandler().process(alice, room, gobangMove(4, 1, 1));

        Assert.assertEquals(3, miniGameEvents.size());
        Assert.assertTrue(miniGameEvents.contains("2132:GOBANG:true:61"));
        Assert.assertTrue(miniGameEvents.contains("2133:GOBANG:false:61"));
        Assert.assertTrue(miniGameEvents.contains("room:GOBANG:[2132, 2133]:61"));
    }

    @Test
    public void gobangGuardShouldConsumeAndHighlightOpponentWinningCell() {
        User alice = user(2160L);
        User bob = user(2161L);
        GameRoom room = room(Game.GOBANG, alice, bob);
        insertPetItem(alice.getAccountId(), "item_gomoku_guard", 1);
        PetGameItemDeclarationService.applyDeclarationForUser(
                alice,
                room,
                new GamePlayerPetItemsDTO("item_gomoku_guard", null));

        new GameActionHandler().process(bob, room, gobangMove(0, 0, 2));
        new GameActionHandler().process(alice, room, gobangMove(0, 2, 1));
        new GameActionHandler().process(bob, room, gobangMove(1, 0, 2));
        new GameActionHandler().process(alice, room, gobangMove(1, 2, 1));
        new GameActionHandler().process(bob, room, gobangMove(2, 0, 2));
        new GameActionHandler().process(alice, room, gobangMove(2, 2, 1));
        GobangDTO threatMove = gobangMove(3, 0, 2);
        new GameActionHandler().process(bob, room, threatMove);

        Assert.assertEquals(Integer.valueOf(4), threatMove.getPetItemGuardX());
        Assert.assertEquals(Integer.valueOf(0), threatMove.getPetItemGuardY());
        Assert.assertEquals(0, countItem(alice.getAccountId(), "item_gomoku_guard"));
        Assert.assertNull(room.getUsers().get(alice.getIdentityKey()).getPetPlayItemId());
        Assert.assertEquals(1, countUsages(room.getId(), alice.getAccountId(),
                "item_gomoku_guard", "gameplay", "consumed"));
        Assert.assertEquals("守门骨触发，已为 玩家2160 高亮对手下一手五连胜点 (4,0)，道具已消耗。",
                threatMove.getPetItemNotice());
    }

    @Test
    public void turtleSoupProbeShouldConsumeAndKeepGuessChanceWhenGuessIsWrong() {
        User host = user(2140L);
        User guesser = user(2141L);
        GameRoom room = room(Game.TURTLE_SOUP, host, guesser);
        room.setTurtleSoupGuessLimit(1);
        insertTurtleSoupStory("试探题", "这里是汤面", "这里是汤底");
        insertPetItem(guesser.getAccountId(), "item_turtle_probe", 1);
        PetGameItemDeclarationService.applyDeclarationForUser(
                guesser,
                room,
                new GamePlayerPetItemsDTO("item_turtle_probe", null));

        TurtleSoupService.nextStory(host, room);
        new GameActionHandler().process(host, room, turtleSoupEvent(TurtleSoupDTO.Event.CONFIRM_STORY));
        TurtleSoupDTO guess = turtleSoupEvent(TurtleSoupDTO.Event.GUESS);
        guess.setContent("我先试一个错误答案");
        new GameActionHandler().process(guesser, room, guess);
        TurtleSoupDTO judge = turtleSoupEvent(TurtleSoupDTO.Event.JUDGE);
        judge.setGuessResult(TurtleSoupDTO.GuessResult.WRONG);
        new GameActionHandler().process(host, room, judge);

        Assert.assertEquals(0, countItem(guesser.getAccountId(), "item_turtle_probe"));
        Assert.assertNull(room.getUsers().get(guesser.getIdentityKey()).getPetPlayItemId());
        Assert.assertEquals(1, countUsages(room.getId(), guesser.getAccountId(),
                "item_turtle_probe", "gameplay", "consumed"));
        Assert.assertEquals(0, countTurtleSoupRecords(room.getId()));
        Assert.assertEquals("试探骨触发，本次猜底判定为错误，不消耗正式猜底次数，道具已消耗。",
                judge.getPetItemNotice());
        Assert.assertEquals(0, judge.getGuessUsed());
    }

    @Test
    public void turtleSoupProbeShouldRefundWhenProbeGuessIsCorrect() {
        User host = user(2150L);
        User guesser = user(2151L);
        GameRoom room = room(Game.TURTLE_SOUP, host, guesser);
        room.setTurtleSoupGuessLimit(1);
        insertTurtleSoupStory("试探命中题", "这里是另一个汤面", "这里是另一个汤底");
        insertPetItem(guesser.getAccountId(), "item_turtle_probe", 1);
        PetGameItemDeclarationService.applyDeclarationForUser(
                guesser,
                room,
                new GamePlayerPetItemsDTO("item_turtle_probe", null));

        TurtleSoupService.nextStory(host, room);
        new GameActionHandler().process(host, room, turtleSoupEvent(TurtleSoupDTO.Event.CONFIRM_STORY));
        TurtleSoupDTO guess = turtleSoupEvent(TurtleSoupDTO.Event.GUESS);
        guess.setContent("我试中了汤底");
        new GameActionHandler().process(guesser, room, guess);
        TurtleSoupDTO judge = turtleSoupEvent(TurtleSoupDTO.Event.JUDGE);
        judge.setGuessResult(TurtleSoupDTO.GuessResult.CORRECT);
        new GameActionHandler().process(host, room, judge);

        Assert.assertEquals(1, countItem(guesser.getAccountId(), "item_turtle_probe"));
        Assert.assertNull(room.getUsers().get(guesser.getIdentityKey()).getPetPlayItemId());
        Assert.assertEquals(1, countUsages(room.getId(), guesser.getAccountId(),
                "item_turtle_probe", "gameplay", "refunded"));
        Assert.assertEquals(1, countTurtleSoupRecords(room.getId()));
        Assert.assertEquals("试探骨命中正确汤底，道具已返还。",
                judge.getPetItemNotice());
        Assert.assertEquals(1, judge.getGuessUsed());
    }

    private static User user(long accountId) {
        User user = new User();
        user.setId("pet-item-channel-" + accountId);
        user.setAccountId(accountId);
        user.setAccount("account-" + accountId);
        user.setNickname("玩家" + accountId);
        user.setUuid("uuid-" + accountId);
        user.setStatus(UserStatus.PLAYING);
        user.setChannel(new EmbeddedChannel());
        return user;
    }

    private static GameRoom room(User user) {
        return room(Game.GOBANG, user);
    }

    private static GameRoom room(Game game, User... users) {
        GameRoom room = GameRoomCache.seize("pet-item-end-room");
        room.setId("pet-item-end-room");
        room.setGame(game);
        room.setNums(Math.max(1, users.length));
        room.setHomeowner(users[0]);
        for (User user : users) {
            UserCache.add(user.getId(), user);
            room.addUser(user);
        }
        return room;
    }

    private static void insertPetItem(long accountId, String itemId, int count) {
        try (SqlSession session = DbInitializer.factory().openSession(true);
             java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                     "INSERT INTO pet_items (account_id, item_id, count, updated_at) VALUES (?, ?, ?, ?)")) {
            statement.setLong(1, accountId);
            statement.setString(2, itemId);
            statement.setInt(3, count);
            statement.setLong(4, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void insertTacitQuizQuestion(String question) {
        try (SqlSession session = DbInitializer.factory().openSession(true);
             java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                     "INSERT INTO tacit_quiz_questions (question, options_json, sort_order, active, created_at, updated_at) " +
                             "VALUES (?, ?, ?, 1, ?, ?)")) {
            long now = System.currentTimeMillis();
            statement.setString(1, question);
            statement.setString(2, "[\"A\",\"B\"]");
            statement.setInt(3, 1);
            statement.setLong(4, now);
            statement.setLong(5, now);
            statement.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void insertQuickQuizQuestion(String question, int correctAnswerIndex, int score) {
        try (SqlSession session = DbInitializer.factory().openSession(true);
             java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                     "INSERT INTO quick_quiz_questions (question, options_json, correct_answer_index, score, sort_order, active, created_at, updated_at) " +
                             "VALUES (?, ?, ?, ?, ?, 1, ?, ?)")) {
            long now = System.currentTimeMillis();
            statement.setString(1, question);
            statement.setString(2, "[\"A\",\"B\",\"C\"]");
            statement.setInt(3, correctAnswerIndex);
            statement.setInt(4, score);
            statement.setInt(5, 1);
            statement.setLong(6, now);
            statement.setLong(7, now);
            statement.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static GobangDTO gobangMove(int x, int y, int type) {
        GobangDTO dto = new GobangDTO();
        dto.setRoomId("pet-item-end-room");
        dto.setGame(Game.GOBANG);
        dto.setX(x);
        dto.setY(y);
        dto.setType(type);
        return dto;
    }

    private static TurtleSoupDTO turtleSoupEvent(TurtleSoupDTO.Event event) {
        TurtleSoupDTO dto = new TurtleSoupDTO("pet-item-end-room");
        dto.setEvent(event);
        return dto;
    }

    private static void insertTurtleSoupStory(String title, String surface, String bottom) {
        try (SqlSession session = DbInitializer.factory().openSession(true);
             java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                     "INSERT INTO turtle_soup_stories " +
                             "(title, surface, bottom, key_clue, difficulty, tags, sort_order, active, created_at, updated_at) " +
                             "VALUES (?, ?, ?, ?, ?, ?, 1, 1, ?, ?)")) {
            long now = System.currentTimeMillis();
            statement.setString(1, title);
            statement.setString(2, surface);
            statement.setString(3, bottom);
            statement.setString(4, "关键线索");
            statement.setString(5, "简单");
            statement.setString(6, "测试");
            statement.setLong(7, now);
            statement.setLong(8, now);
            statement.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static int countItem(long accountId, String itemId) {
        try (SqlSession session = DbInitializer.factory().openSession(true);
             java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                     "SELECT COALESCE(MAX(count), 0) FROM pet_items WHERE account_id = ? AND item_id = ?")) {
            statement.setLong(1, accountId);
            statement.setString(2, itemId);
            try (java.sql.ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static int countTurtleSoupRecords(String roomId) {
        try (SqlSession session = DbInitializer.factory().openSession(true);
             java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                     "SELECT COUNT(1) FROM turtle_soup_records WHERE room_id = ?")) {
            statement.setString(1, roomId);
            try (java.sql.ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static int countUsages(String roomId, long accountId, String itemId, String slot, String status) {
        try (SqlSession session = DbInitializer.factory().openSession(true);
             java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                     "SELECT COUNT(1) FROM game_item_uses " +
                             "WHERE game_id = ? AND account_id = ? AND item_id = ? AND slot = ? AND status = ?")) {
            statement.setString(1, roomId);
            statement.setLong(2, accountId);
            statement.setString(3, itemId);
            statement.setString(4, slot);
            statement.setString(5, status);
            try (java.sql.ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static int maxRewardBones(String roomId, long accountId, String itemId, String slot, String status) {
        try (SqlSession session = DbInitializer.factory().openSession(true);
             java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                     "SELECT COALESCE(MAX(reward_bones), 0) FROM game_item_uses " +
                             "WHERE game_id = ? AND account_id = ? AND item_id = ? AND slot = ? AND status = ?")) {
            statement.setString(1, roomId);
            statement.setLong(2, accountId);
            statement.setString(3, itemId);
            statement.setString(4, slot);
            statement.setString(5, status);
            try (java.sql.ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void resetFactory() throws Exception {
        Field field = DbInitializer.class.getDeclaredField("FACTORY");
        field.setAccessible(true);
        SqlSessionFactory factory = (SqlSessionFactory) field.get(null);
        if (factory != null) {
            factory.getConfiguration().getEnvironment().getDataSource();
        }
        field.set(null, null);
    }
}
