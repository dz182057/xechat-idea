package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 汪汪寻宝概率展示项，probabilityBp 为万分比。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetTreasureHuntProbabilityDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String type;

    private String label;

    private int probabilityBp;

    private Integer value;

}
