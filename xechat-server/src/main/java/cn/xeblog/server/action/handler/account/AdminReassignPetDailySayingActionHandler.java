package cn.xeblog.server.action.handler.account;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.pet.AdminPetDailySayingAssignmentListDTO;
import cn.xeblog.commons.entity.pet.AdminReassignPetDailySayingDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.action.handler.AbstractActionHandler;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.pet.PetDailySayingService;
import lombok.extern.slf4j.Slf4j;

/**
 * 管理员重新分配狗狗每日问候。
 */
@Slf4j
@DoAction(Action.ADMIN_REASSIGN_PET_DAILY_SAYING)
public class AdminReassignPetDailySayingActionHandler extends AbstractActionHandler<AdminReassignPetDailySayingDTO> {

    @Override
    protected void process(User user, AdminReassignPetDailySayingDTO body) {
        if (!user.isAdmin()) {
            user.send(ResponseBuilder.system("仅管理员可重新分配狗狗问候"));
            return;
        }
        try {
            AdminPetDailySayingAssignmentListDTO result = PetDailySayingService.adminReassign(body);
            user.send(ResponseBuilder.build(null, result, MessageType.PET_DAILY_SAYING_ASSIGNMENT_LIST));
            user.send(ResponseBuilder.system("狗狗问候已重新分配"));
        } catch (IllegalArgumentException e) {
            user.send(ResponseBuilder.system(e.getMessage()));
        } catch (Exception e) {
            log.error("重新分配狗狗问候异常", e);
            user.send(ResponseBuilder.system("重新分配狗狗问候失败"));
        }
    }

}
