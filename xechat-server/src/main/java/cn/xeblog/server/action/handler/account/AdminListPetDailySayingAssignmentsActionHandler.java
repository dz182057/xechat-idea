package cn.xeblog.server.action.handler.account;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.pet.AdminListPetDailySayingAssignmentsDTO;
import cn.xeblog.commons.entity.pet.AdminPetDailySayingAssignmentListDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.action.handler.AbstractActionHandler;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.pet.PetDailySayingService;
import lombok.extern.slf4j.Slf4j;

/**
 * 管理员查看狗狗每日问候分配记录。
 */
@Slf4j
@DoAction(Action.ADMIN_LIST_PET_DAILY_SAYING_ASSIGNMENTS)
public class AdminListPetDailySayingAssignmentsActionHandler
        extends AbstractActionHandler<AdminListPetDailySayingAssignmentsDTO> {

    @Override
    protected void process(User user, AdminListPetDailySayingAssignmentsDTO body) {
        if (!user.isAdmin()) {
            user.send(ResponseBuilder.system("仅管理员可查看狗狗问候接收记录"));
            return;
        }
        try {
            AdminPetDailySayingAssignmentListDTO result = PetDailySayingService.adminListAssignments(body);
            user.send(ResponseBuilder.build(null, result, MessageType.PET_DAILY_SAYING_ASSIGNMENT_LIST));
        } catch (IllegalArgumentException e) {
            user.send(ResponseBuilder.system(e.getMessage()));
        } catch (Exception e) {
            log.error("查询狗狗问候接收记录异常", e);
            user.send(ResponseBuilder.system("查询狗狗问候接收记录失败"));
        }
    }

}
