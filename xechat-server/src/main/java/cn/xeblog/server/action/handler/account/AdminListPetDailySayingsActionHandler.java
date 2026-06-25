package cn.xeblog.server.action.handler.account;

import cn.hutool.json.JSONUtil;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.pet.AdminListPetDailySayingsDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.action.handler.AbstractActionHandler;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.pet.PetDailySayingService;
import lombok.extern.slf4j.Slf4j;

/**
 * 管理员查看狗狗每日问候内容库。
 */
@Slf4j
@DoAction(Action.ADMIN_LIST_PET_DAILY_SAYINGS)
public class AdminListPetDailySayingsActionHandler extends AbstractActionHandler<Object> {

    @Override
    protected void process(User user, Object body) {
        if (!user.isAdmin()) {
            user.send(ResponseBuilder.system("仅管理员可查看狗狗问候内容库"));
            return;
        }
        try {
            user.send(ResponseBuilder.build(null,
                    PetDailySayingService.list(toBean(body, AdminListPetDailySayingsDTO.class)),
                    MessageType.PET_DAILY_SAYING_CONTENT_LIST));
        } catch (Exception e) {
            log.error("查询狗狗问候内容库异常", e);
            user.send(ResponseBuilder.system("查询狗狗问候内容库失败"));
        }
    }

    private <T> T toBean(Object content, Class<T> clazz) {
        if (content == null) {
            return null;
        }
        if (clazz.isInstance(content)) {
            return clazz.cast(content);
        }
        return JSONUtil.toBean(JSONUtil.toJsonStr(content), clazz);
    }

}
