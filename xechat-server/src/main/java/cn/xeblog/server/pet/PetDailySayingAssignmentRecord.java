package cn.xeblog.server.pet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * pet_daily_saying_assignment 表实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PetDailySayingAssignmentRecord {

    private String assignmentId;
    private long accountId;
    private String petId;
    private String petNameSnapshot;
    private String petBreedSnapshot;
    private String petStageSnapshot;
    private String contentId;
    private String assignedServerDate;
    private String status;
    private long assignedAt;
    private Long readAt;
    private String readServerDate;
    private Boolean greetingRewardApplied;
    private Integer greetingIntimacyDelta;
    private String contentVersion;

}
