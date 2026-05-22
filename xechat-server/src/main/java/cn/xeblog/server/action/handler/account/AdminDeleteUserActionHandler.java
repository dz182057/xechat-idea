package cn.xeblog.server.action.handler.account;

import cn.xeblog.commons.entity.AdminDeleteUserDTO;
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
 * 管理员注销指定账号(ADMIN_DELETE_USER)。
 *
 * <p>软删 + 踢掉该账号所有在线连接。</p>
 *
 * @author dz
 * @date 2026/5/22
 */
@Slf4j
@DoAction(Action.ADMIN_DELETE_USER)
public class AdminDeleteUserActionHandler extends AbstractActionHandler<AdminDeleteUserDTO> {

    @Override
    protected void process(User user, AdminDeleteUserDTO body) {
        if (!user.isAdmin()) {
            user.send(ResponseBuilder.system("仅管理员可注销其他账号"));
            return;
        }
        if (body == null || body.getAccountId() == 0L) {
            user.send(ResponseBuilder.system("accountId 不能为空"));
            return;
        }
        if (body.getAccountId() == user.getAccountId()) {
            user.send(ResponseBuilder.system("请使用 DELETE_ACCOUNT 注销自己的账号"));
            return;
        }
        try {
            Account target = AccountService.findById(body.getAccountId());
            if (target == null) {
                user.send(ResponseBuilder.system("目标账号不存在"));
                return;
            }
            AccountService.softDelete(body.getAccountId());

            // 踢掉该账号所有在线 channel
            List<User> online = UserCache.getByAccount(body.getAccountId());
            for (User u : online) {
                if (u.getChannel() != null) {
                    u.send(ResponseBuilder.system("账号已被管理员注销"));
                    u.getChannel().close();
                }
            }
            user.send(ResponseBuilder.system("账号 " + target.getNickname() + " 已被注销"));
            log.info("管理员 {} 注销账号 {}", user.getAccountId(), body.getAccountId());
        } catch (AccountException e) {
            user.send(ResponseBuilder.system(e.getMessage()));
        } catch (Exception e) {
            log.error("管理员注销账号异常", e);
            user.send(ResponseBuilder.system("注销失败"));
        }
    }

}
