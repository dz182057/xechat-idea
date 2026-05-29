package cn.xeblog.server.action.handler.account;

import cn.hutool.core.util.StrUtil;
import cn.xeblog.commons.entity.LoginDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.Platform;
import cn.xeblog.server.account.AccountService;
import cn.xeblog.server.account.LoginLogService;
import cn.xeblog.server.account.SessionService;
import cn.xeblog.server.account.entity.Account;
import cn.xeblog.server.account.entity.SessionEntity;
import cn.xeblog.server.action.ChannelAction;
import cn.xeblog.server.action.handler.ActionHandler;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.util.IpUtil;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;

/**
 * token 登录(LOGIN_WITH_TOKEN),客户端自动登录入口。
 *
 * <p>校验 token → 滑动续期 → 查 Account → 上线。失败回 SYSTEM 提示客户端清 token 重新密码登录。</p>
 *
 * @author dz
 * @date 2026/5/22
 */
@Slf4j
@DoAction(Action.LOGIN_WITH_TOKEN)
public class LoginWithTokenActionHandler implements ActionHandler<LoginDTO> {

    @Override
    public void handle(ChannelHandlerContext ctx, LoginDTO body) {
        if (ChannelAction.getUser(ctx) != null) {
            ctx.writeAndFlush(ResponseBuilder.system("请勿重复登录"));
            return;
        }
        if (body == null || StrUtil.isBlank(body.getToken())) {
            LoginLogService.record(null, IpUtil.getIpByCtx(ctx), null, false, "token 缺失,请重新登录");
            ctx.writeAndFlush(ResponseBuilder.system("token 缺失,请重新登录"));
            return;
        }

        String ip = IpUtil.getIpByCtx(ctx);
        Platform platform = body.getPlatform() == null ? Platform.IDEA : body.getPlatform();
        try {
            SessionEntity sess = SessionService.validateAndTouch(body.getToken());
            if (sess == null) {
                LoginLogService.record(null, ip, platform, false, "登录已过期,请重新登录");
                ctx.writeAndFlush(ResponseBuilder.system("登录已过期,请重新登录"));
                return;
            }
            Account account = AccountService.findById(sess.getAccountId());
            if (account == null || !Account.STATUS_ACTIVE.equals(account.getStatus())) {
                // 账号被注销或冻结
                SessionService.revoke(body.getToken());
                LoginLogService.record(sess.getAccountId(), ip, platform, false, "账号不可用,请联系管理员");
                ctx.writeAndFlush(ResponseBuilder.system("账号不可用,请联系管理员"));
                return;
            }

            AccountLoginHelper.onLoginSuccess(ctx, account, sess.getToken(),
                    sess.getExpiresAt(), body.getUuid(), platform);
            log.info("账号 {} 上线(token 登录,滑动续期)", account.getAccount());
        } catch (Exception e) {
            log.error("token 登录异常", e);
            LoginLogService.record(null, ip, platform, false, "token 登录失败,请重新登录");
            ctx.writeAndFlush(ResponseBuilder.system("token 登录失败,请重新登录"));
        }
    }

}
