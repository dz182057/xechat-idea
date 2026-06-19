package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 训狗手册账号级状态。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetTrainingStatusDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String definitionVersion;

    private List<Integer> upgradeCosts;

    private List<PetTrainingSkillDefinitionDTO> definitions;

    private List<PetTrainingSkillDTO> skills;

    private boolean freeLearnAvailable;

}
