package cn.xeblog.server.pet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * pet_checkins 表实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PetCheckinRecord {

    private long accountId;
    private String checkinDate;
    private int cycleDay;
    private long createdAt;

}
