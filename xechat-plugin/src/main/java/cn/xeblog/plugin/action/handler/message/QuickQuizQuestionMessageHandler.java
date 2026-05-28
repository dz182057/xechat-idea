package cn.xeblog.plugin.action.handler.message;

import cn.hutool.json.JSONUtil;
import cn.xeblog.commons.entity.Response;
import cn.xeblog.commons.entity.game.quickquiz.QuickQuizQuestionDTO;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.plugin.action.GameAction;
import cn.xeblog.plugin.annotation.DoMessage;
import cn.xeblog.plugin.game.AbstractGame;
import cn.xeblog.plugin.game.quickquiz.QuickQuiz;

/**
 * 快问快答题目下发。
 */
@DoMessage(MessageType.QUICK_QUIZ_QUESTION)
public class QuickQuizQuestionMessageHandler extends AbstractMessageHandler<Object> {

    @Override
    protected void process(Response<Object> response) {
        AbstractGame action = GameAction.getAction();
        if (!(action instanceof QuickQuiz)) {
            return;
        }
        Object body = response.getBody();
        QuickQuizQuestionDTO question = body instanceof QuickQuizQuestionDTO
                ? (QuickQuizQuestionDTO) body
                : JSONUtil.toBean(JSONUtil.toJsonStr(body), QuickQuizQuestionDTO.class);
        ((QuickQuiz) action).onQuestion(question);
    }

}
