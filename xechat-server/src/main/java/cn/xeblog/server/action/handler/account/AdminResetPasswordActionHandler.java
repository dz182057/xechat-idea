package cn.xeblog.server.action.handler.account;

import cn.hutool.core.util.StrUtil;
import cn.xeblog.commons.entity.AdminResetPasswordDTO;
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
 * 管理员重置用户密码(ADMIN_RESET_PASSWORD)。
 *
 * <p>只重置登录密码并吊销目标账号所有 token。管理员无法重新包裹用户 E2EE 私钥,
 * 所以旧端到端加密私聊可能需要用户在原设备恢复或重新建立密钥。</p>
 *
 * @author dz
 * @date 2026/5/28
 */
@Slf4j
@DoAction(Action.ADMIN_RESET_PASSWORD)
public class AdminResetPasswordActionHandler extends AbstractActionHandler<AdminResetPasswordDTO> {

    @Override
    protected void process(User user, AdminResetPasswordDTO body) {
        if (!user.isAdmin()) {
            user.send(ResponseBuilder.system("仅管理员可重置用户密码"));
            return;
        }
        if (body == null || StrUtil.isBlank(body.getAccount())) {
            user.send(ResponseBuilder.system("账号不能为空"));
            return;
        }
        if (StrUtil.isBlank(body.getNewPassword())) {
            user.send(ResponseBuilder.system("新密码不能为空"));
            return;
        }
        if (body.getAccount().equals(user.getAccount())) {
            user.send(ResponseBuilder.system("请在设置里修改自己的密码"));
            return;
        }

        try {
            Account target = AccountService.resetPasswordByAdmin(body.getAccount().trim(), body.getNewPassword());

            // 踢掉该账号所有在线 channel,避免旧 token 继续使用。
            List<User> online = UserCache.getByAccount(target.getAccountId());
            for (User u : online) {
                if (u.getChannel() != null) {
                    u.send(ResponseBuilder.system("密码已被管理员重置,请使用新密码重新登录"));
                    u.getChannel().close();
                }
            }
            user.send(ResponseBuilder.system("账号 " + target.getAccount() + " 的密码已重置"));
            log.info("管理员 {} 重置账号 {} 密码", user.getAccountId(), target.getAccountId());
        } catch (AccountException e) {
            user.send(ResponseBuilder.system(e.getMessage()));
        } catch (IllegalArgumentException e) {
            user.send(ResponseBuilder.system(e.getMessage()));
        } catch (Exception e) {
            log.error("管理员重置密码异常", e);
            user.send(ResponseBuilder.system("重置密码失败"));
        }
    }

}
