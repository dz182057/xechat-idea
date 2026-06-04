package cn.xeblog.server.action.handler.account;

import cn.xeblog.commons.entity.AdminSetUserStatusDTO;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.server.account.AccountException;
import cn.xeblog.server.account.AccountService;
import cn.xeblog.server.account.entity.Account;
import cn.xeblog.server.action.handler.AbstractActionHandler;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.cache.UserCache;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 管理员禁用/启用账号(ADMIN_SET_USER_STATUS)。
 *
 * @author dz
 * @date 2026/6/4
 */
@Slf4j
@DoAction(Action.ADMIN_SET_USER_STATUS)
public class AdminSetUserStatusActionHandler extends AbstractActionHandler<AdminSetUserStatusDTO> {

    @Override
    protected void process(User user, AdminSetUserStatusDTO body) {
        if (!user.isAdmin()) {
            user.send(ResponseBuilder.system("仅管理员可设置账号状态"));
            return;
        }
        if (body == null || body.getAccountId() == 0L) {
            user.send(ResponseBuilder.system("accountId 不能为空"));
            return;
        }
        if (body.getAccountId() == user.getAccountId()) {
            user.send(ResponseBuilder.system("不能禁用或启用自己"));
            return;
        }

        try {
            Account target = AccountService.setStatusByAdmin(body.getAccountId(), body.getStatus());
            if (Account.STATUS_FROZEN.equals(body.getStatus())) {
                kickOnlineUsers(body.getAccountId(), "账号已被管理员禁用");
                user.send(ResponseBuilder.system("账号 " + target.getNickname() + " 已禁用"));
            } else {
                user.send(ResponseBuilder.system("账号 " + target.getNickname() + " 已启用"));
            }
            log.info("管理员 {} 设置账号 {} 状态为 {}", user.getAccountId(),
                    body.getAccountId(), body.getStatus());
        } catch (AccountException e) {
            user.send(ResponseBuilder.system(e.getMessage()));
        } catch (Exception e) {
            log.error("管理员设置账号状态异常", e);
            user.send(ResponseBuilder.system("设置账号状态失败"));
        }
    }

    private void kickOnlineUsers(long accountId, String message) {
        List<User> online = UserCache.getByAccount(accountId);
        for (User onlineUser : online) {
            if (onlineUser.getChannel() != null) {
                onlineUser.send(ResponseBuilder.system(message));
                onlineUser.getChannel().close();
            }
        }
    }

}
