package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 翻转7当前轮次状态。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetFlip7RoundDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String roundId;

    private String state;

    private String playSource;

    private int paidCost;

    private List<PetFlip7CardDTO> cards;

    private List<String> detailLines;

    private int numberSum;

    private int modifierBonus;

    private int multiplier;

    private int flip7Bonus;

    private int scorePreview;

    private int score;

    private int boneReward;

    private int forcedDrawsRemaining;

    private boolean canDraw;

    private boolean canStand;

    private boolean hasSecondChance;

    private long startedAt;

    private Long settledAt;

}
