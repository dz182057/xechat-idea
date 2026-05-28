package cn.xeblog.server.action.handler.account;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.drawguess.AdminSaveDrawGuessWordsDTO;
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
 * 管理员保存你画我猜词库。
 */
@Slf4j
@DoAction(Action.ADMIN_SAVE_DRAW_GUESS_WORDS)
public class AdminSaveDrawGuessWordsActionHandler extends AbstractActionHandler<AdminSaveDrawGuessWordsDTO> {

    @Override
    protected void process(User user, AdminSaveDrawGuessWordsDTO body) {
        if (!user.isAdmin()) {
            user.send(ResponseBuilder.system("仅管理员可编辑你画我猜词库"));
            return;
        }
        try {
            List<DrawGuessWordDTO> words = DrawGuessWordService.save(body == null ? null : body.getWords());
            user.send(ResponseBuilder.build(null, words, MessageType.DRAW_GUESS_WORD_BANK));
            user.send(ResponseBuilder.system("你画我猜词库已保存"));
        } catch (IllegalArgumentException e) {
            user.send(ResponseBuilder.system(e.getMessage()));
        } catch (Exception e) {
            log.error("保存你画我猜词库异常", e);
            user.send(ResponseBuilder.system("保存你画我猜词库失败"));
        }
    }

}
