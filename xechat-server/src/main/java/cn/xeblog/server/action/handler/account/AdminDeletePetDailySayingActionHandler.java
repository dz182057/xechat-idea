package cn.xeblog.server.action.handler.account;

import cn.hutool.json.JSONUtil;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.pet.AdminDeletePetDailySayingDTO;
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
public class AdminDeletePetDailySayingActionHandler extends AbstractActionHandler<Object> {

    @Override
    protected void process(User user, Object body) {
        if (!user.isAdmin()) {
            user.send(ResponseBuilder.system("仅管理员可下架狗狗问候内容"));
            return;
        }
        try {
            AdminDeletePetDailySayingDTO request = toBean(body, AdminDeletePetDailySayingDTO.class);
            user.send(ResponseBuilder.build(null,
                    PetDailySayingService.delete(request == null ? null : request.getContentId()),
                    MessageType.PET_DAILY_SAYING_CONTENT_LIST));
            user.send(ResponseBuilder.system("狗狗问候内容已下架"));
        } catch (IllegalArgumentException e) {
            user.send(ResponseBuilder.system(e.getMessage()));
        } catch (Exception e) {
            log.error("下架狗狗问候内容异常", e);
            user.send(ResponseBuilder.system("下架狗狗问候内容失败"));
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
