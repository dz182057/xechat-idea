package cn.xeblog.server.game.dogbattle;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.game.dogbattle.DogBattleDTO;
import cn.xeblog.commons.entity.pet.PetDogDTO;
import cn.xeblog.commons.entity.pet.PetProfileDTO;
import cn.xeblog.commons.enums.Game;
import cn.xeblog.server.pet.PetProfileService;

import java.util.ArrayList;
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
    private static final double GRAVITY = 0.35;
    private static final double WIND_ACCEL = 0.006;
    private static final int MAX_STEPS = 240;
    private static final Map<String, BattleState> STATES = new ConcurrentHashMap<>();
    private static Function<GameRoom.Player, PetDogDTO> dogResolver = DogBattleService::resolvePetDog;

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
            if (state.started || state.startedPlayerKeys.size() < room.getUsers().size()) {
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

            SkillTurn skillTurn = SkillTurn.from(actor, input.isUseSkill());
            SimulationResult simulation = simulate(
                    actor,
                    target,
                    state.obstacles,
                    skillTurn.effectiveWindPower(state.windPower),
                    skillTurn.directDamage(),
                    input
            );
            if ("DOG".equals(simulation.hit.getTargetType())) {
                target.setHp(Math.max(0, target.getHp() - simulation.hit.getDamage()));
            } else if ("OBSTACLE".equals(simulation.hit.getTargetType())) {
                state.damageObstacle(simulation.hit.getTargetId(), simulation.hit.getDamage());
            }
            state.updateSkillCooldown(actor, skillTurn);

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

            DogBattleDTO result = new DogBattleDTO();
            result.setRoomId(room.getId());
            result.setGame(Game.DOG_BATTLE);
            result.setEvent("TURN_RESULT");
            result.setRoundNo(state.roundNo);
            result.setTurnNo(state.turnNo);
            result.setActorPlayerKey(actorKey);
            result.setCurrentPlayerKey(state.currentPlayerKey);
            result.setTrajectory(simulation.trajectory);
            result.setHit(simulation.hit);
            result.setPlayers(state.copyPlayers());
            result.setWind(wind(state.turnNo, state.windPower));
            result.setObstacles(state.copyObstacles());
            result.setNextPlayerKey(nextPlayerKey);
            result.setNextWind(matchOver ? null : wind(state.turnNo, state.windPower));
            result.setUsedSkill(skillTurn.used);
            result.setSkillName(skillTurn.skillName);
            result.setRoundOver(roundOver);
            result.setMatchOver(matchOver);
            result.setWinnerPlayerKey(roundWinnerPlayerKey != null ? roundWinnerPlayerKey : state.winnerPlayerKey);
            result.setPhase(matchOver ? "matchOver" : roundOver ? "roundOver" : "playing");

            return result;
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

    private static SimulationResult simulate(
            DogBattleDTO.DogBattlePlayerDTO actor,
            DogBattleDTO.DogBattlePlayerDTO target,
            List<DogBattleDTO.DogBattleObstacleDTO> obstacles,
            int windPower,
            int directDamage,
            DogBattleDTO input
    ) {
        int angle = clamp(input.getAngle(), 0, 90);
        int power = clamp(input.getPower(), 0, 100);
        int startX = "LEFT".equals(actor.getSide()) ? LEFT_X : RIGHT_X;
        int direction = "LEFT".equals(actor.getSide()) ? 1 : -1;
        double radians = Math.toRadians(angle);
        double speed = 4 + power * 0.13;
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
                hit = hit("GROUND", null, x, GROUND_Y, false, 0);
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

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class BattleState {
        private final List<DogBattleDTO.DogBattlePlayerDTO> players;
        private final int roundCount;
        private final int requiredWins;
        private final boolean allowSkill;
        private final LinkedHashSet<String> startedPlayerKeys = new LinkedHashSet<>();
        private final List<DogBattleDTO.DogBattleObstacleDTO> obstacles = new ArrayList<>();
        private int roundNo = 1;
        private int turnNo = 1;
        private int windPower;
        private boolean started;
        private boolean matchOver;
        private String currentPlayerKey;
        private String winnerPlayerKey;

        private BattleState(List<DogBattleDTO.DogBattlePlayerDTO> players, int roundCount, boolean allowSkill) {
            this.players = players;
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
            int index = 0;
            for (GameRoom.Player player : room.getUsers().values()) {
                players.add(createPlayer(player, index == 0 ? "LEFT" : "RIGHT", dogResolver.apply(player)));
                index++;
                if (index >= 2) {
                    break;
                }
            }
            return new BattleState(players, room.getDogBattleRoundCount(), room.isDogBattleAllowSkill());
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

        private void updateSkillCooldown(DogBattleDTO.DogBattlePlayerDTO actor, SkillTurn skillTurn) {
            if (skillTurn.used) {
                actor.setSkillCooldown(skillTurn.cooldown);
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

        private int effectiveWindPower(int windPower) {
            if (used && "native".equals(breed)) {
                return (int) Math.round(windPower * 0.75);
            }
            return windPower;
        }

        private int directDamage() {
            if (used && "corgi".equals(breed)) {
                return CORGI_SKILL_DAMAGE;
            }
            return DIRECT_DAMAGE;
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
}
