package cn.xeblog.server.game.dogbattle;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.game.dogbattle.DogBattleDTO;
import cn.xeblog.commons.entity.pet.PetDogDTO;
import cn.xeblog.commons.enums.Game;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class DogBattleServiceTest {

    private final GameRoom room = createRoom();
    private final User left = createUser("left-channel", 1001L, "左边狗狗");
    private final User right = createUser("right-channel", 1002L, "右边狗狗");

    @Before
    public void setUp() {
        DogBattleService.setDogResolverForTest(player -> null);
    }

    @After
    public void tearDown() {
        DogBattleService.clearRoom(room.getId());
        DogBattleService.resetDogResolver();
        DogBattleService.resetRewardApplier();
        DogBattleService.resetInteractionRewardApplier();
        DogBattleService.resetGameItemSettler();
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
    public void airbagPlayItemReducesNextDamageOnce() {
        room.addUser(left);
        room.addUser(right);
        room.getUsers().get(right.getIdentityKey()).setPetPlayItemId("item_battle_airbag");
        startMatch();

        DogBattleDTO firstHit = DogBattleService.handleInput(left, room, directHitInput());

        assertNotNull(firstHit);
        assertEquals("DOG", firstHit.getHit().getTargetType());
        assertEquals(17, firstHit.getHit().getDamage());
        assertEquals(83, findPlayer(firstHit, right.getIdentityKey()).getHp());
        assertEquals(1, firstHit.getItemEffects().size());
        assertEquals("item_battle_airbag", firstHit.getItemEffects().get(0).getItemId());
        assertEquals(right.getIdentityKey(), firstHit.getItemEffects().get(0).getPlayerKey());
        assertEquals("DAMAGE_REDUCED", firstHit.getItemEffects().get(0).getEffectType());
        assertEquals(7, firstHit.getItemEffects().get(0).getDamageBlocked());

        DogBattleService.handleInput(right, room, missInput());
        DogBattleDTO secondHit = DogBattleService.handleInput(left, room, directHitInput());

        assertNotNull(secondHit);
        assertEquals(24, secondHit.getHit().getDamage());
        assertEquals(59, findPlayer(secondHit, right.getIdentityKey()).getHp());
    }

    @Test
    public void pebblePlayItemThrowsNoDamagePreviewBeforeFormalAttack() {
        room.addUser(left);
        room.addUser(right);
        room.getUsers().get(left.getIdentityKey()).setPetPlayItemId("item_battle_pebble");
        startMatch();

        DogBattleDTO pebble = DogBattleService.handleInput(left, room, directHitInput());

        assertNotNull(pebble);
        assertEquals(left.getIdentityKey(), pebble.getNextPlayerKey());
        assertEquals(1, pebble.getTurnNo());
        assertEquals(0, pebble.getHit().getDamage());
        assertEquals(100, findPlayer(pebble, right.getIdentityKey()).getHp());
        assertTrue(pebble.getItemEffects().stream().anyMatch(effect ->
                left.getIdentityKey().equals(effect.getPlayerKey())
                        && "item_battle_pebble".equals(effect.getItemId())
                        && "PEBBLE_THROWN".equals(effect.getEffectType())));

        DogBattleDTO formalAttack = DogBattleService.handleInput(left, room, directHitInput());

        assertNotNull(formalAttack);
        assertEquals(right.getIdentityKey(), formalAttack.getNextPlayerKey());
        assertEquals(2, formalAttack.getTurnNo());
        assertEquals(24, formalAttack.getHit().getDamage());
        assertEquals(76, findPlayer(formalAttack, right.getIdentityKey()).getHp());
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
    public void startMatchUsesResolvedBattleDogFromPetProfile() {
        DogBattleService.setDogResolverForTest(user -> {
            if (user.getAccountId() == left.getAccountId()) {
                return petDog("dog-left", "短腿队长", "corgi");
            }
            return petDog("dog-right", "二哈教练", "husky");
        });
        room.addUser(left);
        room.addUser(right);

        DogBattleDTO snapshot = startMatch();

        DogBattleDTO.DogBattlePlayerDTO leftPlayer = findPlayer(snapshot, left.getIdentityKey());
        DogBattleDTO.DogBattlePlayerDTO rightPlayer = findPlayer(snapshot, right.getIdentityKey());
        assertEquals("dog-left", leftPlayer.getDog().getDogId());
        assertEquals("短腿队长", leftPlayer.getDog().getName());
        assertEquals("corgi", leftPlayer.getDog().getBreed());
        assertEquals("corgi_bone", leftPlayer.getDog().getProjectileSkinId());
        assertEquals("husky", rightPlayer.getDog().getBreed());
        assertEquals("slipper", rightPlayer.getDog().getProjectileSkinId());
    }

    @Test
    public void corgiSkillReducesDirectHitDamageAsServerAuthority() {
        DogBattleService.setDogResolverForTest(user -> petDog(user.getId() + "-dog", "柯基", "corgi"));
        room.addUser(left);
        room.addUser(right);
        startMatch();

        DogBattleDTO input = directHitInput();
        input.setUseSkill(true);
        DogBattleDTO result = DogBattleService.handleInput(left, room, input);

        assertNotNull(result);
        assertTrue(result.isUsedSkill());
        assertEquals("短腿稳投", result.getSkillName());
        assertEquals(21, result.getHit().getDamage());
    }

    @Test
    public void greyhoundSkillRaisesInitialSpeedWithoutIncreasingDamage() {
        DogBattleService.setDogResolverForTest(user -> petDog(user.getId() + "-dog", "灵缇", "greyhound"));
        room.addUser(left);
        room.addUser(right);
        startMatch();

        DogBattleDTO input = input(23, 97);
        input.setUseSkill(true);
        DogBattleDTO result = DogBattleService.handleInput(left, room, input);

        assertNotNull(result);
        assertEquals("疾影抛射", result.getSkillName());
        assertEquals("DOG", result.getHit().getTargetType());
        assertEquals(24, result.getHit().getDamage());
        assertEquals(4, findPlayer(result, left.getIdentityKey()).getSkillCooldown());
    }

    @Test
    public void goldenSkillRefundsOneCooldownWhenNoEffectiveDamage() {
        DogBattleService.setDogResolverForTest(user -> petDog(user.getId() + "-dog", "金毛", "golden"));
        room.addUser(left);
        room.addUser(right);
        startMatch();

        DogBattleDTO input = missInput();
        input.setUseSkill(true);
        DogBattleDTO result = DogBattleService.handleInput(left, room, input);

        assertNotNull(result);
        assertEquals("金球回收", result.getSkillName());
        assertEquals(0, result.getHit().getDamage());
        assertEquals(3, findPlayer(result, left.getIdentityKey()).getSkillCooldown());
    }

    @Test
    public void poodleSkillReducesNextSplashDamageOnly() {
        DogBattleService.setDogResolverForTest(user -> {
            if (user.getAccountId() == left.getAccountId()) {
                return petDog(user.getId() + "-dog", "贵宾", "poodle");
            }
            return petDog(user.getId() + "-dog", "田园", "native");
        });
        room.addUser(left);
        room.addUser(right);
        startMatch();

        DogBattleDTO guard = missInput();
        guard.setUseSkill(true);
        assertNotNull(DogBattleService.handleInput(left, room, guard));

        DogBattleDTO splash = DogBattleService.handleInput(right, room, input(26, 100));

        assertNotNull(splash);
        assertEquals("DOG", splash.getHit().getTargetType());
        assertFalse(splash.getHit().isDirectHit());
        assertEquals(6, splash.getHit().getDamage());
        assertEquals(94, findPlayer(splash, left.getIdentityKey()).getHp());
    }

    @Test
    public void shibaSkillRequiresBehindAndSuppressesTargetSkillOnHit() {
        DogBattleService.setDogResolverForTest(user -> {
            if (user.getAccountId() == left.getAccountId()) {
                return petDog(user.getId() + "-dog", "柴犬", "shiba");
            }
            return petDog(user.getId() + "-dog", "田园", "native");
        });
        room.addUser(left);
        room.addUser(right);
        startMatch();

        DogBattleDTO notBehind = directHitInput();
        notBehind.setUseSkill(true);
        assertNull(DogBattleService.handleInput(left, room, notBehind));

        DogBattleService.handleInput(left, room, missInput());
        DogBattleService.handleInput(right, room, directHitInput());

        DogBattleDTO counter = directHitInput();
        counter.setUseSkill(true);
        DogBattleDTO result = DogBattleService.handleInput(left, room, counter);

        assertNotNull(result);
        assertEquals("表情包反击", result.getSkillName());
        assertEquals(1, findPlayer(result, right.getIdentityKey()).getSkillCooldown());
    }

    @Test
    public void huskySkillChangesProjectileSkinWithoutChangingDamage() {
        DogBattleService.setDogResolverForTest(user -> petDog(user.getId() + "-dog", "哈士奇", "husky"));
        room.addUser(left);
        room.addUser(right);
        startMatch();

        DogBattleDTO input = directHitInput();
        input.setUseSkill(true);
        DogBattleDTO result = DogBattleService.handleInput(left, room, input);

        assertNotNull(result);
        assertEquals("二哈乱抛", result.getSkillName());
        assertEquals(24, result.getHit().getDamage());
        assertNotEquals("slipper", findPlayer(result, left.getIdentityKey()).getDog().getProjectileSkinId());
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
    public void matchOverAppliesRewardOnceAndIncludesRewardPreview() {
        final int[] rewardCalls = {0};
        DogBattleService.setRewardApplierForTest((winnerAccountId, loserAccountId, winnerBones, loserBones) -> {
            rewardCalls[0]++;
            assertEquals(left.getAccountId(), winnerAccountId);
            assertEquals(right.getAccountId(), loserAccountId);
            assertEquals(8, winnerBones);
            assertEquals(3, loserBones);
            return true;
        });
        room.setDogBattleRoundCount(1);
        room.addUser(left);
        room.addUser(right);
        startMatch();

        DogBattleDTO result = null;
        for (int i = 0; i < 8; i++) {
            result = DogBattleService.handleInput(left, room, directHitInput());
            if (result != null && result.isMatchOver()) {
                break;
            }
            DogBattleService.handleInput(right, room, missInput());
        }

        assertNotNull(result);
        assertTrue(
                "lastHit=" + result.getHit().getTargetType()
                        + ", rightHp=" + findPlayer(result, right.getIdentityKey()).getHp()
                        + ", turnNo=" + result.getTurnNo(),
                result.isMatchOver()
        );
        assertNotNull(result.getRewardPreview());
        assertEquals(left.getIdentityKey(), result.getRewardPreview().getWinnerPlayerKey());
        assertEquals(8, result.getRewardPreview().getWinnerBones());
        assertEquals(3, result.getRewardPreview().getLoserBones());
        assertTrue(result.getRewardPreview().isEconomyApplied());
        assertEquals(1, rewardCalls[0]);

        assertNull(DogBattleService.handleInput(left, room, directHitInput()));
        assertEquals(1, rewardCalls[0]);
    }

    @Test
    public void matchOverAppliesDirectHitInteractionRewardForDeclaredPlayer() {
        final int[] interactionRewardCalls = {0};
        final int[] itemSettleCalls = {0};
        DogBattleService.setRewardApplierForTest((winnerAccountId, loserAccountId, winnerBones, loserBones) -> true);
        DogBattleService.setInteractionRewardApplierForTest((accountId, itemId, requestedBones) -> {
            interactionRewardCalls[0]++;
            assertEquals(left.getAccountId(), accountId);
            assertEquals("item_battle_direct_hit", itemId);
            assertEquals(40, requestedBones);
        });
        DogBattleService.setGameItemSettlerForTest((targetRoom, playerKey, itemId, slot, status, rewardBones) -> {
            itemSettleCalls[0]++;
            assertEquals(room.getId(), targetRoom.getId());
            assertEquals(left.getIdentityKey(), playerKey);
            assertEquals("item_battle_direct_hit", itemId);
            assertEquals("interaction", slot);
            assertEquals("succeeded", status);
            assertEquals(40, rewardBones);
        });
        room.setDogBattleRoundCount(1);
        room.addUser(left);
        room.addUser(right);
        room.getUsers().get(left.getIdentityKey()).setPetInteractionItemId("item_battle_direct_hit");
        startMatch();

        DogBattleDTO result = null;
        for (int i = 0; i < 8; i++) {
            result = DogBattleService.handleInput(left, room, directHitInput());
            if (result != null && result.isMatchOver()) {
                break;
            }
            DogBattleService.handleInput(right, room, missInput());
        }

        assertNotNull(result);
        assertTrue(result.isMatchOver());
        assertTrue(result.getItemEffects().stream().anyMatch(effect ->
                left.getIdentityKey().equals(effect.getPlayerKey())
                        && "item_battle_direct_hit".equals(effect.getItemId())
                        && "DIRECT_HIT_MARKED".equals(effect.getEffectType())
                        && effect.getRewardBones() == 40));
        assertTrue(result.getItemEffects().stream().anyMatch(effect ->
                left.getIdentityKey().equals(effect.getPlayerKey())
                        && "item_battle_direct_hit".equals(effect.getItemId())
                        && "INTERACTION_SUCCEEDED".equals(effect.getEffectType())
                        && effect.getRewardBones() == 40));
        assertEquals(1, interactionRewardCalls[0]);
        assertEquals(1, itemSettleCalls[0]);

        assertNull(DogBattleService.handleInput(left, room, directHitInput()));
        assertEquals(1, interactionRewardCalls[0]);
        assertEquals(1, itemSettleCalls[0]);
    }

    @Test
    public void matchOverConsumesDirectHitInteractionItemWhenChallengeFails() {
        final int[] interactionRewardCalls = {0};
        final int[] itemSettleCalls = {0};
        DogBattleService.setRewardApplierForTest((winnerAccountId, loserAccountId, winnerBones, loserBones) -> true);
        DogBattleService.setInteractionRewardApplierForTest((accountId, itemId, requestedBones) -> interactionRewardCalls[0]++);
        DogBattleService.setGameItemSettlerForTest((targetRoom, playerKey, itemId, slot, status, rewardBones) -> {
            itemSettleCalls[0]++;
            assertEquals(room.getId(), targetRoom.getId());
            assertEquals(left.getIdentityKey(), playerKey);
            assertEquals("item_battle_direct_hit", itemId);
            assertEquals("interaction", slot);
            assertEquals("failed", status);
            assertEquals(0, rewardBones);
        });
        room.setDogBattleRoundCount(1);
        room.addUser(left);
        room.addUser(right);
        room.getUsers().get(left.getIdentityKey()).setPetInteractionItemId("item_battle_direct_hit");
        startMatch();

        DogBattleDTO result = null;
        for (int i = 0; i < 8; i++) {
            DogBattleService.handleInput(left, room, missInput());
            result = DogBattleService.handleInput(right, room, directHitInput());
            if (result != null && result.isMatchOver()) {
                break;
            }
        }

        assertNotNull(result);
        assertTrue(result.isMatchOver());
        assertTrue(result.getItemEffects().stream().anyMatch(effect ->
                left.getIdentityKey().equals(effect.getPlayerKey())
                        && "item_battle_direct_hit".equals(effect.getItemId())
                        && "INTERACTION_FAILED".equals(effect.getEffectType())));
        assertEquals(0, interactionRewardCalls[0]);
        assertEquals(1, itemSettleCalls[0]);
    }

    @Test
    public void matchOverAppliesProphecyRewardWhenDeclaredPlayerWins() {
        final int[] interactionRewardCalls = {0};
        final int[] itemSettleCalls = {0};
        DogBattleService.setRewardApplierForTest((winnerAccountId, loserAccountId, winnerBones, loserBones) -> true);
        DogBattleService.setInteractionRewardApplierForTest((accountId, itemId, requestedBones) -> {
            interactionRewardCalls[0]++;
            assertEquals(left.getAccountId(), accountId);
            assertEquals("item_prophecy", itemId);
            assertEquals(20, requestedBones);
        });
        DogBattleService.setGameItemSettlerForTest((targetRoom, playerKey, itemId, slot, status, rewardBones) -> {
            if (!"item_prophecy".equals(itemId)) {
                return;
            }
            itemSettleCalls[0]++;
            assertEquals(room.getId(), targetRoom.getId());
            assertEquals(left.getIdentityKey(), playerKey);
            assertEquals("interaction", slot);
            assertEquals("succeeded", status);
            assertEquals(20, rewardBones);
        });
        room.setDogBattleRoundCount(1);
        room.addUser(left);
        room.addUser(right);
        room.getUsers().get(left.getIdentityKey()).setPetInteractionItemId("item_prophecy");
        startMatch();

        DogBattleDTO result = null;
        for (int i = 0; i < 8; i++) {
            result = DogBattleService.handleInput(left, room, directHitInput());
            if (result != null && result.isMatchOver()) {
                break;
            }
            DogBattleService.handleInput(right, room, missInput());
        }

        assertNotNull(result);
        assertTrue(result.isMatchOver());
        assertTrue(result.getItemEffects().stream().anyMatch(effect ->
                left.getIdentityKey().equals(effect.getPlayerKey())
                        && "item_prophecy".equals(effect.getItemId())
                        && "INTERACTION_SUCCEEDED".equals(effect.getEffectType())
                        && effect.getRewardBones() == 20));
        assertEquals(1, interactionRewardCalls[0]);
        assertEquals(1, itemSettleCalls[0]);
    }

    @Test
    public void matchOverConsumesProphecyWhenDeclaredPlayerLoses() {
        final int[] interactionRewardCalls = {0};
        final int[] itemSettleCalls = {0};
        DogBattleService.setRewardApplierForTest((winnerAccountId, loserAccountId, winnerBones, loserBones) -> true);
        DogBattleService.setInteractionRewardApplierForTest((accountId, itemId, requestedBones) -> interactionRewardCalls[0]++);
        DogBattleService.setGameItemSettlerForTest((targetRoom, playerKey, itemId, slot, status, rewardBones) -> {
            if (!"item_prophecy".equals(itemId)) {
                return;
            }
            itemSettleCalls[0]++;
            assertEquals(room.getId(), targetRoom.getId());
            assertEquals(left.getIdentityKey(), playerKey);
            assertEquals("interaction", slot);
            assertEquals("failed", status);
            assertEquals(0, rewardBones);
        });
        room.setDogBattleRoundCount(1);
        room.addUser(left);
        room.addUser(right);
        room.getUsers().get(left.getIdentityKey()).setPetInteractionItemId("item_prophecy");
        startMatch();

        DogBattleDTO result = null;
        for (int i = 0; i < 8; i++) {
            DogBattleService.handleInput(left, room, missInput());
            result = DogBattleService.handleInput(right, room, directHitInput());
            if (result != null && result.isMatchOver()) {
                break;
            }
        }

        assertNotNull(result);
        assertTrue(result.isMatchOver());
        assertTrue(result.getItemEffects().stream().anyMatch(effect ->
                left.getIdentityKey().equals(effect.getPlayerKey())
                        && "item_prophecy".equals(effect.getItemId())
                        && "INTERACTION_FAILED".equals(effect.getEffectType())));
        assertEquals(0, interactionRewardCalls[0]);
        assertEquals(1, itemSettleCalls[0]);
    }

    @Test
    public void matchOverConsumesTriggeredPebblePlayItem() {
        final int[] itemSettleCalls = {0};
        DogBattleService.setRewardApplierForTest((winnerAccountId, loserAccountId, winnerBones, loserBones) -> true);
        DogBattleService.setGameItemSettlerForTest((targetRoom, playerKey, itemId, slot, status, rewardBones) -> {
            if (!"item_battle_pebble".equals(itemId)) {
                return;
            }
            itemSettleCalls[0]++;
            assertEquals(left.getIdentityKey(), playerKey);
            assertEquals("gameplay", slot);
            assertEquals("consumed", status);
            assertEquals(0, rewardBones);
        });
        room.setDogBattleRoundCount(1);
        room.addUser(left);
        room.addUser(right);
        room.getUsers().get(left.getIdentityKey()).setPetPlayItemId("item_battle_pebble");
        startMatch();

        DogBattleDTO preview = DogBattleService.handleInput(left, room, directHitInput());
        assertNotNull(preview);
        assertEquals(0, preview.getHit().getDamage());

        DogBattleDTO result = null;
        for (int i = 0; i < 8; i++) {
            result = DogBattleService.handleInput(left, room, directHitInput());
            if (result != null && result.isMatchOver()) {
                break;
            }
            DogBattleService.handleInput(right, room, missInput());
        }

        assertNotNull(result);
        assertTrue(result.isMatchOver());
        assertTrue(result.getItemEffects().stream().anyMatch(effect ->
                left.getIdentityKey().equals(effect.getPlayerKey())
                        && "item_battle_pebble".equals(effect.getItemId())
                        && "PLAY_CONSUMED".equals(effect.getEffectType())));
        assertEquals(1, itemSettleCalls[0]);
    }

    @Test
    public void matchOverRefundsUntriggeredAirbagPlayItem() {
        final int[] itemSettleCalls = {0};
        DogBattleService.setRewardApplierForTest((winnerAccountId, loserAccountId, winnerBones, loserBones) -> true);
        DogBattleService.setGameItemSettlerForTest((targetRoom, playerKey, itemId, slot, status, rewardBones) -> {
            if (!"item_battle_airbag".equals(itemId)) {
                return;
            }
            itemSettleCalls[0]++;
            assertEquals(left.getIdentityKey(), playerKey);
            assertEquals("gameplay", slot);
            assertEquals("refunded", status);
            assertEquals(0, rewardBones);
        });
        room.setDogBattleRoundCount(1);
        room.addUser(left);
        room.addUser(right);
        room.getUsers().get(left.getIdentityKey()).setPetPlayItemId("item_battle_airbag");
        startMatch();

        DogBattleDTO result = null;
        for (int i = 0; i < 8; i++) {
            result = DogBattleService.handleInput(left, room, directHitInput());
            if (result != null && result.isMatchOver()) {
                break;
            }
            DogBattleService.handleInput(right, room, missInput());
        }

        assertNotNull(result);
        assertTrue(result.isMatchOver());
        assertTrue(result.getItemEffects().stream().anyMatch(effect ->
                left.getIdentityKey().equals(effect.getPlayerKey())
                        && "item_battle_airbag".equals(effect.getItemId())
                        && "PLAY_REFUNDED".equals(effect.getEffectType())));
        assertEquals(1, itemSettleCalls[0]);
    }

    @Test
    public void reconnectSnapshotIncludesLastTurnAndItemStates() {
        room.addUser(left);
        room.addUser(right);
        room.getUsers().get(left.getIdentityKey()).setPetPlayItemId("item_battle_pebble");
        room.getUsers().get(left.getIdentityKey()).setPetInteractionItemId("item_battle_direct_hit");
        room.getUsers().get(right.getIdentityKey()).setPetPlayItemId("item_battle_airbag");
        startMatch();

        DogBattleDTO pebblePreview = DogBattleService.handleInput(left, room, directHitInput());
        assertNotNull(pebblePreview);
        DogBattleDTO formalAttack = DogBattleService.handleInput(left, room, directHitInput());
        assertNotNull(formalAttack);

        DogBattleDTO snapshot = DogBattleService.playerStarted(room, left);

        assertNotNull(snapshot);
        assertEquals("SNAPSHOT", snapshot.getEvent());
        assertEquals(left.getIdentityKey(), snapshot.getLastActorPlayerKey());
        assertEquals(formalAttack.getTrajectory().size(), snapshot.getLastTrajectory().size());
        assertEquals(formalAttack.getHit().getTargetType(), snapshot.getLastHit().getTargetType());
        assertTrue(snapshot.getLastItemEffects().stream().anyMatch(effect ->
                left.getIdentityKey().equals(effect.getPlayerKey())
                        && "item_battle_direct_hit".equals(effect.getItemId())
                        && "DIRECT_HIT_MARKED".equals(effect.getEffectType())));
        assertTrue(snapshot.getItemStates().stream().anyMatch(item ->
                left.getIdentityKey().equals(item.getPlayerKey())
                        && "item_battle_pebble".equals(item.getItemId())
                        && "gameplay".equals(item.getSlot())
                        && "triggered".equals(item.getStatus())));
        assertTrue(snapshot.getItemStates().stream().anyMatch(item ->
                right.getIdentityKey().equals(item.getPlayerKey())
                        && "item_battle_airbag".equals(item.getItemId())
                        && "gameplay".equals(item.getSlot())
                        && "triggered".equals(item.getStatus())));
        assertTrue(snapshot.getItemStates().stream().anyMatch(item ->
                left.getIdentityKey().equals(item.getPlayerKey())
                        && "item_battle_direct_hit".equals(item.getItemId())
                        && "interaction".equals(item.getSlot())
                        && "challenge_met".equals(item.getStatus())));
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
        return input(45, 100);
    }

    private static DogBattleDTO missInput() {
        return input(0, 0);
    }

    private static DogBattleDTO input(int angle, int power) {
        DogBattleDTO input = new DogBattleDTO();
        input.setEvent("PLAYER_INPUT");
        input.setAngle(angle);
        input.setPower(power);
        return input;
    }

    private static PetDogDTO petDog(String id, String name, String breed) {
        PetDogDTO dog = new PetDogDTO();
        dog.setId(id);
        dog.setName(name);
        dog.setBreed(breed);
        dog.setStage("adult");
        return dog;
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
