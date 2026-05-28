package cn.xeblog.server.action.handler.account;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.turtlesoup.AdminSaveTurtleSoupStoriesDTO;
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
 * 管理员保存海龟汤题库。
 */
@Slf4j
@DoAction(Action.ADMIN_SAVE_TURTLE_SOUP_STORIES)
public class AdminSaveTurtleSoupStoriesActionHandler extends AbstractActionHandler<AdminSaveTurtleSoupStoriesDTO> {

    @Override
    protected void process(User user, AdminSaveTurtleSoupStoriesDTO body) {
        if (!user.isAdmin()) {
            user.send(ResponseBuilder.system("仅管理员可编辑海龟汤题库"));
            return;
        }
        try {
            List<TurtleSoupStoryDTO> stories = TurtleSoupService.saveStories(body == null ? null : body.getStories());
            user.send(ResponseBuilder.build(null, stories, MessageType.TURTLE_SOUP_STORY_BANK));
            user.send(ResponseBuilder.system("海龟汤题库已保存"));
        } catch (IllegalArgumentException e) {
            user.send(ResponseBuilder.system(e.getMessage()));
        } catch (Exception e) {
            log.error("保存海龟汤题库异常", e);
            user.send(ResponseBuilder.system("保存海龟汤题库失败"));
        }
    }

}
