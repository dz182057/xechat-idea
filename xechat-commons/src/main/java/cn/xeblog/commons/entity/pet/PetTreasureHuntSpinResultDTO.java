package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 汪汪寻宝摇奖结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetTreasureHuntSpinResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private PetProfileDTO profile;

    private String spinSource;

    private int paidCost;

    private int boneReward;

    private PetTreasureHuntExtraRewardDTO extraReward;

    private List<PetTreasureHuntExtraRewardDTO> extraRewards;

    private int bonusSpinReward;

    private List<String> symbols;

    private List<String> detailLines;

}
