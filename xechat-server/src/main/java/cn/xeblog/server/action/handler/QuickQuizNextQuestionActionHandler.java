package cn.xeblog.server.action.handler;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.game.quickquiz.QuickQuizNextQuestionDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.game.quickquiz.QuickQuizService;
import lombok.extern.slf4j.Slf4j;

/**
 * 快问快答下一题。
 */
@Slf4j
@DoAction(Action.QUICK_QUIZ_NEXT_QUESTION)
public class QuickQuizNextQuestionActionHandler extends AbstractGameActionHandler<QuickQuizNextQuestionDTO> {

    @Override
    protected void process(User user, GameRoom gameRoom, QuickQuizNextQuestionDTO body) {
        try {
            QuickQuizService.nextQuestion(user, gameRoom);
        } catch (IllegalArgumentException e) {
            user.send(ResponseBuilder.system(e.getMessage() + "，当前剩余可用题数：" + QuickQuizService.availableCount(gameRoom)));
        } catch (Exception e) {
            log.error("快问快答出题异常", e);
            user.send(ResponseBuilder.system("快问快答出题失败"));
        }
    }

}
