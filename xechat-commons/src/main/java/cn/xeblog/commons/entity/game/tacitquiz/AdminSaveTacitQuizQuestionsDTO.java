package cn.xeblog.commons.entity.game.tacitquiz;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 管理员保存默契问答题库。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminSaveTacitQuizQuestionsDTO implements Serializable {

    private List<TacitQuizQuestionDTO> questions;

}
