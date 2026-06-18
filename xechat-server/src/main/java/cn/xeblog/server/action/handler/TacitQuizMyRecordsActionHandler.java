package cn.xeblog.server.action.handler;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.tacitquiz.TacitQuizRecordDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.game.tacitquiz.TacitQuizService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 默契问答我的记录。
 */
@Slf4j
@DoAction(Action.TACIT_QUIZ_MY_RECORDS)
public class TacitQuizMyRecordsActionHandler extends AbstractActionHandler<Object> {

    @Override
    protected void process(User user, Object body) {
        try {
            List<TacitQuizRecordDTO> records = TacitQuizService.myRecords(user);
            user.send(ResponseBuilder.build(null, records, MessageType.TACIT_QUIZ_RECORDS));
        } catch (Exception e) {
            log.error("查询默契问答我的记录异常", e);
            user.send(ResponseBuilder.system("查询默契问答记录失败"));
        }
    }

}
