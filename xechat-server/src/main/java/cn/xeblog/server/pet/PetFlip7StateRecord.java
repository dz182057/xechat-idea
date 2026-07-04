package cn.xeblog.server.pet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * pet_flip7_states 表实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PetFlip7StateRecord {

    private long accountId;
    private String stateDate;
    private String drawPileJson;
    private String discardPileJson;
    private String activeRoundJson;
    private long updatedAt;

}
