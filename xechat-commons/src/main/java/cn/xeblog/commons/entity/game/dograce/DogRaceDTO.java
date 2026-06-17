package cn.xeblog.commons.entity.game.dograce;

import cn.xeblog.commons.entity.game.GameDTO;
import cn.xeblog.commons.enums.Game;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DogRaceDTO extends GameDTO {

    private static final long serialVersionUID = 1L;

    private Event event;

    private String mode;

    private String phase;

    private int legNo;

    private Die die;

    private List<Participant> participants = new ArrayList<>();

    private List<Cat> cats = new ArrayList<>();

    private List<Tile> tiles = new ArrayList<>();

    private List<Ranking> rankings = new ArrayList<>();

    private String broadcast;

    private List<String> broadcasts = new ArrayList<>();

    private String skillName;

    private String message;

    private String dogId;

    private String betKind;

    private int cell;

    private String tileType;

    public DogRaceDTO(String roomId) {
        super(roomId, Game.DOG_RACE);
    }

    public enum Event {
        RACE_INIT,
        SIGNUP,
        RACE_START,
        ROLL,
        BET_LEG,
        BET_FINAL,
        PLACE_TILE,
        LEG_SETTLE,
        RACE_SETTLE,
        SNAPSHOT,
        ROLL_REQ,
        BET_LEG_REQ,
        BET_FINAL_REQ,
        PLACE_TILE_REQ,
        ERROR
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Participant {
        private int slot;
        private String dogId;
        private String name;
        private String breed;
        private String ownerPlayerKey;
        private String ownerName;
        private int position;
        private int stackIndex;
        private Integer rank;
        private String skillName;
        private boolean skillTriggered;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Cat {
        private String catId;
        private String name;
        private int position;
        private int stackIndex;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Tile {
        private int cell;
        private String tileType;
        private String ownerPlayerKey;
        private String ownerName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Die {
        private String type;
        private int slot;
        private String dogId;
        private String catId;
        private int steps;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Ranking {
        private String dogId;
        private int slot;
        private int rank;
        private String ownerPlayerKey;
        private Integer rewardBones;
        private Integer weeklyPoints;
    }
}
