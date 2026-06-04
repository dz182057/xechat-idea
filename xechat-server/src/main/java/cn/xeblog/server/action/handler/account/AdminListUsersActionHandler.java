package cn.xeblog.server.action.handler.account;

import cn.xeblog.commons.entity.AdminListUsersDTO;
import cn.xeblog.commons.entity.AdminUserDTO;
import cn.xeblog.commons.entity.AdminUserListDTO;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.account.AccountService;
import cn.xeblog.server.account.entity.Account;
import cn.xeblog.server.action.handler.AbstractActionHandler;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理员查询账号列表(ADMIN_LIST_USERS)。
 *
 * @author dz
 * @date 2026/6/4
 */
@Slf4j
@DoAction(Action.ADMIN_LIST_USERS)
public class AdminListUsersActionHandler extends AbstractActionHandler<AdminListUsersDTO> {

    @Override
    protected void process(User user, AdminListUsersDTO body) {
        if (!user.isAdmin()) {
            user.send(ResponseBuilder.system("仅管理员可查看账号列表"));
            return;
        }

        try {
            String account = body == null ? null : body.getAccount();
            String nickname = body == null ? null : body.getNickname();
            String status = body == null ? null : body.getStatus();
            List<Account> accounts = AccountService.listForAdmin(account, nickname, status);
            List<AdminUserDTO> users = new ArrayList<>(accounts.size());
            for (Account accountItem : accounts) {
                users.add(new AdminUserDTO(
                        accountItem.getAccountId(),
                        accountItem.getAccount(),
                        accountItem.getNickname(),
                        accountItem.getRole(),
                        accountItem.getStatus(),
                        accountItem.getCreatedAt(),
                        accountItem.getLastLoginAt(),
                        accountItem.getLastLoginIp()));
            }
            user.send(ResponseBuilder.build(null, new AdminUserListDTO(users), MessageType.ADMIN_USER_LIST));
        } catch (Exception e) {
            log.error("管理员查询账号列表异常", e);
            user.send(ResponseBuilder.system("查询账号列表失败"));
        }
    }

}
