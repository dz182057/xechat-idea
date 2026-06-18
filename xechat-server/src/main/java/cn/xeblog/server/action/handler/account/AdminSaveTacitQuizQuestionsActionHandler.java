package cn.xeblog.server.action.handler.account;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.tacitquiz.AdminSaveTacitQuizQuestionsDTO;
import cn.xeblog.commons.entity.game.tacitquiz.TacitQuizQuestionDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.action.handler.AbstractActionHandler;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.game.tacitquiz.TacitQuizService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 管理员保存默契问答题库。
 */
@Slf4j
@DoAction(Action.ADMIN_SAVE_TACIT_QUIZ_QUESTIONS)
public class AdminSaveTacitQuizQuestionsActionHandler extends AbstractActionHandler<AdminSaveTacitQuizQuestionsDTO> {

    @Override
    protected void process(User user, AdminSaveTacitQuizQuestionsDTO body) {
        if (!user.isAdmin()) {
            user.send(ResponseBuilder.system("仅管理员可编辑默契问答题库"));
            return;
        }
        try {
            List<TacitQuizQuestionDTO> questions = TacitQuizService.saveQuestions(body == null ? null : body.getQuestions());
            user.send(ResponseBuilder.build(null, questions, MessageType.TACIT_QUIZ_QUESTION_BANK));
            user.send(ResponseBuilder.system("默契问答题库已保存"));
        } catch (IllegalArgumentException e) {
            user.send(ResponseBuilder.system(e.getMessage()));
        } catch (Exception e) {
            log.error("保存默契问答题库异常", e);
            user.send(ResponseBuilder.system("保存默契问答题库失败"));
        }
    }

}
