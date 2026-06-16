package cn.xeblog.server.handler;

import cn.xeblog.commons.entity.game.GameDTO;
import cn.xeblog.commons.entity.game.minesweeper.MinesweeperDTO;
import cn.xeblog.commons.enums.Game;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;

public class RequestHandlerMinesweeperTest {

    @Test
    public void resolveGameSubClassShouldPreserveMinesweeperFields() throws Exception {
        Method method = RequestHandler.class.getDeclaredMethod("resolveGameSubClass", Game.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Class<? extends GameDTO> result =
                (Class<? extends GameDTO>) method.invoke(null, Game.MINESWEEPER);

        assertEquals(MinesweeperDTO.class, result);
    }

    @Test
    public void resolveBodyClassShouldUseMinesweeperDtoForSingleMinesweeperAction() throws Exception {
        Method method = RequestHandler.class.getDeclaredMethod("resolveBodyClass", cn.xeblog.commons.enums.Action.class, Object.class);
        method.setAccessible(true);

        Class<?> result = (Class<?>) method.invoke(null, cn.xeblog.commons.enums.Action.MINESWEEPER, null);

        assertEquals(MinesweeperDTO.class, result);
    }
}
