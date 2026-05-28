package cn.xeblog.server.action.handler.account;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.turtlesoup.TurtleSoupStoryDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.action.handler.AbstractActionHandler;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.game.turtlesoup.TurtleSoupService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 管理员查看海龟汤题库。
 */
@Slf4j
@DoAction(Action.ADMIN_LIST_TURTLE_SOUP_STORIES)
public class AdminListTurtleSoupStoriesActionHandler extends AbstractActionHandler<Object> {

    @Override
    protected void process(User user, Object body) {
        if (!user.isAdmin()) {
            user.send(ResponseBuilder.system("仅管理员可查看海龟汤题库"));
            return;
        }
        try {
            List<TurtleSoupStoryDTO> stories = TurtleSoupService.listStories();
            user.send(ResponseBuilder.build(null, stories, MessageType.TURTLE_SOUP_STORY_BANK));
        } catch (Exception e) {
            log.error("查询海龟汤题库异常", e);
            user.send(ResponseBuilder.system("查询海龟汤题库失败"));
        }
    }

}
