package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 训狗手册技能定义。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetTrainingSkillDefinitionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String skillId;

    private String name;

    private String emoji;

    private String description;

    private int maxLevel;

    private List<String> levelEffects;

}
