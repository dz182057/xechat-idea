package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 狗狗宇宙道具出售请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetSellItemDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String itemId;

    private int quantity;

}
