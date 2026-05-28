package cn.xeblog.commons.entity.game.quickquiz;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 管理员保存快问快答题库。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminSaveQuickQuizQuestionsDTO implements Serializable {

    private List<QuickQuizQuestionDTO> questions;

}
