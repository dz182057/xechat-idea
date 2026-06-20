package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 狗狗探险箱子实例快照。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetExploreChestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;

    private String chestItemId;

    private String location;

    private String sourceDogId;

    private String sourceDogName;

    private String sourceDogBreed;

    private int durationHours;

    private String skillSnapshotId;

    private Integer skillSnapshotLevel;

    private String skillSnapshotDefinitionVersion;

    private long createdAt;

}
