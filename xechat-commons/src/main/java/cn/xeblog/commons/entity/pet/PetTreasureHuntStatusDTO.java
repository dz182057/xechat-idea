package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 汪汪寻宝状态和规则。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetTreasureHuntStatusDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String serverDate;

    private int dailyFreeLimit;

    private int dailyFreeUsed;

    private int dailyFreeRemaining;

    private int bonusSpins;

    private int paidSpinCost;

    private String skinTicketItemId;

    private int skinTicketsPerSkin;

    /**
     * 传说皮肤保底次数。
     */
    private int legendSkinPityLimit;

    /**
     * 当前距离上次传说皮肤的寻宝次数。
     */
    private int legendSkinPityProgress;

    /**
     * 距离触发传说皮肤保底还需要的寻宝次数。
     */
    private int legendSkinPityRemaining;

    /**
     * 兼容旧客户端，当前与 extraProbabilities 一样表示单格图标概率。
     */
    private List<PetTreasureHuntProbabilityDTO> boneProbabilities;

    /**
     * 单格图标概率；三个格子独立抽取，三连只额外赠送一次寻宝。
     */
    private List<PetTreasureHuntProbabilityDTO> extraProbabilities;

}
