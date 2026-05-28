package cn.xeblog.server.action.handler;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.drawguess.DrawGuessWordDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.game.drawguess.DrawGuessWordService;
import lombok.extern.slf4j.Slf4j;

/**
 * 你画我猜随机取词。
 */
@Slf4j
@DoAction(Action.DRAW_GUESS_RANDOM_WORD)
public class DrawGuessRandomWordActionHandler extends AbstractActionHandler<Object> {

    @Override
    protected void process(User user, Object body) {
        try {
            DrawGuessWordDTO word = DrawGuessWordService.randomOne();
            if (word == null) {
                user.send(ResponseBuilder.system("你画我猜词库为空，请先联系管理员添加词库"));
                return;
            }
            user.send(ResponseBuilder.build(null, word, MessageType.DRAW_GUESS_WORD));
        } catch (Exception e) {
            log.error("随机获取你画我猜词异常", e);
            user.send(ResponseBuilder.system("随机获取你画我猜词失败"));
        }
    }

}
