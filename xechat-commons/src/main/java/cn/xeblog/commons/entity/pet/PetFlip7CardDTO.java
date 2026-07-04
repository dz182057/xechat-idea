package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 翻转7单张已揭示卡牌。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetFlip7CardDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String type;

    private String label;

    private Integer value;

    private Integer modifier;

    private String action;

    private boolean usedSecondChance;

    private boolean bust;

    private int scoreAfterCard;

}
