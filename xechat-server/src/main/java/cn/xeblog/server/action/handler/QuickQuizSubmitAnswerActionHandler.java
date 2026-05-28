package cn.xeblog.server.action.handler;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.game.quickquiz.QuickQuizSubmitAnswerDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.game.quickquiz.QuickQuizService;
import lombok.extern.slf4j.Slf4j;

/**
 * 快问快答提交答案。
 */
@Slf4j
@DoAction(Action.QUICK_QUIZ_SUBMIT_ANSWER)
public class QuickQuizSubmitAnswerActionHandler extends AbstractGameActionHandler<QuickQuizSubmitAnswerDTO> {

    @Override
    protected void process(User user, GameRoom gameRoom, QuickQuizSubmitAnswerDTO body) {
        try {
            QuickQuizService.submitAnswer(user, gameRoom, body);
        } catch (IllegalArgumentException e) {
            user.send(ResponseBuilder.system(e.getMessage()));
        } catch (Exception e) {
            log.error("快问快答提交答案异常", e);
            user.send(ResponseBuilder.system("快问快答提交答案失败"));
        }
    }

}
