package cn.xeblog.commons.entity.game.quickquiz;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 快问快答剩余可用题数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuickQuizAvailableCountDTO implements Serializable {

    private String roomId;

    private int availableCount;

    private int requestedCount;

}
