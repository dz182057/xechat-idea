package cn.xeblog.plugin.action.handler.message;

import cn.hutool.json.JSONUtil;
import cn.xeblog.commons.entity.game.turtlesoup.TurtleSoupDTO;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.plugin.action.GameAction;
import cn.xeblog.commons.entity.game.GameDTO;
import cn.xeblog.commons.entity.Response;
import cn.xeblog.plugin.annotation.DoMessage;
import cn.xeblog.plugin.game.AbstractGame;
import cn.xeblog.plugin.game.turtlesoup.TurtleSoup;

/**
 * @author anlingyi
 * @date 2020/8/19
 */
@DoMessage(MessageType.GAME)
public class GameMessageHandler extends AbstractGameMessageHandler<GameDTO> {

    @Override
    protected void process(Response<GameDTO> response) {
        AbstractGame action = GameAction.getAction();
        if (action instanceof TurtleSoup) {
            Object body = response.getBody();
            TurtleSoupDTO dto = body instanceof TurtleSoupDTO
                    ? (TurtleSoupDTO) body
                    : JSONUtil.toBean(JSONUtil.toJsonStr(body), TurtleSoupDTO.class);
            ((TurtleSoup) action).handle(dto);
            return;
        }
        GameAction.handle(response);
    }

}
