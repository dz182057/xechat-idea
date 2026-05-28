package cn.xeblog.server.action.handler.account;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.quickquiz.QuickQuizRecordDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.action.handler.AbstractActionHandler;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.game.quickquiz.QuickQuizService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 管理员查看全部快问快答答题记录。
 */
@Slf4j
@DoAction(Action.ADMIN_LIST_QUICK_QUIZ_RECORDS)
public class AdminListQuickQuizRecordsActionHandler extends AbstractActionHandler<Object> {

    @Override
    protected void process(User user, Object body) {
        if (!user.isAdmin()) {
            user.send(ResponseBuilder.system("仅管理员可查看全部快问快答记录"));
            return;
        }
        try {
            List<QuickQuizRecordDTO> records = QuickQuizService.allRecords();
            user.send(ResponseBuilder.build(null, records, MessageType.QUICK_QUIZ_RECORDS));
        } catch (Exception e) {
            log.error("查询快问快答记录异常", e);
            user.send(ResponseBuilder.system("查询快问快答记录失败"));
        }
    }

}
