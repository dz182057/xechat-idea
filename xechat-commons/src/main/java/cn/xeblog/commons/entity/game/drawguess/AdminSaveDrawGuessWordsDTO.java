package cn.xeblog.commons.entity.game.drawguess;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 管理员保存你画我猜词库请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminSaveDrawGuessWordsDTO implements Serializable {

    private List<DrawGuessWordDTO> words;

}
