package cn.xeblog.server.action.handler;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.minesweeper.MinesweeperDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.game.minesweeper.MinesweeperService;

@DoAction(Action.MINESWEEPER)
public class MinesweeperActionHandler extends AbstractActionHandler<MinesweeperDTO> {

    @Override
    protected void process(User user, MinesweeperDTO body) {
        MinesweeperService.handleSingle(user, body);
    }
}
