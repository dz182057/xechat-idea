package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 狗狗宇宙道具使用请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetUseItemDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String itemId;

    private String dogId;

    private String chestId;

    private Integer quantity;

}
