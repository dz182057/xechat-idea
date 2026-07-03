package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 汪汪寻宝皮肤碎片兑换皮肤请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetTreasureHuntRedeemSkinDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String skinItemId;

}
