package cn.xeblog.plugin.action.handler.message;

import cn.xeblog.commons.entity.Response;
import cn.xeblog.commons.entity.game.dograce.DogRaceDTO;
import cn.xeblog.commons.enums.Game;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.plugin.action.GameAction;
import cn.xeblog.plugin.game.dograce.DogRace;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.util.HashMap;
import java.util.Map;

public class GameMessageHandlerTest {

    @After
    public void tearDown() {
        GameAction.clean();
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void shouldConvertDogRaceBodyBeforeDispatch() throws Exception {
        GameAction.setGame(Game.DOG_RACE);
        DogRace action = (DogRace) GameAction.create();
        Map<String, Object> body = new HashMap<>();
        body.put("roomId", "race-room");
        body.put("game", "DOG_RACE");
        body.put("event", "ROLL");
        body.put("mode", "pure_betting");
        body.put("phase", "running");
        body.put("legNo", 1);
        body.put("broadcast", "测试播报");

        new GameMessageHandler().handle(new Response(null, body, MessageType.GAME));
        SwingUtilities.invokeAndWait(() -> {
        });

        Assert.assertNotNull(action.getLatestForTest());
        Assert.assertEquals(DogRaceDTO.Event.ROLL, action.getLatestForTest().getEvent());
        Assert.assertEquals("测试播报", action.getLatestForTest().getBroadcast());
    }
}
