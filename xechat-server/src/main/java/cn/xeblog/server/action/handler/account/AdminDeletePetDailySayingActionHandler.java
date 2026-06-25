package cn.xeblog.server.action.handler.account;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.pet.AdminDeletePetDailySayingDTO;
import cn.xeblog.commons.entity.pet.AdminPetDailySayingContentListDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.action.handler.AbstractActionHandler;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.pet.PetDailySayingService;
import lombok.extern.slf4j.Slf4j;

/**
 * 管理员下架狗狗每日问候内容。
 */
@Slf4j
@DoAction(Action.ADMIN_DELETE_PET_DAILY_SAYING)
public class AdminDeletePetDailySayingActionHandler extends AbstractActionHandler<AdminDeletePetDailySayingDTO> {

    @Override
    protected void process(User user, AdminDeletePetDailySayingDTO body) {
        if (!user.isAdmin()) {
            user.send(ResponseBuilder.system("仅管理员可下架狗狗问候内容"));
            return;
        }
        try {
            AdminPetDailySayingContentListDTO result = PetDailySayingService.adminDelete(body);
            user.send(ResponseBuilder.build(null, result, MessageType.PET_DAILY_SAYING_CONTENT_LIST));
            user.send(ResponseBuilder.system("狗狗问候内容已下架"));
        } catch (IllegalArgumentException e) {
            user.send(ResponseBuilder.system(e.getMessage()));
        } catch (Exception e) {
            log.error("下架狗狗问候内容异常", e);
            user.send(ResponseBuilder.system("下架狗狗问候内容失败"));
        }
    }

}
