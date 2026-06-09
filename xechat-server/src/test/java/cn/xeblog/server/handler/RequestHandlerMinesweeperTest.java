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
}
