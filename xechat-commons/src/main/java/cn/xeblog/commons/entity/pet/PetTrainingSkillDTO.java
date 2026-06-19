package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 账号已学习的训狗技能。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetTrainingSkillDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String skillId;

    private int level;

    private String definitionVersion;

}
