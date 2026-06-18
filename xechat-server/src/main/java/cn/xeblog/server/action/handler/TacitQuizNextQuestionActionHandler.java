package cn.xeblog.server.action.handler;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.game.tacitquiz.TacitQuizNextQuestionDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.game.tacitquiz.TacitQuizService;
import lombok.extern.slf4j.Slf4j;

/**
 * 默契问答下一题。
 */
@Slf4j
@DoAction(Action.TACIT_QUIZ_NEXT_QUESTION)
public class TacitQuizNextQuestionActionHandler extends AbstractGameActionHandler<TacitQuizNextQuestionDTO> {

    @Override
    protected void process(User user, GameRoom gameRoom, TacitQuizNextQuestionDTO body) {
        try {
            TacitQuizService.nextQuestion(user, gameRoom);
        } catch (IllegalArgumentException e) {
            user.send(ResponseBuilder.system(e.getMessage() + "，当前剩余可用题数：" + TacitQuizService.availableCount(gameRoom)));
        } catch (Exception e) {
            log.error("默契问答出题异常", e);
            user.send(ResponseBuilder.system("默契问答出题失败"));
        }
    }

}
