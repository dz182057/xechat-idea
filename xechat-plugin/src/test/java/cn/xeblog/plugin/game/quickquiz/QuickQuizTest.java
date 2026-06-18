package cn.xeblog.plugin.game.quickquiz;

import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.game.quickquiz.QuickQuizAnswerResultDTO;
import cn.xeblog.commons.entity.game.quickquiz.QuickQuizAnswerViewDTO;
import cn.xeblog.commons.entity.game.quickquiz.QuickQuizQuestionDTO;
import cn.xeblog.commons.entity.game.quickquiz.QuickQuizRecordDTO;
import cn.xeblog.commons.enums.Game;
import cn.xeblog.plugin.cache.DataCache;
import cn.xeblog.plugin.game.AbstractGame;
import cn.xeblog.plugin.game.GameRoomHandler;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class QuickQuizTest {

    private final Channel oldChannel = DataCache.channel;

    @After
    public void tearDown() {
        DataCache.channel = oldChannel;
    }

    @Test
    public void onQuestionShouldResetResultAndCreateEnabledOptionsWithoutTimerText() throws Exception {
        QuickQuiz game = startedGame();
        QuickQuizQuestionDTO question = question("首都在哪里？", "北京", "上海");

        onEdt(() -> resultArea(game).setText("上一题结果"));
        game.onQuestion(question);
        flushEdt();

        Assert.assertEquals("", resultArea(game).getText());
        List<JButton> optionButtons = optionButtons(game);
        Assert.assertEquals(2, optionButtons.size());
        Assert.assertEquals("北京", optionButtons.get(0).getText());
        Assert.assertEquals("上海", optionButtons.get(1).getText());
        Assert.assertTrue(optionButtons.get(0).isEnabled());
        Assert.assertTrue(optionButtons.get(1).isEnabled());
        assertGameTextNotContains(game, "倒计时", "超时", "已超时");
    }

    @Test
    public void submitShouldDisableOptionsAndShowWaitingForReveal() throws Exception {
        DataCache.channel = new EmbeddedChannel();
        QuickQuiz game = startedGame();
        setRoom(game, room());
        game.onQuestion(question("首都在哪里？", "北京", "上海"));
        flushEdt();

        JButton firstOption = optionButtons(game).get(0);
        onEdt(firstOption::doClick);
        flushEdt();

        for (JButton button : optionButtons(game)) {
            Assert.assertFalse(button.isEnabled());
        }
        Assert.assertEquals("已提交，等待双方答案揭示...", resultArea(game).getText());
    }

    @Test
    public void unfinishedResultShouldWaitForNextQuestionWithoutReadyNextText() throws Exception {
        QuickQuiz game = startedGame();
        QuickQuizQuestionDTO question = question("首都在哪里？", "北京", "上海");
        game.onQuestion(question);
        flushEdt();

        QuickQuizAnswerResultDTO result = new QuickQuizAnswerResultDTO(
                "room-1",
                question,
                Arrays.asList(new QuickQuizAnswerViewDTO("p1", "我", 0, "北京", 1L, true, false, 1, 1)),
                Arrays.asList(),
                1,
                2,
                false,
                0,
                0,
                false);
        game.onResult(result);
        flushEdt();

        Assert.assertTrue(resultArea(game).getText().contains("等待下一题..."));
        assertGameTextNotContains(game, "准备下一题", "ready");
        for (JButton button : allButtons(game.getComponent())) {
            Assert.assertNotEquals("准备下一题", button.getText());
        }
    }

    @Test
    public void quickQuizShouldNotKeepTimerOrReadyNextArtifacts() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/cn/xeblog/plugin/game/quickquiz/QuickQuiz.java")), StandardCharsets.UTF_8);

        for (String forbidden : Arrays.asList(
                "timer", "timerLabel", "readyButton", "readyNext", "PLAYER_READY", "已超时", "准备下一题")) {
            Assert.assertFalse("QuickQuiz.java 不应残留：" + forbidden, source.contains(forbidden));
        }
        for (Field field : QuickQuiz.class.getDeclaredFields()) {
            String name = field.getName();
            Assert.assertFalse(name.contains("timer"));
            Assert.assertNotEquals("timerLabel", name);
            Assert.assertNotEquals("readyButton", name);
        }
        for (Method method : QuickQuiz.class.getDeclaredMethods()) {
            Assert.assertNotEquals("readyNext", method.getName());
        }
    }

    @Test
    public void groupRecordsShouldUseOpponentAndFallbackToUnknownOpponent() {
        QuickQuizRecordDTO aliceRecord = record("room-1", "alice-key", "Alice");
        QuickQuizRecordDTO oldRecord = record("room-2", null, null);
        QuickQuizRecordDTO anotherAliceRecord = record("room-3", "alice-key", "Alice");

        List<QuickQuiz.RecordGroup> groups = QuickQuiz.groupRecordsByOpponent(
                Arrays.asList(aliceRecord, oldRecord, anotherAliceRecord));

        Assert.assertEquals(2, groups.size());
        Assert.assertEquals("Alice", groups.get(0).getOpponentName());
        Assert.assertEquals(Arrays.asList(aliceRecord, anotherAliceRecord), groups.get(0).getRecords());
        Assert.assertEquals("未知对手", groups.get(1).getOpponentName());
        Assert.assertEquals(Arrays.asList(oldRecord), groups.get(1).getRecords());
    }

    private QuickQuiz startedGame() throws Exception {
        QuickQuiz game = new QuickQuiz();
        onEdt(game::start);
        return game;
    }

    private GameRoom room() {
        GameRoom room = new GameRoom();
        room.setId("room-1");
        room.setGame(Game.QUICK_QUIZ);
        room.setNums(2);
        return room;
    }

    private QuickQuizQuestionDTO question(String text, String... options) {
        return new QuickQuizQuestionDTO(1L, text, Arrays.asList(options), 0, 1, 0L, 0L, 1, 2);
    }

    private QuickQuizRecordDTO record(String roomId, String opponentKey, String opponentName) {
        QuickQuizRecordDTO record = new QuickQuizRecordDTO();
        record.setRoomId(roomId);
        record.setOpponentKey(opponentKey);
        record.setOpponentName(opponentName);
        return record;
    }

    private JTextArea resultArea(QuickQuiz game) {
        return fieldValue(game, "resultArea", JTextArea.class);
    }

    private List<JButton> optionButtons(QuickQuiz game) {
        JPanel optionPanel = fieldValue(game, "optionPanel", JPanel.class);
        return allButtons(optionPanel);
    }

    private List<JButton> allButtons(Component component) {
        List<JButton> buttons = new ArrayList<>();
        collect(component, JButton.class, buttons);
        return buttons;
    }

    private void assertGameTextNotContains(QuickQuiz game, String... forbiddenTexts) {
        String text = componentText(game.getComponent());
        for (String forbiddenText : forbiddenTexts) {
            Assert.assertFalse("界面不应出现：" + forbiddenText, text.contains(forbiddenText));
        }
    }

    private String componentText(Component component) {
        StringBuilder sb = new StringBuilder();
        collectText(component, sb);
        return sb.toString();
    }

    private void collectText(Component component, StringBuilder sb) {
        if (component instanceof JLabel) {
            sb.append(((JLabel) component).getText()).append('\n');
        } else if (component instanceof AbstractButton) {
            sb.append(((AbstractButton) component).getText()).append('\n');
        } else if (component instanceof JTextComponent) {
            sb.append(((JTextComponent) component).getText()).append('\n');
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                collectText(child, sb);
            }
        }
    }

    private <T extends Component> void collect(Component component, Class<T> type, List<T> output) {
        if (type.isInstance(component)) {
            output.add(type.cast(component));
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                collect(child, type, output);
            }
        }
    }

    private void setRoom(QuickQuiz game, GameRoom room) throws Exception {
        Field handlerField = AbstractGame.class.getDeclaredField("gameRoomHandler");
        handlerField.setAccessible(true);
        GameRoomHandler handler = (GameRoomHandler) handlerField.get(game);
        Field roomField = GameRoomHandler.class.getDeclaredField("gameRoom");
        roomField.setAccessible(true);
        roomField.set(handler, room);
    }

    private <T> T fieldValue(QuickQuiz game, String fieldName, Class<T> type) {
        try {
            Field field = QuickQuiz.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return type.cast(field.get(game));
        } catch (Exception e) {
            throw new AssertionError("读取 QuickQuiz 测试字段失败：" + fieldName, e);
        }
    }

    private void onEdt(Runnable runnable) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
            return;
        }
        SwingUtilities.invokeAndWait(runnable);
    }

    private void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            // 排空 EDT 上通过 invokeLater 投递的界面更新。
        });
    }
}
