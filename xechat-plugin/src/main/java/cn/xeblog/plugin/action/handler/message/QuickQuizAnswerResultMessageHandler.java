package cn.xeblog.plugin.action.handler.message;

import cn.hutool.json.JSONUtil;
import cn.xeblog.commons.entity.Response;
import cn.xeblog.commons.entity.game.quickquiz.QuickQuizAnswerResultDTO;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.plugin.action.GameAction;
import cn.xeblog.plugin.annotation.DoMessage;
import cn.xeblog.plugin.game.AbstractGame;
import cn.xeblog.plugin.game.quickquiz.QuickQuiz;

/**
 * 快问快答答案揭示。
 */
@DoMessage(MessageType.QUICK_QUIZ_ANSWER_RESULT)
public class QuickQuizAnswerResultMessageHandler extends AbstractMessageHandler<Object> {

    @Override
    protected void process(Response<Object> response) {
        AbstractGame action = GameAction.getAction();
        if (!(action instanceof QuickQuiz)) {
            return;
        }
        Object body = response.getBody();
        QuickQuizAnswerResultDTO result = body instanceof QuickQuizAnswerResultDTO
                ? (QuickQuizAnswerResultDTO) body
                : JSONUtil.toBean(JSONUtil.toJsonStr(body), QuickQuizAnswerResultDTO.class);
        ((QuickQuiz) action).onResult(result);
    }

}
