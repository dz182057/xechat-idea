package cn.xeblog.commons.entity.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 玩家在单局游戏中声明的狗狗携带道具。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GamePlayerPetItemsDTO implements Serializable {

    /**
     * 兼容字段：界面统一展示为携带栏 1。
     */
    private String petPlayItemId;

    /**
     * 兼容字段：界面统一展示为携带栏 2。
     */
    private String petInteractionItemId;

}
