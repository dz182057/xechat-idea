package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * ADMIN_GRANT_PET_RESOURCE 响应。
 *
 * @author dz
 * @date 2026/7/1
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminPetResourceGrantResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long targetAccountId;

    private String account;

    private String nickname;

    private String resourceType;

    private String itemId;

    private Integer quantity;

    private Integer beforeAmount;

    private Integer afterAmount;

    private String note;

}
