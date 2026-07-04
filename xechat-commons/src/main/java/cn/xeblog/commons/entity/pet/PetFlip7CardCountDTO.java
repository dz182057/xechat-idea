package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 翻转7卡牌种类数量。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetFlip7CardCountDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String type;

    private String label;

    private Integer value;

    private Integer modifier;

    private String action;

    private int total;

    private int remaining;

}
