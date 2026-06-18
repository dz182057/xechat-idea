package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 狗狗宇宙背包库存项快照。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetInventoryItemDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String itemId;

    private int count;

}
