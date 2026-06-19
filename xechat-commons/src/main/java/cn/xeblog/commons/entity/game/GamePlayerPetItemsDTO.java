package cn.xeblog.commons.entity.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 玩家在单局游戏中声明的狗狗道具槽。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GamePlayerPetItemsDTO implements Serializable {

    /**
     * 玩法槽 itemId。
     */
    private String petPlayItemId;

    /**
     * 互动槽 itemId。
     */
    private String petInteractionItemId;

}
