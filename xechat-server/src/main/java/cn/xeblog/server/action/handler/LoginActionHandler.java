package cn.xeblog.server.action.handler;

import cn.hutool.core.util.StrUtil;
import cn.xeblog.commons.entity.LoginDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.Platform;
import cn.xeblog.server.account.AccountException;
import cn.xeblog.server.account.AccountService;
import cn.xeblog.server.account.SessionService;
import cn.xeblog.server.account.entity.Account;
import cn.xeblog.server.account.entity.SessionEntity;
import cn.xeblog.server.action.ChannelAction;
import cn.xeblog.server.action.handler.account.AccountLoginHelper;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.util.IpUtil;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;

/**
 * 账号+密码登录(LOGIN)。
 *
 * <p>取代旧的"输入昵称即用"的伪登录流程。校验通过后:
 * AccountService.login → SessionService.createToken → AccountLoginHelper.onLoginSuccess。</p>
 *
 * @author dz(基于 anlingyi 原 Handler 重写)
 * @date 2026/5/22
 */
@Slf4j
@DoAction(Action.LOGIN)
public class LoginActionHandler implements ActionHandler<LoginDTO> {

    @Override
    public void handle(ChannelHandlerContext ctx, LoginDTO body) {
        if (ChannelAction.getUser(ctx) != null) {
            ctx.writeAndFlush(ResponseBuilder.system("请勿重复登录"));
            return;
        }
        if (body == null) {
            ctx.writeAndFlush(ResponseBuilder.system("登录请求 body 为空"));
            return;
        }
        if (StrUtil.isBlank(body.getAccount()) || StrUtil.isBlank(body.getPassword())) {
            ctx.writeAndFlush(ResponseBuilder.system("账号或密码不能为空"));
            return;
        }
        if (StrUtil.isBlank(body.getUuid())) {
            ctx.writeAndFlush(ResponseBuilder.system("未获取到 UUID,请重新登录"));
            return;
        }

        String ip = IpUtil.getIpByCtx(ctx);
        Platform platform = body.getPlatform() == null ? Platform.IDEA : body.getPlatform();

        try {
            Account account = AccountService.login(body.getAccount(), body.getPassword(), ip);
            SessionEntity sess = SessionService.createToken(account.getAccountId(),
                    platform.name(), body.getUuid(), ip);
            AccountLoginHelper.onLoginSuccess(ctx, account, sess.getToken(),
                    sess.getExpiresAt(), body.getUuid(), platform);
            log.info("账号 {} 上线(密码登录)", account.getAccount());
        } catch (AccountException e) {
            ctx.writeAndFlush(ResponseBuilder.system(e.getMessage()));
        } catch (Exception e) {
            log.error("登录异常", e);
            ctx.writeAndFlush(ResponseBuilder.system("登录失败,请稍后重试"));
        }
    }

}
