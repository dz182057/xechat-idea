package cn.xeblog.server.pet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * pet_training_skills 表实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PetTrainingSkillRecord {

    private long accountId;
    private String skillId;
    private int level;
    private String definitionVersion;
    private long updatedAt;

}
