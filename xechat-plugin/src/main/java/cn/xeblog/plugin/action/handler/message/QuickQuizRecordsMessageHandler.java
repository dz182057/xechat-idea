package cn.xeblog.plugin.action.handler.message;

import cn.hutool.json.JSONUtil;
import cn.xeblog.commons.entity.Response;
import cn.xeblog.commons.entity.game.quickquiz.QuickQuizRecordDTO;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.plugin.action.GameAction;
import cn.xeblog.plugin.annotation.DoMessage;
import cn.xeblog.plugin.game.AbstractGame;
import cn.xeblog.plugin.game.quickquiz.QuickQuiz;

import java.util.List;

/**
 * 快问快答答题记录。
 */
@DoMessage(MessageType.QUICK_QUIZ_RECORDS)
public class QuickQuizRecordsMessageHandler extends AbstractMessageHandler<Object> {

    @Override
    protected void process(Response<Object> response) {
        AbstractGame action = GameAction.getAction();
        if (!(action instanceof QuickQuiz)) {
            return;
        }
        List<QuickQuizRecordDTO> records = JSONUtil.parseArray(JSONUtil.toJsonStr(response.getBody()))
                .toList(QuickQuizRecordDTO.class);
        ((QuickQuiz) action).onRecords(records);
    }

}
