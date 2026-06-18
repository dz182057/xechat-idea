package cn.xeblog.plugin.action.handler.message;

import cn.hutool.json.JSONUtil;
import cn.xeblog.commons.entity.Response;
import cn.xeblog.commons.entity.game.tacitquiz.TacitQuizRecordDTO;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.plugin.action.GameAction;
import cn.xeblog.plugin.annotation.DoMessage;
import cn.xeblog.plugin.game.AbstractGame;
import cn.xeblog.plugin.game.tacitquiz.TacitQuiz;

import java.util.List;

/**
 * 默契问答答题记录。
 */
@DoMessage(MessageType.TACIT_QUIZ_RECORDS)
public class TacitQuizRecordsMessageHandler extends AbstractMessageHandler<Object> {

    @Override
    protected void process(Response<Object> response) {
        AbstractGame action = GameAction.getAction();
        if (!(action instanceof TacitQuiz)) {
            return;
        }
        List<TacitQuizRecordDTO> records = JSONUtil.parseArray(JSONUtil.toJsonStr(response.getBody()))
                .toList(TacitQuizRecordDTO.class);
        ((TacitQuiz) action).onRecords(records);
    }

}
