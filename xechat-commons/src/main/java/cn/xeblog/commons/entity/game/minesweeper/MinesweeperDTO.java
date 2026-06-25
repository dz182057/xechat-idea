package cn.xeblog.commons.entity.game.minesweeper;

import cn.xeblog.commons.entity.game.GameDTO;
import cn.xeblog.commons.enums.Game;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 扫雷联机事件。
 */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MinesweeperDTO extends GameDTO {

    private Event event;

    private ActionType action;

    private Integer x;

    private Integer y;

    private Integer rows;

    private Integer cols;

    private Integer mines;

    private List<MinesweeperCellDTO> cells;

    private String actorKey;

    private String actorName;

    private String nextTurnPlayerKey;

    private Phase phase;

    private Integer openedCount;

    private Boolean hitMine;

    private Boolean won;

    private Boolean restartApproved;

    private String petItemId;

    private String petItemName;

    private String petItemDescription;

    private String petItemIconSrc;

    private String petItemTriggerLabel;

    private String petItemNotice;

    private Integer petItemSlotIndex;

    private Boolean petItemConsumed;

    private Integer petItemTargetX;

    private Integer petItemTargetY;

    private Integer petItemCounterMines;

    private Long petItemExpiresAt;

    private Boolean assisted;

    public MinesweeperDTO(String roomId) {
        super(roomId, Game.MINESWEEPER);
    }

    public enum Event {
        INIT,
        ACTION_REQUEST,
        STATE_PATCH,
        SHARED_MARK,
        GAME_RESULT,
        SYNC_REQUEST,
        SYNC_SNAPSHOT,
        RESTART_REQUEST,
        RESTART_RESPONSE,
        SERVER_START_REQUEST,
        SERVER_START_RESPONSE,
        SERVER_ACTION_REQUEST,
        ITEM_USE_REQUEST,
        ITEM_EFFECT,
        SERVER_ERROR
    }

    public enum ActionType {
        OPEN,
        OPEN_AROUND
    }

    public enum Phase {
        playing,
        won,
        lost
    }

}
