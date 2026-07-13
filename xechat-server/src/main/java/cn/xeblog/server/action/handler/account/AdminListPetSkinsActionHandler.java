package cn.xeblog.server.action.handler.account;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.action.handler.AbstractActionHandler;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.pet.AdminPetSkinCatalogService;
import lombok.extern.slf4j.Slf4j;

/**
 * 管理员查询狗狗之家皮肤目录。
 */
@Slf4j
@DoAction(Action.ADMIN_LIST_PET_SKINS)
public class AdminListPetSkinsActionHandler extends AbstractActionHandler<Object> {

    @Override
    protected void process(User user, Object body) {
        if (!user.isAdmin()) {
            user.send(ResponseBuilder.system("仅管理员可查看狗狗之家皮肤列表"));
            return;
        }

        try {
            user.send(ResponseBuilder.build(null, AdminPetSkinCatalogService.list(), MessageType.ADMIN_PET_SKIN_LIST));
        } catch (Exception e) {
            log.error("管理员查询狗狗之家皮肤列表异常", e);
            user.send(ResponseBuilder.system("查询狗狗之家皮肤列表失败"));
        }
    }

}
