package cn.xeblog.server.action.handler.account;

import cn.hutool.json.JSONUtil;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.pet.AdminSavePetDailySayingDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.action.handler.AbstractActionHandler;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.pet.PetDailySayingService;
import lombok.extern.slf4j.Slf4j;

/**
 * 管理员保存狗狗每日问候内容。
 */
@Slf4j
@DoAction(Action.ADMIN_SAVE_PET_DAILY_SAYING)
public class AdminSavePetDailySayingActionHandler extends AbstractActionHandler<Object> {

    @Override
    protected void process(User user, Object body) {
        if (!user.isAdmin()) {
            user.send(ResponseBuilder.system("仅管理员可编辑狗狗问候内容库"));
            return;
        }
        try {
            AdminSavePetDailySayingDTO request = toBean(body, AdminSavePetDailySayingDTO.class);
            user.send(ResponseBuilder.build(null,
                    PetDailySayingService.save(request == null ? null : request.getContent()),
                    MessageType.PET_DAILY_SAYING_CONTENT_LIST));
            user.send(ResponseBuilder.system("狗狗问候内容已保存"));
        } catch (IllegalArgumentException e) {
            user.send(ResponseBuilder.system(e.getMessage()));
        } catch (Exception e) {
            log.error("保存狗狗问候内容异常", e);
            user.send(ResponseBuilder.system("保存狗狗问候内容失败"));
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
