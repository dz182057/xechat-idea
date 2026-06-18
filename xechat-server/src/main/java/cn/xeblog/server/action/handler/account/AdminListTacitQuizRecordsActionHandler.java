package cn.xeblog.server.action.handler.account;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.tacitquiz.TacitQuizRecordDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.action.handler.AbstractActionHandler;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.game.tacitquiz.TacitQuizService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 管理员查看全部默契问答答题记录。
 */
@Slf4j
@DoAction(Action.ADMIN_LIST_TACIT_QUIZ_RECORDS)
public class AdminListTacitQuizRecordsActionHandler extends AbstractActionHandler<Object> {

    @Override
    protected void process(User user, Object body) {
        if (!user.isAdmin()) {
            user.send(ResponseBuilder.system("仅管理员可查看全部默契问答记录"));
            return;
        }
        try {
            List<TacitQuizRecordDTO> records = TacitQuizService.allRecords();
            user.send(ResponseBuilder.build(null, records, MessageType.TACIT_QUIZ_RECORDS));
        } catch (Exception e) {
            log.error("查询默契问答记录异常", e);
            user.send(ResponseBuilder.system("查询默契问答记录失败"));
        }
    }

}
