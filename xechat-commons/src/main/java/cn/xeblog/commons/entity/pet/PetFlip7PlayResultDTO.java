package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 翻转7单局结算结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetFlip7PlayResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private PetProfileDTO profile;

    private String playSource;

    private int paidCost;

    private int score;

    private int boneReward;

    private String result;

    private List<PetFlip7CardDTO> cards;

    private List<String> detailLines;

    private int numberSum;

    private int modifierBonus;

    private int multiplier;

    private int flip7Bonus;

}
