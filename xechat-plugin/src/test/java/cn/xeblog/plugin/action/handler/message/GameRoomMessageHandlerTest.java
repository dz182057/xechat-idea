package cn.xeblog.plugin.action.handler.message;

import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.game.GameRoomMsgDTO;
import cn.xeblog.commons.enums.Game;
import cn.xeblog.plugin.action.GameAction;
import cn.xeblog.plugin.cache.DataCache;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class GameRoomMessageHandlerTest {

    @After
    public void tearDown() {
        GameAction.clean();
    }

    @Test
    public void shouldCreateGameFromAcceptedInviteResultRoom() {
        DataCache.username = "XHCL";
        GameRoom room = new GameRoom();
        room.setId("room-1");
        room.setGame(Game.GOBANG);
        room.setNums(2);

        GameRoomMsgDTO msg = new GameRoomMsgDTO("room-1", null, GameRoomMsgDTO.MsgType.PLAYER_INVITE_RESULT, null);
        new GameRoomMessageHandler().initGameAction(msg, room);

        assertEquals(Game.GOBANG, GameAction.getGame());
        assertEquals("room-1", GameAction.getRoomId());
        assertNotNull(GameAction.create());
    }
}
