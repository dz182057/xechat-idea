package cn.xeblog.server.action.handler;

import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.Game;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.commons.entity.game.GameDTO;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.cache.UserCache;
import cn.xeblog.server.game.minesweeper.MinesweeperService;
import cn.xeblog.server.game.turtlesoup.TurtleSoupService;

/**
 * @author anlingyi
 * @date 2020/8/14
 */
@DoAction(Action.GAME)
public class GameActionHandler extends AbstractGameActionHandler<GameDTO> {

    @Override
    protected void process(User user, GameRoom gameRoom, GameDTO body) {
        if (!gameRoom.isPlayerConnection(user)) {
            return;
        }
        if (gameRoom.getGame() == Game.TURTLE_SOUP) {
            TurtleSoupService.handle(user, gameRoom, body);
            return;
        }
        if (gameRoom.getGame() == Game.MINESWEEPER && MinesweeperService.handleRoom(user, gameRoom, body)) {
            return;
        }

        gameRoom.getUsers().forEach((k, v) -> {
            if (v.isConnection(user)) {
                return;
            }

            User player = UserCache.get(v.getChannelId());
            if (player != null) {
                player.send(ResponseBuilder.build(user, body, MessageType.GAME));
            }
        });
    }

}
