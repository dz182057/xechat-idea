package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 汪汪寻宝摇奖请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetTreasureHuntSpinRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 本次连续寻宝次数，当前支持 1 次或 10 次。
     */
    private int spinCount;

}
