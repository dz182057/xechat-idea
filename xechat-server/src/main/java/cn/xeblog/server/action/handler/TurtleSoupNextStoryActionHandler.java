package cn.xeblog.server.action.handler;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.game.turtlesoup.TurtleSoupNextStoryDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.game.turtlesoup.TurtleSoupService;
import lombok.extern.slf4j.Slf4j;

/**
 * 海龟汤下一题。
 */
@Slf4j
@DoAction(Action.TURTLE_SOUP_NEXT_STORY)
public class TurtleSoupNextStoryActionHandler extends AbstractGameActionHandler<TurtleSoupNextStoryDTO> {

    @Override
    protected void process(User user, GameRoom gameRoom, TurtleSoupNextStoryDTO body) {
        try {
            TurtleSoupService.nextStory(user, gameRoom);
        } catch (IllegalArgumentException e) {
            user.send(ResponseBuilder.system(e.getMessage()));
        } catch (Exception e) {
            log.error("海龟汤出题异常", e);
            user.send(ResponseBuilder.system("海龟汤出题失败"));
        }
    }

}
