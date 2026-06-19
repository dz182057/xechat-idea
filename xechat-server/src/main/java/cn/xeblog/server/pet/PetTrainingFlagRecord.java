package cn.xeblog.server.pet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * pet_training_flags 表实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PetTrainingFlagRecord {

    private long accountId;
    private int firstExploreFreeAvailable;
    private int firstExploreFreeUsed;
    private long updatedAt;

}
