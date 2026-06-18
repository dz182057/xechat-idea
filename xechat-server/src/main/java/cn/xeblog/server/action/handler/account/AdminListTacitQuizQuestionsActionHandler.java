package cn.xeblog.server.action.handler.account;

import cn.xeblog.commons.entity.User;
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
 * 管理员查看默契问答题库。
 */
@Slf4j
@DoAction(Action.ADMIN_LIST_TACIT_QUIZ_QUESTIONS)
public class AdminListTacitQuizQuestionsActionHandler extends AbstractActionHandler<Object> {

    @Override
    protected void process(User user, Object body) {
        if (!user.isAdmin()) {
            user.send(ResponseBuilder.system("仅管理员可查看默契问答题库"));
            return;
        }
        try {
            List<TacitQuizQuestionDTO> questions = TacitQuizService.listQuestions();
            user.send(ResponseBuilder.build(null, questions, MessageType.TACIT_QUIZ_QUESTION_BANK));
        } catch (Exception e) {
            log.error("查询默契问答题库异常", e);
            user.send(ResponseBuilder.system("查询默契问答题库失败"));
        }
    }

}
