package cn.xeblog.server.action.handler;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.gobang.GobangOracleRequestDTO;
import cn.xeblog.commons.entity.game.gobang.GobangOracleResponseDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.game.gobang.GobangOracleService;

/**
 * 五子棋天元罗盘推荐。
 */
@DoAction(Action.GOBANG_ORACLE)
public class GobangOracleActionHandler extends AbstractActionHandler<GobangOracleRequestDTO> {

    @Override
    protected void process(User user, GobangOracleRequestDTO body) {
        GobangOracleResponseDTO response = GobangOracleService.suggest(body);
        user.send(ResponseBuilder.build(user, response, MessageType.GOBANG_ORACLE));
    }

}
