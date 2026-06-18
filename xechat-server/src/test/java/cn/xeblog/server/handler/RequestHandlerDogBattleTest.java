package cn.xeblog.server.handler;

import cn.xeblog.commons.entity.game.CreateGameRoomDTO;
import cn.xeblog.commons.entity.game.dogbattle.DogBattleDTO;
import cn.xeblog.commons.entity.game.GameDTO;
import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.enums.Game;
import cn.xeblog.server.action.handler.GameRoomCreateActionHandler;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RequestHandlerDogBattleTest {

    @Test
    public void dogBattleShouldBeKnownGameAndPreserveDogBattleFields() throws Exception {
        assertEquals("狗狗大战", Game.DOG_BATTLE.getName());
        assertTrue(Game.DOG_BATTLE.isRequiredLogin());

        Method method = RequestHandler.class.getDeclaredMethod("resolveGameSubClass", Game.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Class<? extends GameDTO> result =
                (Class<? extends GameDTO>) method.invoke(null, Game.DOG_BATTLE);

        assertEquals(DogBattleDTO.class, result);
    }

    @Test
    public void dogBattleRoomConfigShouldBePreservedOnDtoAndRoom() {
        CreateGameRoomDTO dto = new CreateGameRoomDTO(Game.DOG_BATTLE, 2, "在线PK");
        dto.setDogBattleRoundCount(3);
        dto.setDogBattleAllowSkill(true);

        assertEquals(3, dto.getDogBattleRoundCount());
        assertTrue(dto.getDogBattleAllowSkill());

        GameRoom room = new GameRoom();
        room.setGame(Game.DOG_BATTLE);
        room.setDogBattleRoundCount(dto.getDogBattleRoundCount());
        room.setDogBattleAllowSkill(dto.getDogBattleAllowSkill());

        assertEquals(Game.DOG_BATTLE, room.getGame());
        assertEquals(3, room.getDogBattleRoundCount());
        assertTrue(room.isDogBattleAllowSkill());
    }

    @Test
    public void dogBattleRoomConfigShouldNormalizeInvalidValues() {
        CreateGameRoomDTO dto = new CreateGameRoomDTO(Game.DOG_BATTLE, 2, "在线PK");

        assertEquals(3, GameRoomCreateActionHandler.resolveDogBattleRoundCount(dto));
        assertTrue(GameRoomCreateActionHandler.resolveDogBattleAllowSkill(dto));

        dto.setDogBattleRoundCount(5);
        dto.setDogBattleAllowSkill(false);

        assertEquals(5, GameRoomCreateActionHandler.resolveDogBattleRoundCount(dto));
        assertTrue(!GameRoomCreateActionHandler.resolveDogBattleAllowSkill(dto));
    }
}
