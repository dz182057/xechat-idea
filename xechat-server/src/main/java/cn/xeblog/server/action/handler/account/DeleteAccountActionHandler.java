package cn.xeblog.server.action.handler.account;

import cn.xeblog.commons.entity.DeleteAccountDTO;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.server.account.AccountException;
import cn.xeblog.server.account.AccountService;
import cn.xeblog.server.account.PasswordHasher;
import cn.xeblog.server.account.entity.Account;
import cn.xeblog.server.action.handler.AbstractActionHandler;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import lombok.extern.slf4j.Slf4j;

/**
 * 注销自己的账号(DELETE_ACCOUNT)。需要二次输入密码。
 *
 * @author dz
 * @date 2026/5/22
 */
@Slf4j
@DoAction(Action.DELETE_ACCOUNT)
public class DeleteAccountActionHandler extends AbstractActionHandler<DeleteAccountDTO> {

    @Override
    protected void process(User user, DeleteAccountDTO body) {
        if (body == null || body.getPassword() == null || body.getPassword().isEmpty()) {
            user.send(ResponseBuilder.system("请输入密码以确认注销"));
            return;
        }
        try {
            Account a = AccountService.findById(user.getAccountId());
            if (a == null) {
                user.send(ResponseBuilder.system("账号不存在"));
                return;
            }
            if (!PasswordHasher.verify(a.getPasswordHash(), body.getPassword())) {
                user.send(ResponseBuilder.system("密码错误"));
                return;
            }
            AccountService.softDelete(user.getAccountId());
            user.send(ResponseBuilder.system("账号已注销"));
            log.info("账号 {} 主动注销", user.getAccountId());
            if (user.getChannel() != null) {
                user.getChannel().close();
            }
        } catch (AccountException e) {
            user.send(ResponseBuilder.system(e.getMessage()));
        } catch (Exception e) {
            log.error("注销账号异常", e);
            user.send(ResponseBuilder.system("注销失败,请稍后重试"));
        }
    }

}
