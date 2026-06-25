package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 首页狗狗每日问候状态。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetDailySayingDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String state;
    private String assignmentId;
    private String assignedServerDate;
    private PetSnapshot pet;
    private PetDailySayingContentDTO content;
    private Long readAt;
    private Boolean greetingRewardApplied;
    private Integer greetingIntimacyDelta;

    public static PetDailySayingDTO none() {
        return new PetDailySayingDTO("NONE", null, null, null, null, null, null, null);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PetSnapshot implements Serializable {

        private static final long serialVersionUID = 1L;

        private String id;
        private String name;
        private String avatar;

    }

}
