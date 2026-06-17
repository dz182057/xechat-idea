package cn.xeblog.server.game.dogbattle;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.game.dogbattle.DogBattleDTO;
import cn.xeblog.commons.enums.Game;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DogBattleService {

    private static final int WORLD_WIDTH = 1000;
    private static final int GROUND_Y = 500;
    private static final int LEFT_X = 120;
    private static final int RIGHT_X = 880;
    private static final int DOG_WIDTH = 60;
    private static final int DOG_HEIGHT = 80;
    private static final int MAX_HP = 100;
    private static final int DIRECT_DAMAGE = 24;
    private static final double GRAVITY = 0.35;
    private static final int MAX_STEPS = 240;
    private static final Map<String, BattleState> STATES = new ConcurrentHashMap<>();

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

            SimulationResult simulation = simulate(actor, target, input);
            if ("DOG".equals(simulation.hit.getTargetType())) {
                target.setHp(Math.max(0, target.getHp() - simulation.hit.getDamage()));
            }

            boolean roundOver = target.getHp() <= 0;
            boolean matchOver = false;
            String nextPlayerKey = target.getPlayerKey();
            if (roundOver) {
                actor.setScore(actor.getScore() + 1);
                matchOver = true;
                state.matchOver = true;
                state.winnerPlayerKey = actor.getPlayerKey();
                nextPlayerKey = null;
            } else {
                state.currentPlayerKey = nextPlayerKey;
                state.turnNo++;
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
            result.setObstacles(new ArrayList<>());
            result.setNextPlayerKey(nextPlayerKey);
            result.setRoundOver(roundOver);
            result.setMatchOver(matchOver);
            result.setWinnerPlayerKey(state.winnerPlayerKey);
            result.setPhase(matchOver ? "matchOver" : roundOver ? "roundOver" : "playing");

            return result;
        }
    }

    public static void clearRoom(String roomId) {
        if (roomId != null) {
            STATES.remove(roomId);
        }
    }

    private static SimulationResult simulate(
            DogBattleDTO.DogBattlePlayerDTO actor,
            DogBattleDTO.DogBattlePlayerDTO target,
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
            vy += GRAVITY;
            x += vx;
            y += vy;
            if (step % 3 == 0) {
                trajectory.add(point(x, y));
            }

            if (inDogBox(x, y, target)) {
                hit = hit("DOG", target.getPlayerKey(), x, y, true, DIRECT_DAMAGE);
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
        private final LinkedHashSet<String> startedPlayerKeys = new LinkedHashSet<>();
        private int roundNo = 1;
        private int turnNo = 1;
        private boolean started;
        private boolean matchOver;
        private String currentPlayerKey;
        private String winnerPlayerKey;

        private BattleState(List<DogBattleDTO.DogBattlePlayerDTO> players) {
            this.players = players;
            if (!players.isEmpty()) {
                this.currentPlayerKey = players.get(0).getPlayerKey();
            }
        }

        private static BattleState from(GameRoom room) {
            List<DogBattleDTO.DogBattlePlayerDTO> players = new ArrayList<>();
            int index = 0;
            for (GameRoom.Player player : room.getUsers().values()) {
                players.add(createPlayer(player, index == 0 ? "LEFT" : "RIGHT"));
                index++;
                if (index >= 2) {
                    break;
                }
            }
            return new BattleState(players);
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
            snapshot.setWind(wind(turnNo));
            snapshot.setObstacles(new ArrayList<>());
            snapshot.setPhase("playing");
            snapshot.setWinnerPlayerKey(winnerPlayerKey);
            return snapshot;
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
    }

    private static DogBattleDTO.DogBattlePlayerDTO createPlayer(GameRoom.Player player, String side) {
        DogBattleDTO.DogBattlePlayerDTO dto = new DogBattleDTO.DogBattlePlayerDTO();
        dto.setPlayerKey(player.getId());
        dto.setUsername(player.getUsername());
        dto.setSide(side);
        dto.setHp(MAX_HP);
        dto.setScore(0);
        dto.setSkillCooldown(0);
        DogBattleDTO.DogBattleDogDTO dog = new DogBattleDTO.DogBattleDogDTO();
        dog.setDogId(player.getId() + ":default");
        dog.setName("默认狗狗");
        dog.setBreed("native");
        dog.setSkillLevel(1);
        dog.setProjectileSkinId("bone");
        dto.setDog(dog);
        return dto;
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

    private static DogBattleDTO.DogBattleConfigDTO config(GameRoom room) {
        DogBattleDTO.DogBattleConfigDTO config = new DogBattleDTO.DogBattleConfigDTO();
        config.setRoundCount(room.getDogBattleRoundCount());
        config.setAllowSkill(room.isDogBattleAllowSkill());
        return config;
    }

    private static DogBattleDTO.DogBattleWindDTO wind(int turnNo) {
        DogBattleDTO.DogBattleWindDTO wind = new DogBattleDTO.DogBattleWindDTO();
        wind.setTurnNo(turnNo);
        wind.setPower(0);
        return wind;
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
