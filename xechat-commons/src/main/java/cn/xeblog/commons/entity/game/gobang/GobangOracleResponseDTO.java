package cn.xeblog.commons.entity.game.gobang;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 五子棋天元罗盘推荐响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GobangOracleResponseDTO {

    private String requestId;
    private int moveSeq;
    private Integer x;
    private Integer y;
    private Integer type;
    private Integer score;
    private String reason;
    private boolean success;
    private String error;

}
