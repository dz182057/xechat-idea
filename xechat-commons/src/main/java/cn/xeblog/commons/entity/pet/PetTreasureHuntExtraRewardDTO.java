package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 汪汪寻宝追加奖励。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetTreasureHuntExtraRewardDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String type;

    private String itemId;

    private String label;

    private int quantity;

}
