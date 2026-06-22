package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 狗狗宇宙账号资源。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetAssetsDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private int bones;

    private int food;

    private int makeupCards;

    private int dogSlots;

    private int energy;

    private String energyDate;

    private int energyLimit;

}
