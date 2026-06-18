package cn.xeblog.server.handler;

import cn.xeblog.commons.entity.game.CreateGameRoomDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.Game;
import cn.xeblog.commons.enums.MessageType;
import org.junit.Assert;
import org.junit.Test;

public class RequestHandlerQuickQuizTest {

    @Test
    public void quickQuizAndTacitQuizShouldBeDistinctGames() {
        Assert.assertEquals("默契问答", Game.TACIT_QUIZ.getName());
        Assert.assertEquals("快问快答", Game.QUICK_QUIZ.getName());

        CreateGameRoomDTO dto = new CreateGameRoomDTO(Game.QUICK_QUIZ, 8, "在线PK");
        dto.setQuickQuizQuestionCount(10);
        dto.setQuickQuizTimeLimitSeconds(15);
        dto.setQuickQuizEntryFee(20);

        Assert.assertEquals(8, dto.getNums());
        Assert.assertEquals(10, dto.getQuickQuizQuestionCount());
        Assert.assertEquals(15, dto.getQuickQuizTimeLimitSeconds());
        Assert.assertEquals(20, dto.getQuickQuizEntryFee());

        Assert.assertEquals(Action.TACIT_QUIZ_NEXT_QUESTION, Action.valueOf("TACIT_QUIZ_NEXT_QUESTION"));
        Assert.assertEquals(Action.QUICK_QUIZ_NEXT_QUESTION, Action.valueOf("QUICK_QUIZ_NEXT_QUESTION"));
        Assert.assertEquals(MessageType.TACIT_QUIZ_QUESTION, MessageType.valueOf("TACIT_QUIZ_QUESTION"));
        Assert.assertEquals(MessageType.QUICK_QUIZ_QUESTION, MessageType.valueOf("QUICK_QUIZ_QUESTION"));
    }
}
