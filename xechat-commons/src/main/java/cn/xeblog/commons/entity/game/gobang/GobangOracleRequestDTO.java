package cn.xeblog.commons.entity.game.gobang;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 五子棋天元罗盘推荐请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GobangOracleRequestDTO {

    private String requestId;
    private int[][] board;
    private int type;
    private int moveSeq;

}
