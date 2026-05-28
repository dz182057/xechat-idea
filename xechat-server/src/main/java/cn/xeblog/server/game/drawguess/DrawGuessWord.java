package cn.xeblog.server.game.drawguess;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * draw_guess_words 表实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrawGuessWord {

    private Long id;
    private String word;
    private String hint;
    private int sortOrder;
    private long createdAt;
    private long updatedAt;

}
