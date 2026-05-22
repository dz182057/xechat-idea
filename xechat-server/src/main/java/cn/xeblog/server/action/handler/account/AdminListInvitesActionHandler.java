package cn.xeblog.server.action.handler.account;

import cn.xeblog.commons.entity.AdminListInvitesDTO;
import cn.xeblog.commons.entity.InviteCodeDTO;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.account.InviteCodeService;
import cn.xeblog.server.action.handler.AbstractActionHandler;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 管理员邀请码列表(ADMIN_LIST_INVITES)
 *
 * @author dz
 * @date 2026/5/22
 */
@Slf4j
@DoAction(Action.ADMIN_LIST_INVITES)
public class AdminListInvitesActionHandler extends AbstractActionHandler<AdminListInvitesDTO> {

    @Override
    protected void process(User user, AdminListInvitesDTO body) {
        if (!user.isAdmin()) {
            user.send(ResponseBuilder.system("仅管理员可查看邀请码"));
            return;
        }
        boolean includeUsed = body != null && body.isIncludeUsed();
        try {
            List<InviteCodeDTO> list = InviteCodeService.list(includeUsed);
            user.send(ResponseBuilder.build(null, list, MessageType.INVITE_LIST));
        } catch (Exception e) {
            log.error("列邀请码异常", e);
            user.send(ResponseBuilder.system("查询邀请码失败"));
        }
    }

}
