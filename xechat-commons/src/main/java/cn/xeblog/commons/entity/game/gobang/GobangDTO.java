package cn.xeblog.commons.entity.game.gobang;

import cn.xeblog.commons.entity.game.GameDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author anlingyi
 * @date 2020/6/5
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GobangDTO extends GameDTO {

    private int x;
    private int y;
    private int type;
    private String event;
    private String phase;
    private int turn;
    private int winner;
    private int moveSeq;
    private String petItemNotice;
    private String petItemId;
    private Integer petItemSlotIndex;
    private Boolean petItemConsumed;
    private Integer petItemGuardX;
    private Integer petItemGuardY;

    public GobangDTO(int x, int y, int type) {
        this.x = x;
        this.y = y;
        this.type = type;
    }

}
