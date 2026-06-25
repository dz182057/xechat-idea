package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 狗狗宇宙签到状态快照。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetCheckinStatusDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String serverDate;

    private boolean todayCheckedIn;

    private int cycleDay;

    private int totalCheckins;

    private int milestoneRemaining;

    private String makeupAvailableSince;

    private List<String> checkedDatesInMonth;

    private PetCheckinMilestoneRewardDTO lastMilestoneReward;

}
