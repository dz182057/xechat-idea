package cn.xeblog.commons.entity.game.tacitquiz;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 默契问答剩余可用题数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TacitQuizAvailableCountDTO implements Serializable {

    private String roomId;

    private int availableCount;

    private int requestedCount;

}
