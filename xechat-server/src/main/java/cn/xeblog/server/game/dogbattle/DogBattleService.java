package cn.xeblog.server.game.dogbattle;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.game.dogbattle.DogBattleDTO;
import cn.xeblog.commons.entity.pet.PetDogDTO;
import cn.xeblog.commons.entity.pet.PetProfileDTO;
import cn.xeblog.commons.enums.Game;
import cn.xeblog.server.pet.PetGameItemDeclarationService;
import cn.xeblog.server.pet.PetProfileService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class DogBattleService {

    private static final int WORLD_WIDTH = 1000;
    private static final int GROUND_Y = 500;
    private static final int LEFT_X = 120;
    private static final int RIGHT_X = 880;
    private static final int DOG_WIDTH = 60;
    private static final int DOG_HEIGHT = 80;
    private static final int MAX_HP = 100;
    private static final int DIRECT_DAMAGE = 24;
    private static final int CORGI_SKILL_DAMAGE = 21;
    private static final int OBSTACLE_DAMAGE = 24;
    private static final int SPLASH_RADIUS = 85;
    private static final int MIN_SPLASH_DAMAGE = 4;
    private static final int MAX_SPLASH_DAMAGE = 18;
    private static final int WINNER_REWARD_BONES = 8;
    private static final int LOSER_REWARD_BONES = 3;
    private static final int DIRECT_HIT_ITEM_REWARD_BONES = 40;
    private static final int PROPHECY_ITEM_REWARD_BONES = 20;
    private static final String ITEM_BATTLE_DIRECT_HIT = "item_battle_direct_hit";
    private static final String ITEM_BATTLE_AIRBAG = "item_battle_airbag";
    private static final String ITEM_BATTLE_PEBBLE = "item_battle_pebble";
    private static final String ITEM_BATTLE_ECHO = "item_battle_echo";
    private static final String ITEM_PROPHECY = "item_prophecy";
    private static final double GRAVITY = 0.35;
    private static final double WIND_ACCEL = 0.006;
    private static final int MAX_STEPS = 240;
    private static final Map<String, BattleState> STATES = new ConcurrentHashMap<>();
    private static Function<GameRoom.Player, PetDogDTO> dogResolver = DogBattleService::resolvePetDog;
    private static RewardApplier rewardApplier = PetProfileService::addDogBattleReward;
    private static InteractionRewardApplier interactionRewardApplier = DogBattleService::ignoreInteractionReward;
    private static GameItemSettler gameItemSettler = DogBattleService::settleGameItem;

    private DogBattleService() {
    }

    public static DogBattleDTO playerStarted(GameRoom room, User user) {
        if (room == null || user == null || room.getGame() != Game.DOG_BATTLE) {
            return null;
        }
        if (room.getUsers().size() != 2) {
            return null;
        }
        BattleState state = STATES.computeIfAbsent(room.getId(), key -> BattleState.from(room));
        synchronized (state) {
            state.startedPlayerKeys.add(user.getIdentityKey());
            if (state.started) {
                return state.toSnapshot(room, "SNAPSHOT");
            }
            if (state.startedPlayerKeys.size() < room.getUsers().size()) {
                return null;
            }
            state.started = true;
            return state.toSnapshot(room, "MATCH_START");
        }
    }

    public static DogBattleDTO handleInput(User user, GameRoom room, DogBattleDTO input) {
        if (user == null || room == null || input == null || room.getGame() != Game.DOG_BATTLE) {
            return null;
        }
        if (!"PLAYER_INPUT".equals(input.getEvent())) {
            return null;
        }
        BattleState state = STATES.get(room.getId());
        if (state == null) {
            return null;
        }
        synchronized (state) {
            String actorKey = user.getIdentityKey();
            if (!actorKey.equals(state.currentPlayerKey) || state.matchOver) {
                return null;
            }

            DogBattleDTO.DogBattlePlayerDTO actor = state.findPlayer(actorKey);
            DogBattleDTO.DogBattlePlayerDTO target = state.findOpponent(actorKey);
            if (actor == null || target == null) {
                return null;
            }
            if (input.isUseSkill() && (!state.allowSkill || actor.getSkillCooldown() > 0)) {
                return null;
            }

            boolean pebblePreview = state.hasPendingPebble(actorKey);
            SkillTurn skillTurn = SkillTurn.from(actor, !pebblePreview && input.isUseSkill());
            if (skillTurn.used && !skillTurn.canUse(actor, target)) {
                return null;
            }
            SimulationResult simulation = simulate(
                    actor,
                    target,
                    state.obstacles,
                    skillTurn.effectiveWindPower(state.windPower),
                    skillTurn.directDamage(),
                    skillTurn.speedMultiplier(),
                    input
            );
            List<DogBattleDTO.DogBattleItemEffectDTO> itemEffects = new ArrayList<>();
            if (pebblePreview) {
                state.consumePebble(actorKey);
                simulation.hit.setDamage(0);
                itemEffects.add(itemEffect(actorKey, ITEM_BATTLE_PEBBLE, "PEBBLE_THROWN", 0, 0));
                return state.toTurnResult(room, actorKey, simulation, itemEffects, false, null,
                        false, false, null, actorKey, wind(state.turnNo, state.windPower), "playing");
            }
            state.applyDefensiveSkill(target, simulation.hit);
            state.applyPlayItemEffects(target, simulation.hit, itemEffects);
            if ("DOG".equals(simulation.hit.getTargetType())) {
                target.setHp(Math.max(0, target.getHp() - simulation.hit.getDamage()));
            } else if ("OBSTACLE".equals(simulation.hit.getTargetType())) {
                state.damageObstacle(simulation.hit.getTargetId(), simulation.hit.getDamage());
            }
            state.applyPostSkillEffects(actor, target, skillTurn, simulation.hit);
            state.updateSkillCooldown(actor, skillTurn, simulation.hit);
            state.recordInteractionHit(actorKey, simulation.hit, itemEffects);

            boolean roundOver = target.getHp() <= 0;
            boolean matchOver = false;
            String roundWinnerPlayerKey = roundOver ? actor.getPlayerKey() : null;
            String nextPlayerKey = target.getPlayerKey();
            if (roundOver) {
                actor.setScore(actor.getScore() + 1);
                if (actor.getScore() >= state.requiredWins) {
                    matchOver = true;
                    state.matchOver = true;
                    state.winnerPlayerKey = actor.getPlayerKey();
                    state.applyReward(room, actor.getPlayerKey(), target.getPlayerKey(), itemEffects);
                    nextPlayerKey = null;
                } else {
                    state.startNextRound(actor.getPlayerKey());
                    nextPlayerKey = state.currentPlayerKey;
                }
            } else {
                state.currentPlayerKey = nextPlayerKey;
                state.turnNo++;
                state.windPower = nextWindPower(state.windPower, state.turnNo);
            }

            return state.toTurnResult(room, actorKey, simulation, itemEffects, skillTurn.used, skillTurn.skillName,
                    roundOver, matchOver, roundWinnerPlayerKey != null ? roundWinnerPlayerKey : state.winnerPlayerKey,
                    nextPlayerKey, matchOver ? null : wind(state.turnNo, state.windPower),
                    matchOver ? "matchOver" : roundOver ? "roundOver" : "playing");
        }
    }

    public static void clearRoom(String roomId) {
        if (roomId != null) {
            STATES.remove(roomId);
        }
    }

    static void setDogResolverForTest(Function<GameRoom.Player, PetDogDTO> resolver) {
        dogResolver = resolver == null ? DogBattleService::resolvePetDog : resolver;
    }

    static void resetDogResolver() {
        dogResolver = DogBattleService::resolvePetDog;
    }

    static void setRewardApplierForTest(RewardApplier applier) {
        rewardApplier = applier == null ? PetProfileService::addDogBattleReward : applier;
    }

    static void resetRewardApplier() {
        rewardApplier = PetProfileService::addDogBattleReward;
    }

    static void setInteractionRewardApplierForTest(InteractionRewardApplier applier) {
        interactionRewardApplier = applier == null ? DogBattleService::ignoreInteractionReward : applier;
    }

    static void resetInteractionRewardApplier() {
        interactionRewardApplier = DogBattleService::ignoreInteractionReward;
    }

    static void setGameItemSettlerForTest(GameItemSettler settler) {
        gameItemSettler = settler == null ? DogBattleService::settleGameItem : settler;
    }

    static void resetGameItemSettler() {
        gameItemSettler = DogBattleService::settleGameItem;
    }

    private static void settleGameItem(GameRoom room, String playerKey, String itemId,
                                       String slot, String status, int rewardBones) {
        if ("succeeded".equals(status)) {
            PetGameItemDeclarationService.settleSucceededWithInteractionReward(room, playerKey, itemId, slot, rewardBones);
        } else if ("failed".equals(status)) {
            PetGameItemDeclarationService.settleFailed(room, playerKey, itemId, slot);
        } else if ("consumed".equals(status)) {
            PetGameItemDeclarationService.settleConsumed(room, playerKey, itemId, slot);
        } else if ("refunded".equals(status)) {
            PetGameItemDeclarationService.settleRefunded(room, playerKey, itemId, slot);
        }
    }

    private static void ignoreInteractionReward(long accountId, String itemId, int requestedBones) {
    }

    private static SimulationResult simulate(
            DogBattleDTO.DogBattlePlayerDTO actor,
            DogBattleDTO.DogBattlePlayerDTO target,
            List<DogBattleDTO.DogBattleObstacleDTO> obstacles,
            int windPower,
            int directDamage,
            double speedMultiplier,
            DogBattleDTO input
    ) {
        int angle = clamp(input.getAngle(), 0, 90);
        int power = clamp(input.getPower(), 0, 100);
        int startX = "LEFT".equals(actor.getSide()) ? LEFT_X : RIGHT_X;
        int direction = "LEFT".equals(actor.getSide()) ? 1 : -1;
        double radians = Math.toRadians(angle);
        double speed = (4 + power * 0.13) * speedMultiplier;
        double x = startX;
        double y = GROUND_Y - DOG_HEIGHT / 2.0;
        double vx = Math.cos(radians) * speed * direction;
        double vy = -Math.sin(radians) * speed;
        List<DogBattleDTO.DogBattleTrajectoryPointDTO> trajectory = new ArrayList<>();

        DogBattleDTO.DogBattleHitDTO hit = null;
        for (int step = 0; step < MAX_STEPS; step++) {
            vx += windPower * WIND_ACCEL;
            vy += GRAVITY;
            x += vx;
            y += vy;
            if (step % 3 == 0) {
                trajectory.add(point(x, y));
            }

            DogBattleDTO.DogBattleObstacleDTO obstacle = findObstacle(x, y, obstacles);
            if (obstacle != null) {
                hit = hit("OBSTACLE", obstacle.getId(), x, y, false, OBSTACLE_DAMAGE);
                break;
            }
            if (inDogBox(x, y, target)) {
                hit = hit("DOG", target.getPlayerKey(), x, y, true, directDamage);
                break;
            }
            if (y >= GROUND_Y) {
                hit = splashHitOrGround(target, x, GROUND_Y);
                break;
            }
            if (x < 0 || x > WORLD_WIDTH) {
                hit = hit("OUT_OF_BOUNDS", null, x, y, false, 0);
                break;
            }
        }
        if (hit == null) {
            hit = hit("OUT_OF_BOUNDS", null, x, y, false, 0);
        }
        trajectory.add(point(hit.getX(), hit.getY()));
        return new SimulationResult(trajectory, hit);
    }

    private static DogBattleDTO.DogBattleHitDTO splashHitOrGround(
            DogBattleDTO.DogBattlePlayerDTO target,
            double x,
            double y
    ) {
        int targetX = "LEFT".equals(target.getSide()) ? LEFT_X : RIGHT_X;
        double targetY = GROUND_Y - DOG_HEIGHT / 2.0;
        double distance = Math.hypot(x - targetX, y - targetY);
        if (distance > SPLASH_RADIUS) {
            return hit("GROUND", null, x, y, false, 0);
        }
        double ratio = 1 - distance / SPLASH_RADIUS;
        int damage = (int) Math.floor(MIN_SPLASH_DAMAGE + ratio * (MAX_SPLASH_DAMAGE - MIN_SPLASH_DAMAGE));
        return hit("DOG", target.getPlayerKey(), x, y, false, damage);
    }

    private static DogBattleDTO.DogBattleObstacleDTO findObstacle(
            double x,
            double y,
            List<DogBattleDTO.DogBattleObstacleDTO> obstacles
    ) {
        for (DogBattleDTO.DogBattleObstacleDTO obstacle : obstacles) {
            if (obstacle.isDestroyed()) {
                continue;
            }
            if (x >= obstacle.getX()
                    && x <= obstacle.getX() + obstacle.getWidth()
                    && y >= obstacle.getY()
                    && y <= obstacle.getY() + obstacle.getHeight()) {
                return obstacle;
            }
        }
        return null;
    }

    private static boolean inDogBox(double x, double y, DogBattleDTO.DogBattlePlayerDTO player) {
        int centerX = "LEFT".equals(player.getSide()) ? LEFT_X : RIGHT_X;
        return x >= centerX - DOG_WIDTH / 2.0
                && x <= centerX + DOG_WIDTH / 2.0
                && y >= GROUND_Y - DOG_HEIGHT
                && y <= GROUND_Y;
    }

    private static DogBattleDTO.DogBattleTrajectoryPointDTO point(double x, double y) {
        DogBattleDTO.DogBattleTrajectoryPointDTO point = new DogBattleDTO.DogBattleTrajectoryPointDTO();
        point.setX((int) Math.round(x));
        point.setY((int) Math.round(y));
        return point;
    }

    private static DogBattleDTO.DogBattleHitDTO hit(
            String targetType,
            String targetId,
            double x,
            double y,
            boolean directHit,
            int damage
    ) {
        DogBattleDTO.DogBattleHitDTO hit = new DogBattleDTO.DogBattleHitDTO();
        hit.setTargetType(targetType);
        hit.setTargetId(targetId);
        hit.setX((int) Math.round(x));
        hit.setY((int) Math.round(y));
        hit.setDirectHit(directHit);
        hit.setDamage(damage);
        return hit;
    }

    private static DogBattleDTO.DogBattleItemEffectDTO itemEffect(
            String playerKey,
            String itemId,
            String effectType,
            int damageBlocked,
            int rewardBones
    ) {
        DogBattleDTO.DogBattleItemEffectDTO effect = new DogBattleDTO.DogBattleItemEffectDTO();
        effect.setPlayerKey(playerKey);
        effect.setItemId(itemId);
        effect.setEffectType(effectType);
        effect.setDamageBlocked(damageBlocked);
        effect.setRewardBones(rewardBones);
        return effect;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class BattleState {
        private final List<DogBattleDTO.DogBattlePlayerDTO> players;
        private final Map<String, Long> accountIds;
        private final Map<String, String> playItemIds;
        private final Map<String, String> interactionItemIds;
        private final LinkedHashSet<String> usedPebblePlayerKeys = new LinkedHashSet<>();
        private final LinkedHashSet<String> usedAirbagPlayerKeys = new LinkedHashSet<>();
        private final LinkedHashSet<String> directHitItemPlayerKeys = new LinkedHashSet<>();
        private final int roundCount;
        private final int requiredWins;
        private final boolean allowSkill;
        private final LinkedHashSet<String> startedPlayerKeys = new LinkedHashSet<>();
        private final List<DogBattleDTO.DogBattleObstacleDTO> obstacles = new ArrayList<>();
        private String lastActorPlayerKey;
        private List<DogBattleDTO.DogBattleTrajectoryPointDTO> lastTrajectory = new ArrayList<>();
        private DogBattleDTO.DogBattleHitDTO lastHit;
        private List<DogBattleDTO.DogBattleItemEffectDTO> lastItemEffects = new ArrayList<>();
        private int roundNo = 1;
        private int turnNo = 1;
        private int windPower;
        private boolean started;
        private boolean matchOver;
        private boolean rewardApplied;
        private boolean interactionRewardApplied;
        private String currentPlayerKey;
        private String winnerPlayerKey;
        private String poodleGuardPlayerKey;

        private BattleState(
                List<DogBattleDTO.DogBattlePlayerDTO> players,
                Map<String, Long> accountIds,
                Map<String, String> playItemIds,
                Map<String, String> interactionItemIds,
                int roundCount,
                boolean allowSkill
        ) {
            this.players = players;
            this.accountIds = accountIds;
            this.playItemIds = playItemIds;
            this.interactionItemIds = interactionItemIds;
            this.roundCount = normalizeRoundCount(roundCount);
            this.requiredWins = this.roundCount / 2 + 1;
            this.allowSkill = allowSkill;
            this.obstacles.addAll(generateObstacles(roundNo));
            if (!players.isEmpty()) {
                this.currentPlayerKey = players.get(0).getPlayerKey();
            }
        }

        private static BattleState from(GameRoom room) {
            List<DogBattleDTO.DogBattlePlayerDTO> players = new ArrayList<>();
            Map<String, Long> accountIds = new LinkedHashMap<>();
            Map<String, String> playItemIds = new LinkedHashMap<>();
            Map<String, String> interactionItemIds = new LinkedHashMap<>();
            int index = 0;
            for (GameRoom.Player player : room.getUsers().values()) {
                players.add(createPlayer(player, index == 0 ? "LEFT" : "RIGHT", dogResolver.apply(player)));
                accountIds.put(player.getId(), player.getAccountId());
                playItemIds.put(player.getId(), player.getPetPlayItemId());
                interactionItemIds.put(player.getId(), player.getPetInteractionItemId());
                index++;
                if (index >= 2) {
                    break;
                }
            }
            return new BattleState(players, accountIds, playItemIds, interactionItemIds,
                    room.getDogBattleRoundCount(), room.isDogBattleAllowSkill());
        }

        private DogBattleDTO toSnapshot(GameRoom room, String event) {
            DogBattleDTO snapshot = new DogBattleDTO();
            snapshot.setRoomId(room.getId());
            snapshot.setGame(Game.DOG_BATTLE);
            snapshot.setEvent(event);
            snapshot.setConfig(config(room));
            snapshot.setRoundNo(roundNo);
            snapshot.setTurnNo(turnNo);
            snapshot.setCurrentPlayerKey(currentPlayerKey);
            snapshot.setPlayers(copyPlayers());
            snapshot.setWind(wind(turnNo, windPower));
            snapshot.setObstacles(copyObstacles());
            snapshot.setPhase("playing");
            snapshot.setWinnerPlayerKey(winnerPlayerKey);
            snapshot.setItemStates(buildItemStates());
            snapshot.setLastActorPlayerKey(lastActorPlayerKey);
            snapshot.setLastTrajectory(copyTrajectory(lastTrajectory));
            snapshot.setLastHit(copyHit(lastHit));
            snapshot.setLastItemEffects(copyItemEffects(lastItemEffects));
            return snapshot;
        }

        private void startNextRound(String firstPlayerKey) {
            roundNo++;
            turnNo = 1;
            windPower = 0;
            currentPlayerKey = firstPlayerKey;
            winnerPlayerKey = null;
            for (DogBattleDTO.DogBattlePlayerDTO player : players) {
                player.setHp(MAX_HP);
            }
            obstacles.clear();
            obstacles.addAll(generateObstacles(roundNo));
        }

        private boolean hasPendingPebble(String playerKey) {
            return playerKey != null
                    && ITEM_BATTLE_PEBBLE.equals(playItemIds.get(playerKey))
                    && !usedPebblePlayerKeys.contains(playerKey);
        }

        private void consumePebble(String playerKey) {
            if (playerKey != null) {
                usedPebblePlayerKeys.add(playerKey);
            }
        }

        private void damageObstacle(String obstacleId, int damage) {
            for (DogBattleDTO.DogBattleObstacleDTO obstacle : obstacles) {
                if (!obstacle.getId().equals(obstacleId) || !obstacle.isDestructible()) {
                    continue;
                }
                int nextHp = Math.max(0, (obstacle.getHp() == null ? 0 : obstacle.getHp()) - damage);
                obstacle.setHp(nextHp);
                obstacle.setDestroyed(nextHp <= 0);
                return;
            }
        }

        private void applyReward(GameRoom room, String winnerPlayerKey, String loserPlayerKey,
                                 List<DogBattleDTO.DogBattleItemEffectDTO> itemEffects) {
            if (rewardApplied) {
                return;
            }
            Long winnerAccountId = accountIds.get(winnerPlayerKey);
            Long loserAccountId = accountIds.get(loserPlayerKey);
            if (winnerAccountId == null || loserAccountId == null) {
                return;
            }
            rewardApplied = rewardApplier.apply(winnerAccountId, loserAccountId, WINNER_REWARD_BONES, LOSER_REWARD_BONES);
            if (rewardApplied) {
                applyInteractionRewards(room, itemEffects);
                applyPlayItemSettlements(room, itemEffects);
            }
        }

        private DogBattleDTO toTurnResult(
                GameRoom room,
                String actorKey,
                SimulationResult simulation,
                List<DogBattleDTO.DogBattleItemEffectDTO> itemEffects,
                boolean usedSkill,
                String skillName,
                boolean roundOver,
                boolean matchOver,
                String winnerPlayerKey,
                String nextPlayerKey,
                DogBattleDTO.DogBattleWindDTO nextWind,
                String phase
        ) {
            recordLastTurn(actorKey, simulation, itemEffects);
            DogBattleDTO result = new DogBattleDTO();
            result.setRoomId(room.getId());
            result.setGame(Game.DOG_BATTLE);
            result.setEvent("TURN_RESULT");
            result.setRoundNo(roundNo);
            result.setTurnNo(turnNo);
            result.setActorPlayerKey(actorKey);
            result.setCurrentPlayerKey(currentPlayerKey);
            result.setTrajectory(simulation.trajectory);
            result.setHit(simulation.hit);
            result.setPlayers(copyPlayers());
            result.setWind(wind(turnNo, windPower));
            result.setObstacles(copyObstacles());
            result.setNextPlayerKey(nextPlayerKey);
            result.setNextWind(nextWind);
            result.setUsedSkill(usedSkill);
            result.setSkillName(skillName);
            result.setItemEffects(itemEffects);
            result.setRoundOver(roundOver);
            result.setMatchOver(matchOver);
            result.setWinnerPlayerKey(winnerPlayerKey);
            result.setRewardPreview(matchOver ? rewardPreview(this.winnerPlayerKey, rewardApplied) : null);
            result.setPhase(phase);
            return result;
        }

        private void recordLastTurn(
                String actorKey,
                SimulationResult simulation,
                List<DogBattleDTO.DogBattleItemEffectDTO> itemEffects
        ) {
            lastActorPlayerKey = actorKey;
            lastTrajectory = copyTrajectory(simulation == null ? null : simulation.trajectory);
            lastHit = copyHit(simulation == null ? null : simulation.hit);
            lastItemEffects = copyItemEffects(itemEffects);
        }

        private List<DogBattleDTO.DogBattleItemStateDTO> buildItemStates() {
            List<DogBattleDTO.DogBattleItemStateDTO> itemStates = new ArrayList<>();
            for (Map.Entry<String, String> entry : playItemIds.entrySet()) {
                String playerKey = entry.getKey();
                String itemId = entry.getValue();
                if (isBlank(itemId)) {
                    continue;
                }
                itemStates.add(itemState(playerKey, itemId, "gameplay", playItemStatus(playerKey, itemId)));
            }
            for (Map.Entry<String, String> entry : interactionItemIds.entrySet()) {
                String playerKey = entry.getKey();
                String itemId = entry.getValue();
                if (isBlank(itemId)) {
                    continue;
                }
                itemStates.add(itemState(playerKey, itemId, "interaction", interactionItemStatus(playerKey, itemId)));
            }
            return itemStates;
        }

        private String playItemStatus(String playerKey, String itemId) {
            if (rewardApplied) {
                if (ITEM_BATTLE_PEBBLE.equals(itemId)) {
                    return usedPebblePlayerKeys.contains(playerKey) ? "consumed" : "refunded";
                }
                if (ITEM_BATTLE_AIRBAG.equals(itemId)) {
                    return usedAirbagPlayerKeys.contains(playerKey) ? "consumed" : "refunded";
                }
                if (ITEM_BATTLE_ECHO.equals(itemId)) {
                    return "consumed";
                }
            }
            if (ITEM_BATTLE_PEBBLE.equals(itemId) && usedPebblePlayerKeys.contains(playerKey)) {
                return "triggered";
            }
            if (ITEM_BATTLE_AIRBAG.equals(itemId) && usedAirbagPlayerKeys.contains(playerKey)) {
                return "triggered";
            }
            return "reserved";
        }

        private String interactionItemStatus(String playerKey, String itemId) {
            if (rewardApplied) {
                if (ITEM_BATTLE_DIRECT_HIT.equals(itemId)) {
                    return directHitItemPlayerKeys.contains(playerKey) ? "succeeded" : "failed";
                }
                if (ITEM_PROPHECY.equals(itemId)) {
                    return playerKey.equals(winnerPlayerKey) ? "succeeded" : "failed";
                }
            }
            if (ITEM_BATTLE_DIRECT_HIT.equals(itemId) && directHitItemPlayerKeys.contains(playerKey)) {
                return "challenge_met";
            }
            return "reserved";
        }

        private void recordInteractionHit(String actorPlayerKey, DogBattleDTO.DogBattleHitDTO hit,
                                          List<DogBattleDTO.DogBattleItemEffectDTO> itemEffects) {
            if (actorPlayerKey == null
                    || hit == null
                    || !hit.isDirectHit()
                    || !ITEM_BATTLE_DIRECT_HIT.equals(interactionItemIds.get(actorPlayerKey))) {
                return;
            }
            directHitItemPlayerKeys.add(actorPlayerKey);
            itemEffects.add(itemEffect(actorPlayerKey, ITEM_BATTLE_DIRECT_HIT,
                    "DIRECT_HIT_MARKED", 0, DIRECT_HIT_ITEM_REWARD_BONES));
        }

        private void applyInteractionRewards(GameRoom room, List<DogBattleDTO.DogBattleItemEffectDTO> itemEffects) {
            if (interactionRewardApplied) {
                return;
            }
            interactionRewardApplied = true;
            for (Map.Entry<String, String> entry : interactionItemIds.entrySet()) {
                String playerKey = entry.getKey();
                String itemId = entry.getValue();
                boolean succeeded;
                int rewardBones;
                if (ITEM_BATTLE_DIRECT_HIT.equals(itemId)) {
                    succeeded = directHitItemPlayerKeys.contains(playerKey);
                    rewardBones = DIRECT_HIT_ITEM_REWARD_BONES;
                } else if (ITEM_PROPHECY.equals(itemId)) {
                    succeeded = playerKey.equals(winnerPlayerKey);
                    rewardBones = PROPHECY_ITEM_REWARD_BONES;
                } else {
                    continue;
                }
                gameItemSettler.settle(room, playerKey, itemId,
                        "interaction", succeeded ? "succeeded" : "failed",
                        succeeded ? rewardBones : 0);
                itemEffects.add(itemEffect(playerKey, itemId,
                        succeeded ? "INTERACTION_SUCCEEDED" : "INTERACTION_FAILED",
                        0, succeeded ? rewardBones : 0));
                if (!succeeded) {
                    continue;
                }
                Long accountId = accountIds.get(playerKey);
                if (accountId == null || accountId <= 0L) {
                    continue;
                }
                interactionRewardApplier.apply(accountId, itemId, rewardBones);
            }
        }

        private void applyPlayItemSettlements(GameRoom room, List<DogBattleDTO.DogBattleItemEffectDTO> itemEffects) {
            for (Map.Entry<String, String> entry : playItemIds.entrySet()) {
                String playerKey = entry.getKey();
                String itemId = entry.getValue();
                String status = null;
                if (ITEM_BATTLE_PEBBLE.equals(itemId)) {
                    status = usedPebblePlayerKeys.contains(playerKey) ? "consumed" : "refunded";
                } else if (ITEM_BATTLE_AIRBAG.equals(itemId)) {
                    status = usedAirbagPlayerKeys.contains(playerKey) ? "consumed" : "refunded";
                } else if (ITEM_BATTLE_ECHO.equals(itemId)) {
                    status = "consumed";
                }
                if (status != null) {
                    gameItemSettler.settle(room, playerKey, itemId, "gameplay", status, 0);
                    itemEffects.add(itemEffect(playerKey, itemId,
                            "consumed".equals(status) ? "PLAY_CONSUMED" : "PLAY_REFUNDED",
                            0, 0));
                }
            }
        }

        private void applyDefensiveSkill(DogBattleDTO.DogBattlePlayerDTO target, DogBattleDTO.DogBattleHitDTO hit) {
            if (target == null || hit == null || !target.getPlayerKey().equals(poodleGuardPlayerKey)) {
                return;
            }
            poodleGuardPlayerKey = null;
            if ("DOG".equals(hit.getTargetType()) && !hit.isDirectHit() && hit.getDamage() > 0) {
                hit.setDamage(Math.max(0, (int) Math.floor(hit.getDamage() * 0.9)));
            }
        }

        private void applyPlayItemEffects(DogBattleDTO.DogBattlePlayerDTO target, DogBattleDTO.DogBattleHitDTO hit,
                                          List<DogBattleDTO.DogBattleItemEffectDTO> itemEffects) {
            if (target == null
                    || hit == null
                    || hit.getDamage() <= 0
                    || !ITEM_BATTLE_AIRBAG.equals(playItemIds.get(target.getPlayerKey()))
                    || usedAirbagPlayerKeys.contains(target.getPlayerKey())) {
                return;
            }
            usedAirbagPlayerKeys.add(target.getPlayerKey());
            int blockedDamage = Math.min(15, (int) Math.floor(hit.getDamage() * 0.3));
            hit.setDamage(Math.max(5, hit.getDamage() - blockedDamage));
            itemEffects.add(itemEffect(target.getPlayerKey(), ITEM_BATTLE_AIRBAG,
                    "DAMAGE_REDUCED", blockedDamage, 0));
        }

        private void applyPostSkillEffects(
                DogBattleDTO.DogBattlePlayerDTO actor,
                DogBattleDTO.DogBattlePlayerDTO target,
                SkillTurn skillTurn,
                DogBattleDTO.DogBattleHitDTO hit
        ) {
            if (!skillTurn.used) {
                return;
            }
            if ("poodle".equals(skillTurn.breed)) {
                poodleGuardPlayerKey = actor.getPlayerKey();
            } else if ("shiba".equals(skillTurn.breed)
                    && "DOG".equals(hit.getTargetType())
                    && hit.getDamage() >= MIN_SPLASH_DAMAGE) {
                target.setSkillCooldown(Math.max(target.getSkillCooldown(), 1));
            } else if ("husky".equals(skillTurn.breed) && actor.getDog() != null) {
                actor.getDog().setProjectileSkinId(huskySkillProjectileSkin(turnNo));
            }
        }

        private void updateSkillCooldown(
                DogBattleDTO.DogBattlePlayerDTO actor,
                SkillTurn skillTurn,
                DogBattleDTO.DogBattleHitDTO hit
        ) {
            if (skillTurn.used) {
                actor.setSkillCooldown(skillTurn.cooldownAfter(hit));
                return;
            }
            if (actor.getSkillCooldown() > 0) {
                actor.setSkillCooldown(actor.getSkillCooldown() - 1);
            }
        }

        private DogBattleDTO.DogBattlePlayerDTO findPlayer(String playerKey) {
            return players.stream()
                    .filter(player -> playerKey.equals(player.getPlayerKey()))
                    .findFirst()
                    .orElse(null);
        }

        private DogBattleDTO.DogBattlePlayerDTO findOpponent(String playerKey) {
            return players.stream()
                    .filter(player -> !playerKey.equals(player.getPlayerKey()))
                    .findFirst()
                    .orElse(null);
        }

        private List<DogBattleDTO.DogBattlePlayerDTO> copyPlayers() {
            List<DogBattleDTO.DogBattlePlayerDTO> copy = new ArrayList<>();
            for (DogBattleDTO.DogBattlePlayerDTO player : players) {
                copy.add(copyPlayer(player));
            }
            return copy;
        }

        private List<DogBattleDTO.DogBattleObstacleDTO> copyObstacles() {
            List<DogBattleDTO.DogBattleObstacleDTO> copy = new ArrayList<>();
            for (DogBattleDTO.DogBattleObstacleDTO obstacle : obstacles) {
                copy.add(copyObstacle(obstacle));
            }
            return copy;
        }
    }

    private static DogBattleDTO.DogBattlePlayerDTO createPlayer(GameRoom.Player player, String side, PetDogDTO petDog) {
        DogBattleDTO.DogBattlePlayerDTO dto = new DogBattleDTO.DogBattlePlayerDTO();
        dto.setPlayerKey(player.getId());
        dto.setUsername(player.getUsername());
        dto.setSide(side);
        dto.setHp(MAX_HP);
        dto.setScore(0);
        dto.setSkillCooldown(0);
        dto.setDog(toBattleDog(player, petDog));
        return dto;
    }

    private static PetDogDTO resolvePetDog(GameRoom.Player player) {
        if (player == null || player.getAccountId() <= 0) {
            return null;
        }
        try {
            PetProfileDTO profile = PetProfileService.profile(player.getAccountId());
            if (profile == null || profile.getDogs() == null || profile.getDogs().isEmpty()) {
                return null;
            }
            if (profile.getCompanionDogId() != null) {
                for (PetDogDTO dog : profile.getDogs()) {
                    if (profile.getCompanionDogId().equals(dog.getId())) {
                        return dog;
                    }
                }
            }
            return profile.getDogs().get(0);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static DogBattleDTO.DogBattleDogDTO toBattleDog(GameRoom.Player player, PetDogDTO petDog) {
        DogBattleDTO.DogBattleDogDTO dog = new DogBattleDTO.DogBattleDogDTO();
        String breed = petDog == null ? "native" : normalizeBreed(petDog.getBreed());
        dog.setDogId(petDog == null ? player.getId() + ":default" : petDog.getId());
        dog.setName(petDog == null ? "默认狗狗" : petDog.getName());
        dog.setBreed(breed);
        dog.setSkillLevel(1);
        dog.setProjectileSkinId(defaultProjectileSkinId(breed));
        return dog;
    }

    private static DogBattleDTO.DogBattlePlayerDTO copyPlayer(DogBattleDTO.DogBattlePlayerDTO player) {
        DogBattleDTO.DogBattlePlayerDTO copy = new DogBattleDTO.DogBattlePlayerDTO();
        copy.setPlayerKey(player.getPlayerKey());
        copy.setUsername(player.getUsername());
        copy.setSide(player.getSide());
        copy.setHp(player.getHp());
        copy.setScore(player.getScore());
        copy.setSkillCooldown(player.getSkillCooldown());
        copy.setDog(player.getDog());
        return copy;
    }

    private static DogBattleDTO.DogBattleObstacleDTO copyObstacle(DogBattleDTO.DogBattleObstacleDTO obstacle) {
        DogBattleDTO.DogBattleObstacleDTO copy = new DogBattleDTO.DogBattleObstacleDTO();
        copy.setId(obstacle.getId());
        copy.setType(obstacle.getType());
        copy.setX(obstacle.getX());
        copy.setY(obstacle.getY());
        copy.setWidth(obstacle.getWidth());
        copy.setHeight(obstacle.getHeight());
        copy.setHp(obstacle.getHp());
        copy.setDestructible(obstacle.isDestructible());
        copy.setDestroyed(obstacle.isDestroyed());
        return copy;
    }

    private static List<DogBattleDTO.DogBattleTrajectoryPointDTO> copyTrajectory(
            List<DogBattleDTO.DogBattleTrajectoryPointDTO> trajectory
    ) {
        List<DogBattleDTO.DogBattleTrajectoryPointDTO> copy = new ArrayList<>();
        if (trajectory == null) {
            return copy;
        }
        for (DogBattleDTO.DogBattleTrajectoryPointDTO point : trajectory) {
            copy.add(copyPoint(point));
        }
        return copy;
    }

    private static DogBattleDTO.DogBattleTrajectoryPointDTO copyPoint(
            DogBattleDTO.DogBattleTrajectoryPointDTO point
    ) {
        DogBattleDTO.DogBattleTrajectoryPointDTO copy = new DogBattleDTO.DogBattleTrajectoryPointDTO();
        if (point == null) {
            return copy;
        }
        copy.setX(point.getX());
        copy.setY(point.getY());
        return copy;
    }

    private static DogBattleDTO.DogBattleHitDTO copyHit(DogBattleDTO.DogBattleHitDTO hit) {
        if (hit == null) {
            return null;
        }
        DogBattleDTO.DogBattleHitDTO copy = new DogBattleDTO.DogBattleHitDTO();
        copy.setTargetType(hit.getTargetType());
        copy.setTargetId(hit.getTargetId());
        copy.setX(hit.getX());
        copy.setY(hit.getY());
        copy.setDirectHit(hit.isDirectHit());
        copy.setDamage(hit.getDamage());
        return copy;
    }

    private static List<DogBattleDTO.DogBattleItemEffectDTO> copyItemEffects(
            List<DogBattleDTO.DogBattleItemEffectDTO> itemEffects
    ) {
        List<DogBattleDTO.DogBattleItemEffectDTO> copy = new ArrayList<>();
        if (itemEffects == null) {
            return copy;
        }
        for (DogBattleDTO.DogBattleItemEffectDTO itemEffect : itemEffects) {
            copy.add(copyItemEffect(itemEffect));
        }
        return copy;
    }

    private static DogBattleDTO.DogBattleItemEffectDTO copyItemEffect(
            DogBattleDTO.DogBattleItemEffectDTO itemEffect
    ) {
        DogBattleDTO.DogBattleItemEffectDTO copy = new DogBattleDTO.DogBattleItemEffectDTO();
        if (itemEffect == null) {
            return copy;
        }
        copy.setPlayerKey(itemEffect.getPlayerKey());
        copy.setItemId(itemEffect.getItemId());
        copy.setEffectType(itemEffect.getEffectType());
        copy.setDamageBlocked(itemEffect.getDamageBlocked());
        copy.setRewardBones(itemEffect.getRewardBones());
        return copy;
    }

    private static DogBattleDTO.DogBattleItemStateDTO itemState(
            String playerKey,
            String itemId,
            String slot,
            String status
    ) {
        DogBattleDTO.DogBattleItemStateDTO itemState = new DogBattleDTO.DogBattleItemStateDTO();
        itemState.setPlayerKey(playerKey);
        itemState.setItemId(itemId);
        itemState.setSlot(slot);
        itemState.setStatus(status);
        return itemState;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static DogBattleDTO.DogBattleConfigDTO config(GameRoom room) {
        DogBattleDTO.DogBattleConfigDTO config = new DogBattleDTO.DogBattleConfigDTO();
        config.setRoundCount(room.getDogBattleRoundCount());
        config.setAllowSkill(room.isDogBattleAllowSkill());
        return config;
    }

    private static List<DogBattleDTO.DogBattleObstacleDTO> generateObstacles(int roundNo) {
        List<DogBattleDTO.DogBattleObstacleDTO> obstacles = new ArrayList<>();
        DogBattleDTO.DogBattleObstacleDTO woodBox = new DogBattleDTO.DogBattleObstacleDTO();
        woodBox.setId("wood-box-" + roundNo);
        woodBox.setType("WOOD_BOX");
        woodBox.setX(470);
        woodBox.setY(390);
        woodBox.setWidth(60);
        woodBox.setHeight(110);
        woodBox.setHp(30);
        woodBox.setDestructible(true);
        woodBox.setDestroyed(false);
        obstacles.add(woodBox);
        return obstacles;
    }

    private static DogBattleDTO.DogBattleWindDTO wind(int turnNo, int power) {
        DogBattleDTO.DogBattleWindDTO wind = new DogBattleDTO.DogBattleWindDTO();
        wind.setTurnNo(turnNo);
        wind.setPower(power);
        return wind;
    }

    private static DogBattleDTO.DogBattleRewardPreviewDTO rewardPreview(String winnerPlayerKey, boolean economyApplied) {
        DogBattleDTO.DogBattleRewardPreviewDTO rewardPreview = new DogBattleDTO.DogBattleRewardPreviewDTO();
        rewardPreview.setWinnerPlayerKey(winnerPlayerKey);
        rewardPreview.setWinnerBones(WINNER_REWARD_BONES);
        rewardPreview.setLoserBones(LOSER_REWARD_BONES);
        rewardPreview.setEconomyApplied(economyApplied);
        return rewardPreview;
    }

    private static int nextWindPower(int currentWind, int nextTurnNo) {
        int delta = nextTurnNo % 3 - 1;
        return clamp(currentWind + delta, -5, 5);
    }

    private static int normalizeRoundCount(int roundCount) {
        if (roundCount == 1 || roundCount == 3 || roundCount == 5 || roundCount == 7) {
            return roundCount;
        }
        return 3;
    }

    private static String normalizeBreed(String breed) {
        if ("shiba".equals(breed)
                || "corgi".equals(breed)
                || "golden".equals(breed)
                || "border_collie".equals(breed)
                || "greyhound".equals(breed)
                || "poodle".equals(breed)
                || "native".equals(breed)
                || "husky".equals(breed)) {
            return breed;
        }
        return "native";
    }

    private static String defaultProjectileSkinId(String breed) {
        switch (breed) {
            case "corgi":
                return "corgi_bone";
            case "golden":
                return "golden_tennis";
            case "border_collie":
                return "training_frisbee";
            case "greyhound":
                return "stream_bone_dart";
            case "poodle":
                return "bow_toy_ball";
            case "shiba":
                return "meme_bone";
            case "husky":
                return "slipper";
            case "native":
            default:
                return "bowl_lid";
        }
    }

    private static String huskySkillProjectileSkin(int turnNo) {
        switch (Math.floorMod(turnNo, 3)) {
            case 0:
                return "husky_toy_fragments";
            case 1:
                return "husky_sofa_cushion";
            case 2:
            default:
                return "husky_slipper_spin";
        }
    }

    private static final class SkillTurn {
        private final boolean used;
        private final String skillName;
        private final String breed;
        private final int cooldown;

        private SkillTurn(boolean used, String skillName, String breed, int cooldown) {
            this.used = used;
            this.skillName = skillName;
            this.breed = breed;
            this.cooldown = cooldown;
        }

        private static SkillTurn from(DogBattleDTO.DogBattlePlayerDTO actor, boolean useSkill) {
            String breed = actor.getDog() == null ? "native" : actor.getDog().getBreed();
            if (!useSkill) {
                return new SkillTurn(false, null, breed, 0);
            }
            return new SkillTurn(true, skillName(breed), breed, skillCooldown(breed));
        }

        private boolean canUse(DogBattleDTO.DogBattlePlayerDTO actor, DogBattleDTO.DogBattlePlayerDTO target) {
            if (!used || !"shiba".equals(breed)) {
                return true;
            }
            return actor.getHp() < target.getHp() || actor.getScore() < target.getScore();
        }

        private int effectiveWindPower(int windPower) {
            if (used && "native".equals(breed)) {
                return (int) (windPower * 0.75);
            }
            if (used && "border_collie".equals(breed)) {
                return (int) (windPower * 0.9);
            }
            return windPower;
        }

        private double speedMultiplier() {
            if (used && "greyhound".equals(breed)) {
                return 1.12;
            }
            return 1.0;
        }

        private int directDamage() {
            if (used && "corgi".equals(breed)) {
                return CORGI_SKILL_DAMAGE;
            }
            return DIRECT_DAMAGE;
        }

        private int cooldownAfter(DogBattleDTO.DogBattleHitDTO hit) {
            if (used
                    && "golden".equals(breed)
                    && (hit == null || !"DOG".equals(hit.getTargetType()) || hit.getDamage() < MIN_SPLASH_DAMAGE)) {
                return Math.max(0, cooldown - 1);
            }
            return cooldown;
        }

        private static int skillCooldown(String breed) {
            if ("golden".equals(breed) || "greyhound".equals(breed) || "poodle".equals(breed)) {
                return 4;
            }
            if ("shiba".equals(breed) || "husky".equals(breed)) {
                return 5;
            }
            return 3;
        }

        private static String skillName(String breed) {
            switch (breed) {
                case "corgi":
                    return "短腿稳投";
                case "golden":
                    return "金球回收";
                case "border_collie":
                    return "牧羊测风";
                case "greyhound":
                    return "疾影抛射";
                case "poodle":
                    return "蓬松缓冲";
                case "shiba":
                    return "表情包反击";
                case "husky":
                    return "二哈乱抛";
                case "native":
                default:
                    return "土狗识路";
            }
        }
    }

    private static final class SimulationResult {
        private final List<DogBattleDTO.DogBattleTrajectoryPointDTO> trajectory;
        private final DogBattleDTO.DogBattleHitDTO hit;

        private SimulationResult(
                List<DogBattleDTO.DogBattleTrajectoryPointDTO> trajectory,
                DogBattleDTO.DogBattleHitDTO hit
        ) {
            this.trajectory = trajectory;
            this.hit = hit;
        }
    }

    interface RewardApplier {
        boolean apply(long winnerAccountId, long loserAccountId, int winnerBones, int loserBones);
    }

    interface InteractionRewardApplier {
        void apply(long accountId, String itemId, int requestedBones);
    }

    interface GameItemSettler {
        void settle(GameRoom room, String playerKey, String itemId, String slot, String status, int rewardBones);
    }
}
