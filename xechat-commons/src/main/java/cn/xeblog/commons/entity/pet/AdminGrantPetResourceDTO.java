package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * ADMIN_GRANT_PET_RESOURCE 请求。
 *
 * @author dz
 * @date 2026/7/1
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminGrantPetResourceDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 目标账号 ID。
     */
    private Long targetAccountId;

    /**
     * 资源类型：BONES / FOOD / MAKEUP_CARD / ENERGY / ITEM / COLLECTION。
     */
    private String resourceType;

    /**
     * 道具或收藏品 ID；资源类型为 ITEM / COLLECTION 时必填。
     */
    private String itemId;

    /**
     * 发放数量，必须为正数。
     */
    private Integer quantity;

    /**
     * 管理员备注。
     */
    private String note;

}
