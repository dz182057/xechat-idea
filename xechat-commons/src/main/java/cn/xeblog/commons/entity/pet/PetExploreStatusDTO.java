package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 狗狗宇宙探险状态快照。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetExploreStatusDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private int dailyStartLimit;

    private int dailyStartsUsed;

    private int dailyItemGainLimit;

    private int dailyItemGainsUsed;

    private int treasureMapFragments;

    private boolean huskyUnlocked;

}
