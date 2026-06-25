package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 当前账号待读的狗狗每日问候。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetDailySayingDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String state;
    private String assignmentId;
    private String assignedServerDate;
    private String petId;
    private String petName;
    private String petBreed;
    private String petStage;
    private PetDailySayingContentDTO content;
    private Long readAt;
    private Boolean greetingRewardApplied;
    private Integer greetingIntimacyDelta;

}
