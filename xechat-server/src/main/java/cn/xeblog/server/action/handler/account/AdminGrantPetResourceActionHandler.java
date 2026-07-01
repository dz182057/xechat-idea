package cn.xeblog.server.action.handler.account;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.pet.AdminGrantPetResourceDTO;
import cn.xeblog.commons.entity.pet.AdminPetResourceGrantResultDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.account.AccountException;
import cn.xeblog.server.action.handler.AbstractActionHandler;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.pet.AdminPetResourceGrantService;
import lombok.extern.slf4j.Slf4j;

/**
 * 管理员手动发放狗狗之家资源。
 */
@Slf4j
@DoAction(Action.ADMIN_GRANT_PET_RESOURCE)
public class AdminGrantPetResourceActionHandler extends AbstractActionHandler<AdminGrantPetResourceDTO> {

    @Override
    protected void process(User user, AdminGrantPetResourceDTO body) {
        if (!user.isAdmin()) {
            user.send(ResponseBuilder.system("仅管理员可发放狗狗之家资源"));
            return;
        }

        try {
            AdminPetResourceGrantResultDTO result =
                    AdminPetResourceGrantService.grant(user.getAccountId(), body);
            user.send(ResponseBuilder.build(null, result, MessageType.ADMIN_PET_RESOURCE_GRANTED));
            log.info("管理员 {} 向账号 {} 发放资源 {} {} 数量 {}",
                    user.getAccountId(),
                    result.getTargetAccountId(),
                    result.getResourceType(),
                    result.getItemId(),
                    result.getQuantity());
        } catch (AccountException e) {
            user.send(ResponseBuilder.system(e.getMessage()));
        } catch (Exception e) {
            log.error("管理员发放狗狗之家资源异常", e);
            user.send(ResponseBuilder.system("发放狗狗之家资源失败"));
        }
    }

}
