package cn.xeblog.server.pet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * pet_assets 表实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PetAssetsRecord {

    private long accountId;
    private int bones;
    private int food;
    private int makeupCards;
    private int dogSlots;
    private int energyLimit;
    private String companionDogId;
    private long createdAt;
    private long updatedAt;

}
