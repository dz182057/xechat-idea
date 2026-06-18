package cn.xeblog.server.action.handler;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.GameDTO;
import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.game.tacitquiz.TacitQuizAvailableCountDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.game.tacitquiz.TacitQuizService;

/**
 * 默契问答剩余可用题数。
 */
@DoAction(Action.TACIT_QUIZ_AVAILABLE_COUNT)
public class TacitQuizAvailableCountActionHandler extends AbstractGameActionHandler<GameDTO> {

    @Override
    protected void process(User user, GameRoom gameRoom, GameDTO body) {
        user.send(ResponseBuilder.build(null,
                new TacitQuizAvailableCountDTO(gameRoom.getId(), TacitQuizService.availableCount(gameRoom),
                        gameRoom.getTacitQuizQuestionCount()),
                MessageType.TACIT_QUIZ_AVAILABLE_COUNT));
    }

}
