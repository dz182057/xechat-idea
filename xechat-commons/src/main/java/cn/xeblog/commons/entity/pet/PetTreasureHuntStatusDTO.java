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

    private List<PetTreasureHuntProbabilityDTO> boneProbabilities;

    private List<PetTreasureHuntProbabilityDTO> extraProbabilities;

}
