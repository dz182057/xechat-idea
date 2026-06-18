package cn.xeblog.server.pet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * dogs 表实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PetDogRecord {

    private String id;
    private long ownerId;
    private String name;
    private String breed;
    private String stage;
    private int speed;
    private int stamina;
    private int burst;
    private int wisdom;
    private int bond;
    private int energy;
    private String energyDate;
    private String status;
    private String exploreLocation;
    private Long exploreEndsAt;
    private Integer exploreDurationHours;
    private int raceCount;
    private int raceFirstCount;
    private int weeklyPoints;
    private long createdAt;
    private long updatedAt;

}
