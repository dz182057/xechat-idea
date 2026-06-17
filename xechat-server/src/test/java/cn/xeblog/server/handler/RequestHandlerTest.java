package cn.xeblog.server.handler;

import cn.xeblog.commons.entity.game.dograce.DogRaceDTO;
import cn.xeblog.commons.enums.Game;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Method;

public class RequestHandlerTest {

    @Test
    public void resolveGameSubClassShouldKeepDogRaceRequestFields() throws Exception {
        Method method = RequestHandler.class.getDeclaredMethod("resolveGameSubClass", Game.class);
        method.setAccessible(true);

        Object result = method.invoke(null, Game.DOG_RACE);

        Assert.assertEquals(DogRaceDTO.class, result);
    }
}
