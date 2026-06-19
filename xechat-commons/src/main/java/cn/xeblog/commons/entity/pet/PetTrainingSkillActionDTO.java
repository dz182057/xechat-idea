package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 训狗手册技能操作请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetTrainingSkillActionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String skillId;

    private String dogId;

}
