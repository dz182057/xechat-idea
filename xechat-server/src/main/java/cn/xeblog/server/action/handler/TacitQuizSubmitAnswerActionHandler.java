package cn.xeblog.server.action.handler;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.game.tacitquiz.TacitQuizSubmitAnswerDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.game.tacitquiz.TacitQuizService;
import lombok.extern.slf4j.Slf4j;

/**
 * 默契问答提交答案。
 */
@Slf4j
@DoAction(Action.TACIT_QUIZ_SUBMIT_ANSWER)
public class TacitQuizSubmitAnswerActionHandler extends AbstractGameActionHandler<TacitQuizSubmitAnswerDTO> {

    @Override
    protected void process(User user, GameRoom gameRoom, TacitQuizSubmitAnswerDTO body) {
        try {
            TacitQuizService.submitAnswer(user, gameRoom, body);
        } catch (IllegalArgumentException e) {
            user.send(ResponseBuilder.system(e.getMessage()));
        } catch (Exception e) {
            log.error("默契问答提交答案异常", e);
            user.send(ResponseBuilder.system("默契问答提交答案失败"));
        }
    }

}
