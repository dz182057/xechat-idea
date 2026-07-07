package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 汪汪寻宝单次结算。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetTreasureHuntSpinRoundDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String spinSource;

    private int paidCost;

    private int boneReward;

    private PetTreasureHuntExtraRewardDTO extraReward;

    private List<PetTreasureHuntExtraRewardDTO> extraRewards;

    private int bonusSpinReward;

    private List<String> symbols;

    private List<String> detailLines;

    private boolean legendSkinPityTriggered;

}
