package cn.xeblog.plugin.action.handler.message;

import cn.hutool.json.JSONUtil;
import cn.xeblog.commons.entity.Response;
import cn.xeblog.commons.entity.game.drawguess.DrawGuessWordDTO;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.plugin.action.GameAction;
import cn.xeblog.plugin.annotation.DoMessage;
import cn.xeblog.plugin.game.AbstractGame;
import cn.xeblog.plugin.game.drawguess.DrawGuess;

/**
 * 你画我猜随机词响应。
 */
@DoMessage(MessageType.DRAW_GUESS_WORD)
public class DrawGuessWordMessageHandler extends AbstractMessageHandler<Object> {

    @Override
    protected void process(Response<Object> response) {
        AbstractGame action = GameAction.getAction();
        if (!(action instanceof DrawGuess)) {
            return;
        }
        Object body = response.getBody();
        DrawGuessWordDTO word = body instanceof DrawGuessWordDTO
                ? (DrawGuessWordDTO) body
                : JSONUtil.toBean(JSONUtil.toJsonStr(body), DrawGuessWordDTO.class);
        ((DrawGuess) action).onRandomWord(word);
    }

}
