package cn.xeblog.commons.entity.game.gobang;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 五子棋本地棋局留痕上报。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GobangHistoryReportDTO {

    private String sessionId;
    private String event;
    private String mode;
    private String aiDifficulty;
    private Integer myType;
    private Integer turn;
    private Integer winner;
    private String phase;
    private Integer moveSeq;
    private Integer lastX;
    private Integer lastY;
    private Integer lastType;
    private String notice;
    private int[][] board;
    private List<GobangDTO> moveHistory;

}
