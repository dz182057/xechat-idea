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
        assertNotNull(result.getNextWind());
        assertEquals(2, result.getNextWind().getTurnNo());
    }

    @Test
    public void matchStartIncludesAuthoritativeWindAndObstacles() {
        room.addUser(left);
        room.addUser(right);

        DogBattleDTO snapshot = startMatch();

        assertNotNull(snapshot.getWind());
        assertEquals(1, snapshot.getWind().getTurnNo());
        assertFalse(snapshot.getObstacles().isEmpty());
        DogBattleDTO.DogBattleObstacleDTO obstacle = snapshot.getObstacles().get(0);
        assertEquals("WOOD_BOX", obstacle.getType());
        assertTrue(obstacle.isDestructible());
        assertFalse(obstacle.isDestroyed());
        assertNotNull(obstacle.getHp());
    }

    @Test
    public void obstacleBlocksProjectileAndCanBeDestroyed() {
        room.addUser(left);
        room.addUser(right);
        startMatch();

        DogBattleDTO input = new DogBattleDTO();
        input.setEvent("PLAYER_INPUT");
        input.setAngle(20);
        input.setPower(100);

        DogBattleDTO result = DogBattleService.handleInput(left, room, input);

        assertNotNull(result);
        assertEquals("OBSTACLE", result.getHit().getTargetType());
        assertEquals("wood-box-1", result.getHit().getTargetId());
        assertEquals(24, result.getHit().getDamage());
        DogBattleDTO.DogBattleObstacleDTO obstacle = result.getObstacles().get(0);
        assertEquals(Integer.valueOf(6), obstacle.getHp());
        assertFalse(obstacle.isDestroyed());

        DogBattleDTO miss = new DogBattleDTO();
        miss.setEvent("PLAYER_INPUT");
        miss.setAngle(0);
        miss.setPower(0);
        DogBattleService.handleInput(right, room, miss);

        DogBattleDTO secondHit = DogBattleService.handleInput(left, room, input);
        DogBattleDTO.DogBattleObstacleDTO destroyed = secondHit.getObstacles().get(0);
        assertTrue(destroyed.isDestroyed());
        assertEquals(Integer.valueOf(0), destroyed.getHp());
    }

    @Test
    public void useNativeSkillSetsCooldownAndResultSkillName() {
        room.addUser(left);
        room.addUser(right);
        startMatch();

        DogBattleDTO input = directHitInput();
        input.setUseSkill(true);

        DogBattleDTO result = DogBattleService.handleInput(left, room, input);

        assertNotNull(result);
        assertTrue(result.isUsedSkill());
        assertEquals("土狗识路", result.getSkillName());
        assertEquals(3, findPlayer(result, left.getIdentityKey()).getSkillCooldown());
    }

    @Test
    public void rejectSkillWhenDisabledByRoomConfig() {
        room.setDogBattleAllowSkill(false);
        room.addUser(left);
        room.addUser(right);
        startMatch();

        DogBattleDTO input = directHitInput();
        input.setUseSkill(true);

        assertNull(DogBattleService.handleInput(left, room, input));
    }

    @Test
    public void rejectSkillWhenCooldownIsNotReady() {
        room.addUser(left);
        room.addUser(right);
        startMatch();

        DogBattleDTO first = directHitInput();
        first.setUseSkill(true);
        assertNotNull(DogBattleService.handleInput(left, room, first));
        DogBattleService.handleInput(right, room, missInput());

        DogBattleDTO second = directHitInput();
        second.setUseSkill(true);
        assertNull(DogBattleService.handleInput(left, room, second));
    }

    @Test
    public void bo3AdvancesToNextRoundBeforeMatchOver() {
        room.addUser(left);
        room.addUser(right);
        startMatch();

        DogBattleDTO result = null;
        for (int i = 0; i < 8; i++) {
            result = DogBattleService.handleInput(left, room, directHitInput());
            if (result != null && result.isRoundOver()) {
                break;
            }
            DogBattleService.handleInput(right, room, missInput());
        }

        assertNotNull(result);
        assertTrue(
                "lastHit=" + result.getHit().getTargetType()
                        + ", rightHp=" + findPlayer(result, right.getIdentityKey()).getHp()
                        + ", turnNo=" + result.getTurnNo(),
                result.isRoundOver()
        );
        assertFalse(result.isMatchOver());
        assertEquals(2, result.getRoundNo());
        assertEquals(1, result.getTurnNo());
        assertEquals(1, findPlayer(result, left.getIdentityKey()).getScore());
        assertEquals(100, findPlayer(result, left.getIdentityKey()).getHp());
        assertEquals(100, findPlayer(result, right.getIdentityKey()).getHp());
        assertFalse(result.getObstacles().isEmpty());
        assertEquals(1, result.getWind().getTurnNo());
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

    private DogBattleDTO startMatch() {
        DogBattleDTO snapshot = DogBattleService.playerStarted(room, left);
        if (snapshot == null) {
            snapshot = DogBattleService.playerStarted(room, right);
        }
        return snapshot;
    }

    private static DogBattleDTO directHitInput() {
        DogBattleDTO input = new DogBattleDTO();
        input.setEvent("PLAYER_INPUT");
        input.setAngle(45);
        input.setPower(100);
        return input;
    }

    private static DogBattleDTO missInput() {
        DogBattleDTO input = new DogBattleDTO();
        input.setEvent("PLAYER_INPUT");
        input.setAngle(0);
        input.setPower(0);
        return input;
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
