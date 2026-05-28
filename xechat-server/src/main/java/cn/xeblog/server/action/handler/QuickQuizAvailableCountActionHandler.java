package cn.xeblog.server.action.handler;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.GameDTO;
import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.game.quickquiz.QuickQuizAvailableCountDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.game.quickquiz.QuickQuizService;

/**
 * 快问快答剩余可用题数。
 */
@DoAction(Action.QUICK_QUIZ_AVAILABLE_COUNT)
public class QuickQuizAvailableCountActionHandler extends AbstractGameActionHandler<GameDTO> {

    @Override
    protected void process(User user, GameRoom gameRoom, GameDTO body) {
        user.send(ResponseBuilder.build(null,
                new QuickQuizAvailableCountDTO(gameRoom.getId(), QuickQuizService.availableCount(gameRoom),
                        gameRoom.getQuickQuizQuestionCount()),
                MessageType.QUICK_QUIZ_AVAILABLE_COUNT));
    }

}
