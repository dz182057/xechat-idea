package cn.xeblog.server.action.handler;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.gobang.GobangHistoryReportDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.game.gobang.GobangHistoryService;

/**
 * 五子棋本地棋局留痕。
 */
@DoAction(Action.GOBANG_HISTORY)
public class GobangHistoryActionHandler extends AbstractActionHandler<GobangHistoryReportDTO> {

    @Override
    protected void process(User user, GobangHistoryReportDTO body) {
        GobangHistoryService.recordClientReport(user, body);
    }

}
