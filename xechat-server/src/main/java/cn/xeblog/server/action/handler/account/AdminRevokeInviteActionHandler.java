package cn.xeblog.server.action.handler.account;

import cn.hutool.core.util.StrUtil;
import cn.xeblog.commons.entity.AdminRevokeInviteDTO;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.server.account.AccountException;
import cn.xeblog.server.account.InviteCodeService;
import cn.xeblog.server.action.handler.AbstractActionHandler;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import lombok.extern.slf4j.Slf4j;

/**
 * 管理员吊销邀请码(ADMIN_REVOKE_INVITE)
 *
 * @author dz
 * @date 2026/5/22
 */
@Slf4j
@DoAction(Action.ADMIN_REVOKE_INVITE)
public class AdminRevokeInviteActionHandler extends AbstractActionHandler<AdminRevokeInviteDTO> {

    @Override
    protected void process(User user, AdminRevokeInviteDTO body) {
        if (!user.isAdmin()) {
            user.send(ResponseBuilder.system("仅管理员可吊销邀请码"));
            return;
        }
        if (body == null || StrUtil.isBlank(body.getCode())) {
            user.send(ResponseBuilder.system("邀请码不能为空"));
            return;
        }
        try {
            InviteCodeService.revoke(body.getCode());
            user.send(ResponseBuilder.system("邀请码已吊销"));
            log.info("管理员 {} 吊销邀请码 {}", user.getAccountId(), body.getCode());
        } catch (AccountException e) {
            user.send(ResponseBuilder.system(e.getMessage()));
        } catch (Exception e) {
            log.error("吊销邀请码异常", e);
            user.send(ResponseBuilder.system("吊销失败"));
        }
    }

}
