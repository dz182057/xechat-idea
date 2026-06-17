package cn.xeblog.commons.entity.game.dogbattle;

import cn.xeblog.commons.entity.game.GameDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class DogBattleDTO extends GameDTO {

    private String event;
    private DogBattleConfigDTO config;
    private int roundNo;
    private int turnNo;
    private String currentPlayerKey;
    private List<DogBattlePlayerDTO> players;
    private DogBattleWindDTO wind;
    private List<DogBattleObstacleDTO> obstacles;
    private String phase;
    private String winnerPlayerKey;

    private String actorPlayerKey;
    private List<DogBattleTrajectoryPointDTO> trajectory;
    private DogBattleHitDTO hit;
    private String nextPlayerKey;
    private boolean roundOver;
    private boolean matchOver;

    private int angle;
    private int power;
    private boolean useSkill;

    @Data
    public static class DogBattleConfigDTO implements Serializable {
        private int roundCount;
        private boolean allowSkill;
    }

    @Data
    public static class DogBattleDogDTO implements Serializable {
        private String dogId;
        private String name;
        private String breed;
        private int skillLevel;
        private String projectileSkinId;
    }

    @Data
    public static class DogBattlePlayerDTO implements Serializable {
        private String playerKey;
        private String username;
        private DogBattleDogDTO dog;
        private String side;
        private int hp;
        private int score;
        private int skillCooldown;
    }

    @Data
    public static class DogBattleObstacleDTO implements Serializable {
        private String id;
        private String type;
        private int x;
        private int y;
        private int width;
        private int height;
        private Integer hp;
        private boolean destructible;
        private boolean destroyed;
    }

    @Data
    public static class DogBattleWindDTO implements Serializable {
        private int turnNo;
        private int power;
    }

    @Data
    public static class DogBattleTrajectoryPointDTO implements Serializable {
        private int x;
        private int y;
    }

    @Data
    public static class DogBattleHitDTO implements Serializable {
        private String targetType;
        private String targetId;
        private int x;
        private int y;
        private boolean directHit;
        private int damage;
    }
}
