package cn.xeblog.plugin.action.handler.message;

import cn.hutool.json.JSONUtil;
import cn.xeblog.commons.entity.Response;
import cn.xeblog.commons.entity.game.tacitquiz.TacitQuizAnswerResultDTO;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.plugin.action.GameAction;
import cn.xeblog.plugin.annotation.DoMessage;
import cn.xeblog.plugin.game.AbstractGame;
import cn.xeblog.plugin.game.tacitquiz.TacitQuiz;

/**
 * 默契问答答案揭示。
 */
@DoMessage(MessageType.TACIT_QUIZ_ANSWER_RESULT)
public class TacitQuizAnswerResultMessageHandler extends AbstractMessageHandler<Object> {

    @Override
    protected void process(Response<Object> response) {
        AbstractGame action = GameAction.getAction();
        if (!(action instanceof TacitQuiz)) {
            return;
        }
        Object body = response.getBody();
        TacitQuizAnswerResultDTO result = body instanceof TacitQuizAnswerResultDTO
                ? (TacitQuizAnswerResultDTO) body
                : JSONUtil.toBean(JSONUtil.toJsonStr(body), TacitQuizAnswerResultDTO.class);
        ((TacitQuiz) action).onResult(result);
    }

}
