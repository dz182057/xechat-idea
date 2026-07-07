package cn.xeblog.server.pet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * pet_treasure_hunt_records 表实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PetTreasureHuntRecord {

    private String recordId;
    private long accountId;
    private int spinCount;
    private String spinSource;
    private int paidCost;
    private int boneReward;
    private String extraRewardText;
    private int bonusSpinReward;
    private String symbolsJson;
    private String detailLinesJson;
    private long createdAt;

}
