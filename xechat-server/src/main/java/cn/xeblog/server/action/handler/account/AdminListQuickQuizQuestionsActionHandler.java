package cn.xeblog.server.action.handler.account;

import cn.xeblog.commons.entity.User;
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
 * 管理员查看快问快答题库。
 */
@Slf4j
@DoAction(Action.ADMIN_LIST_QUICK_QUIZ_QUESTIONS)
public class AdminListQuickQuizQuestionsActionHandler extends AbstractActionHandler<Object> {

    @Override
    protected void process(User user, Object body) {
        if (!user.isAdmin()) {
            user.send(ResponseBuilder.system("仅管理员可查看快问快答题库"));
            return;
        }
        try {
            List<QuickQuizQuestionDTO> questions = QuickQuizService.listQuestions();
            user.send(ResponseBuilder.build(null, questions, MessageType.QUICK_QUIZ_QUESTION_BANK));
        } catch (Exception e) {
            log.error("查询快问快答题库异常", e);
            user.send(ResponseBuilder.system("查询快问快答题库失败"));
        }
    }

}
