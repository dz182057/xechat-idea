package cn.xeblog.server.pet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * pet_explore_chests 表实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PetExploreChestRecord {

    private String id;
    private long accountId;
    private String chestItemId;
    private String location;
    private String sourceDogId;
    private String sourceDogName;
    private String sourceDogBreed;
    private int durationHours;
    private String skillSnapshotId;
    private Integer skillSnapshotLevel;
    private String skillSnapshotDefinitionVersion;
    private String status;
    private long createdAt;
    private Long openedAt;

}
