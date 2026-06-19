package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 狗狗宇宙狗狗资料。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetDogDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;

    private String name;

    private String breed;

    private String stage;

    private int speed;

    private int stamina;

    private int burst;

    private int wisdom;

    private int bond;

    private int energy;

    private String status;

    private String exploreLocation;

    private Long exploreEndsAt;

    private String exploreSkillId;

    private String exploreSkillSnapshotId;

    private Integer exploreSkillSnapshotLevel;

    private String exploreSkillSnapshotDefinitionVersion;

    private int raceCount;

    private int raceFirstCount;

    private int weeklyPoints;

}
