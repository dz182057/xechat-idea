package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 翻转7开始、翻牌或停牌后的结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetFlip7ActionResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private PetProfileDTO profile;

    private PetFlip7StatusDTO status;

    private PetFlip7RoundDTO round;

    private String event;

    private PetFlip7CardDTO drawnCard;

    private String message;

}
