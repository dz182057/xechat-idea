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
    private int bond;
    private String status;
    private String exploreLocation;
    private Long exploreEndsAt;
    private Integer exploreDurationHours;
    private String exploreSkillId;
    private String exploreSkillSnapshotId;
    private Integer exploreSkillSnapshotLevel;
    private String exploreSkillSnapshotVersion;
    private int raceCount;
    private int raceFirstCount;
    private int weeklyPoints;
    private long createdAt;
    private long updatedAt;

}
