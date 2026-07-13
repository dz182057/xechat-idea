package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 管理员发放用的狗狗之家皮肤目录项。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminPetSkinDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 皮肤道具 ID。
     */
    private String itemId;

    /**
     * 皮肤名称。
     */
    private String name;

    /**
     * 稀有度，使用 COMMON / RARE / EPIC / LEGENDARY。
     */
    private String rarity;

    /**
     * 适用游戏名称。
     */
    private List<String> relatedGames;

}
