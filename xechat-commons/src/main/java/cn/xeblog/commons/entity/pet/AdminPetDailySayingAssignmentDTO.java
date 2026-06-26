package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 狗狗每日问候分配记录视图。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminPetDailySayingAssignmentDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String assignmentId;
    private long accountId;
    private String account;
    private String nickname;
    private String dogId;
    private String dogNameSnapshot;
    private String dogAvatarSnapshot;
    private String contentId;
    private String category;
    private String title;
    private String primaryText;
    private String secondaryText;
    private String assignedServerDate;
    private String status;
    private long assignedAt;
    private Long readAt;
    private String readServerDate;
    private Boolean greetingRewardApplied;
    private Integer greetingIntimacyDelta;
    private String contentVersion;

}
