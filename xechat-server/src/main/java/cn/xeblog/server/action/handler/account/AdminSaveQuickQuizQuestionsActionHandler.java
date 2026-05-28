package cn.xeblog.server.action.handler.account;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.quickquiz.AdminSaveQuickQuizQuestionsDTO;
import cn.xeblog.commons.entity.game.quickquiz.QuickQuizQuestionDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.action.handler.AbstractActionHandler;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.game.quickquiz.QuickQuizService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 管理员保存快问快答题库。
 */
@Slf4j
@DoAction(Action.ADMIN_SAVE_QUICK_QUIZ_QUESTIONS)
public class AdminSaveQuickQuizQuestionsActionHandler extends AbstractActionHandler<AdminSaveQuickQuizQuestionsDTO> {

    @Override
    protected void process(User user, AdminSaveQuickQuizQuestionsDTO body) {
        if (!user.isAdmin()) {
            user.send(ResponseBuilder.system("仅管理员可编辑快问快答题库"));
            return;
        }
        try {
            List<QuickQuizQuestionDTO> questions = QuickQuizService.saveQuestions(body == null ? null : body.getQuestions());
            user.send(ResponseBuilder.build(null, questions, MessageType.QUICK_QUIZ_QUESTION_BANK));
            user.send(ResponseBuilder.system("快问快答题库已保存"));
        } catch (IllegalArgumentException e) {
            user.send(ResponseBuilder.system(e.getMessage()));
        } catch (Exception e) {
            log.error("保存快问快答题库异常", e);
            user.send(ResponseBuilder.system("保存快问快答题库失败"));
        }
    }

}
