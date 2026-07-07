package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 汪汪寻宝抽奖记录。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetTreasureHuntRecordDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String recordId;

    private int spinCount;

    private String spinSource;

    private int paidCost;

    private int boneReward;

    private String extraRewardText;

    private int bonusSpinReward;

    private List<String> symbols;

    private List<String> detailLines;

    private long createdAt;

}
