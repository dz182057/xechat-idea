package cn.xeblog.server.game.dogbattle;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.game.dogbattle.DogBattleDTO;
import cn.xeblog.commons.enums.Game;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.*;

public class DogBattleServiceTest {

    private final GameRoom room = createRoom();
    private final User left = createUser("left-channel", 1001L, "左边狗狗");
    private final User right = createUser("right-channel", 1002L, "右边狗狗");

    @After
    public void tearDown() {
        DogBattleService.clearRoom(room.getId());
    }

    @Test
    public void startMatchAfterAllPlayersEnteredGame() {
        room.addUser(left);
        room.addUser(right);

        assertNull(DogBattleService.playerStarted(room, left));

        DogBattleDTO snapshot = DogBattleService.playerStarted(room, right);

        assertNotNull(snapshot);
        assertEquals("MATCH_START", snapshot.getEvent());
        assertEquals("playing", snapshot.getPhase());
        assertEquals(1, snapshot.getRoundNo());
        assertEquals(1, snapshot.getTurnNo());
        assertEquals(2, snapshot.getPlayers().size());
        assertEquals(100, snapshot.getPlayers().get(0).getHp());
        assertEquals(100, snapshot.getPlayers().get(1).getHp());
        assertEquals(snapshot.getPlayers().get(0).getPlayerKey(), snapshot.getCurrentPlayerKey());
    }

    @Test
    public void doNotStartMatchBeforeRoomHasTwoPlayers() {
        room.addUser(left);

        assertNull(DogBattleService.playerStarted(room, left));
    }

    @Test
    public void doNotStartMatchWhenRoomHasMoreThanTwoPlayers() {
        User third = createUser("third-channel", 1003L, "第三只狗狗");
        room.setNums(3);
        room.addUser(left);
        room.addUser(right);
        room.addUser(third);

        DogBattleService.playerStarted(room, left);
        DogBattleService.playerStarted(room, right);

        assertNull(DogBattleService.playerStarted(room, third));
    }

    @Test
    public void rejectInputFromNonCurrentPlayer() {
        room.addUser(left);
        room.addUser(right);
        DogBattleService.playerStarted(room, left);
        DogBattleService.playerStarted(room, right);

        DogBattleDTO input = new DogBattleDTO();
        input.setEvent("PLAYER_INPUT");
        input.setAngle(45);
        input.setPower(100);

        assertNull(DogBattleService.handleInput(right, room, input));
    }

    @Test
    public void currentPlayerInputProducesAuthoritativeTurnResult() {
        room.addUser(left);
        room.addUser(right);
        DogBattleDTO snapshot = DogBattleService.playerStarted(room, left);
        if (snapshot == null) {
            snapshot = DogBattleService.playerStarted(room, right);
        }
        String leftKey = left.getIdentityKey();
        String rightKey = right.getIdentityKey();

        DogBattleDTO input = new DogBattleDTO();
        input.setEvent("PLAYER_INPUT");
        input.setAngle(45);
        input.setPower(100);

        DogBattleDTO result = DogBattleService.handleInput(left, room, input);

        assertNotNull(result);
        assertEquals("TURN_RESULT", result.getEvent());
        assertEquals(leftKey, result.getActorPlayerKey());
        assertEquals(rightKey, result.getNextPlayerKey());
        assertEquals(2, result.getTurnNo());
        assertFalse(result.isMatchOver());
        assertFalse(result.getTrajectory().isEmpty());
        assertEquals("DOG", result.getHit().getTargetType());
        assertEquals(rightKey, result.getHit().getTargetId());
        assertEquals(24, result.getHit().getDamage());
        assertEquals(76, findPlayer(result, rightKey).getHp());
    }

    @Test
    public void clearRoomRemovesBattleState() {
        room.addUser(left);
        room.addUser(right);
        DogBattleService.playerStarted(room, left);
        DogBattleService.playerStarted(room, right);

        DogBattleService.clearRoom(room.getId());

        DogBattleDTO input = new DogBattleDTO();
        input.setEvent("PLAYER_INPUT");
        input.setAngle(45);
        input.setPower(100);
        assertNull(DogBattleService.handleInput(left, room, input));
    }

    private static DogBattleDTO.DogBattlePlayerDTO findPlayer(DogBattleDTO result, String playerKey) {
        return result.getPlayers().stream()
                .filter(player -> playerKey.equals(player.getPlayerKey()))
                .findFirst()
                .orElseThrow(AssertionError::new);
    }

    private static GameRoom createRoom() {
        GameRoom room = new GameRoom();
        room.setId("dog-battle-test-room");
        room.setGame(Game.DOG_BATTLE);
        room.setNums(2);
        room.setDogBattleRoundCount(3);
        room.setDogBattleAllowSkill(true);
        return room;
    }

    private static User createUser(String channelId, long accountId, String nickname) {
        User user = new User();
        user.setId(channelId);
        user.setAccountId(accountId);
        user.setAccount("acct" + accountId);
        user.setNickname(nickname);
        user.setUsername(nickname);
        return user;
    }
}
