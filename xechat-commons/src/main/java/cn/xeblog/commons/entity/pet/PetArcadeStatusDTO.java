package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 狗狗之家小游戏聚合状态。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetArcadeStatusDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private PetTreasureHuntStatusDTO treasureHunt;

}
