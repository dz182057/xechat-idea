package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 狗狗宇宙累计签到里程碑奖励快照。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetCheckinMilestoneRewardDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private int milestoneIndex;

    private String decorationId;

    private String itemId;

    private int overflowBones;

}
