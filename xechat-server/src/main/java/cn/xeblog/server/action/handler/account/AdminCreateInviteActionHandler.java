package cn.xeblog.server.action.handler.account;

import cn.xeblog.commons.entity.AdminCreateInviteDTO;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.account.AccountException;
import cn.xeblog.server.account.InviteCodeService;
import cn.xeblog.server.action.handler.AbstractActionHandler;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;

/**
 * 管理员创建邀请码(ADMIN_CREATE_INVITE)。
 *
 * @author dz
 * @date 2026/5/22
 */
@Slf4j
@DoAction(Action.ADMIN_CREATE_INVITE)
public class AdminCreateInviteActionHandler extends AbstractActionHandler<AdminCreateInviteDTO> {

    @Override
    protected void process(User user, AdminCreateInviteDTO body) {
        if (!user.isAdmin()) {
            user.send(ResponseBuilder.system("仅管理员可创建邀请码"));
            return;
        }
        int maxUses = body == null || body.getMaxUses() == null ? 1 : body.getMaxUses();
        Integer expiresInDays = body == null ? Integer.valueOf(7) : body.getExpiresInDays();
        String note = body == null ? null : body.getNote();

        try {
            String code = InviteCodeService.generate(user.getAccountId(), maxUses, expiresInDays, note);
            // 回包: 用 INVITE_CREATED MessageType 包一个单元素 list,客户端按 list 处理
            user.send(ResponseBuilder.build(null, Collections.singletonList(code),
                    MessageType.INVITE_CREATED));
            log.info("管理员 {} 创建邀请码 {} maxUses={} expiresInDays={}",
                    user.getAccountId(), code, maxUses, expiresInDays);
        } catch (AccountException e) {
            user.send(ResponseBuilder.system(e.getMessage()));
        } catch (Exception e) {
            log.error("创建邀请码异常", e);
            user.send(ResponseBuilder.system("创建邀请码失败"));
        }
    }

}
