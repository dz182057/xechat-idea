package cn.xeblog.server.action.handler.account;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.drawguess.DrawGuessWordDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.action.handler.AbstractActionHandler;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.game.drawguess.DrawGuessWordService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 管理员查看你画我猜词库。
 */
@Slf4j
@DoAction(Action.ADMIN_LIST_DRAW_GUESS_WORDS)
public class AdminListDrawGuessWordsActionHandler extends AbstractActionHandler<Object> {

    @Override
    protected void process(User user, Object body) {
        if (!user.isAdmin()) {
            user.send(ResponseBuilder.system("仅管理员可查看你画我猜词库"));
            return;
        }
        try {
            List<DrawGuessWordDTO> words = DrawGuessWordService.list();
            user.send(ResponseBuilder.build(null, words, MessageType.DRAW_GUESS_WORD_BANK));
        } catch (Exception e) {
            log.error("查询你画我猜词库异常", e);
            user.send(ResponseBuilder.system("查询你画我猜词库失败"));
        }
    }

}
