package cn.xeblog.commons.entity.game.minesweeper;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 扫雷可见格子状态。
 */
@Data
@NoArgsConstructor
public class MinesweeperCellDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private int x;

    private int y;

    private boolean opened;

    private Integer adjacentMines;

    private Boolean sharedMarked;

    private Boolean hasMine;

    private Boolean exploded;

}
