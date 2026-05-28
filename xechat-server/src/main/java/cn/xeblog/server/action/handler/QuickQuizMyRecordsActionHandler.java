package cn.xeblog.server.action.handler;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.quickquiz.QuickQuizRecordDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.game.quickquiz.QuickQuizService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 快问快答我的记录。
 */
@Slf4j
@DoAction(Action.QUICK_QUIZ_MY_RECORDS)
public class QuickQuizMyRecordsActionHandler extends AbstractActionHandler<Object> {

    @Override
    protected void process(User user, Object body) {
        try {
            List<QuickQuizRecordDTO> records = QuickQuizService.myRecords(user);
            user.send(ResponseBuilder.build(null, records, MessageType.QUICK_QUIZ_RECORDS));
        } catch (Exception e) {
            log.error("查询快问快答我的记录异常", e);
            user.send(ResponseBuilder.system("查询快问快答记录失败"));
        }
    }

}
