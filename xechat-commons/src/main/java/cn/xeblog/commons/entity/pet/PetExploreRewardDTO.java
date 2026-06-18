package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 狗狗探险单项奖励。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetExploreRewardDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String type;

    private String itemId;

    private int amount;

}
