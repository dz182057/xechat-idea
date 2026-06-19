package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 狗狗道具互动奖金今日使用状态。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetInteractionStatusDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private int dailyBonusCap;

    private int dailyBonusUsed;

    private int dailyBonusRemaining;

    private int sameItemDailyRewardLimit;

    private Map<String, Integer> itemRewardCounts = new HashMap<>();

    private Map<String, Integer> itemRemainingRewardCounts = new HashMap<>();
}
