package cn.xeblog.plugin.action.handler.message;

import cn.hutool.json.JSONUtil;
import cn.xeblog.commons.entity.Response;
import cn.xeblog.commons.entity.game.tacitquiz.TacitQuizQuestionDTO;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.plugin.action.GameAction;
import cn.xeblog.plugin.annotation.DoMessage;
import cn.xeblog.plugin.game.AbstractGame;
import cn.xeblog.plugin.game.tacitquiz.TacitQuiz;

/**
 * 默契问答题目下发。
 */
@DoMessage(MessageType.TACIT_QUIZ_QUESTION)
public class TacitQuizQuestionMessageHandler extends AbstractMessageHandler<Object> {

    @Override
    protected void process(Response<Object> response) {
        AbstractGame action = GameAction.getAction();
        if (!(action instanceof TacitQuiz)) {
            return;
        }
        Object body = response.getBody();
        TacitQuizQuestionDTO question = body instanceof TacitQuizQuestionDTO
                ? (TacitQuizQuestionDTO) body
                : JSONUtil.toBean(JSONUtil.toJsonStr(body), TacitQuizQuestionDTO.class);
        ((TacitQuiz) action).onQuestion(question);
    }

}
