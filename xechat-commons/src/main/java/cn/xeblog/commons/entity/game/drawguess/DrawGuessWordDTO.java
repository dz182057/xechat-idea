package cn.xeblog.commons.entity.game.drawguess;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 你画我猜词库条目。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DrawGuessWordDTO implements Serializable {

    private String word;
    private String hint;

}
