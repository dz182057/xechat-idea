package cn.xeblog.server.action.handler.account;

import cn.hutool.core.util.StrUtil;
import cn.xeblog.commons.entity.RegisterDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.Platform;
import cn.xeblog.server.account.AccountException;
import cn.xeblog.server.account.AccountService;
import cn.xeblog.server.account.InviteCodeService;
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
 * 注册账号(REGISTER)。
 *
 * <p>编排顺序:
 * 1. 用占位 accountId=0 调 InviteCodeService.consume 仅做有效性预检 → 不,直接执行 register 先拿 accountId
 *    再 consume — 但 register 必须先消费邀请码避免数据不一致;采用先 generateAccountId+consume → register。
 * </p>
 *
 * <p>实现策略:先校验邀请码 → 注册账号 → 消费邀请码(用真实 accountId)。
 * 邀请码 race 由 incrementUsed 的 SQL 原子性兜底,即便两人同时拿到同一 maxUses=1 的码,
 * 也只有先 incrementUsed 的成功;后续读到 usedCount >= maxUses 会被 register 内部判定为已用满。</p>
 *
 * @author dz
 * @date 2026/5/22
 */
@Slf4j
@DoAction(Action.REGISTER)
public class RegisterActionHandler implements ActionHandler<RegisterDTO> {

    @Override
    public void handle(ChannelHandlerContext ctx, RegisterDTO body) {
        if (ChannelAction.getUser(ctx) != null) {
            ctx.writeAndFlush(ResponseBuilder.system("请勿在已登录状态下重复注册"));
            return;
        }
        if (body == null) {
            ctx.writeAndFlush(ResponseBuilder.system("注册请求 body 为空"));
            return;
        }
        if (StrUtil.isBlank(body.getInviteCode())) {
            ctx.writeAndFlush(ResponseBuilder.system("邀请码不能为空"));
            return;
        }

        String ip = IpUtil.getIpByCtx(ctx);
        Platform platform = body.getPlatform() == null ? Platform.IDEA : body.getPlatform();

        try {
            // 1. 注册账号(暂定 USER 角色,首注册者升 ADMIN 由 consume 决定)
            //    E2EE 三件套若客户端传入则同事务落库,缺失时(老客户端)走"无 E2EE"账号
            Account account = AccountService.register(body.getAccount(),
                    body.getPassword(), body.getNickname(), Account.ROLE_USER, ip,
                    body.getE2eeSalt(), body.getIdentityPubKey(), body.getIdentityPrivKeyEnvelope());

            // 2. 消费邀请码,若返回 isInitialAdmin → 把账号 role 提升为 ADMIN
            boolean isInitialAdmin;
            try {
                isInitialAdmin = InviteCodeService.consume(body.getInviteCode(), account.getAccountId());
            } catch (AccountException ie) {
                // 邀请码消费失败,需要回滚刚注册的账号
                AccountService.softDelete(account.getAccountId());
                throw ie;
            }
            if (isInitialAdmin) {
                AccountService.updateRole(account.getAccountId(), Account.ROLE_ADMIN);
                account.setRole(Account.ROLE_ADMIN);
            }

            // 3. 创建 token
            SessionEntity sess = SessionService.createToken(account.getAccountId(),
                    platform.name(), body.getUuid(), ip);

            // 4. 上线
            AccountLoginHelper.onLoginSuccess(ctx, account, sess.getToken(),
                    sess.getExpiresAt(), body.getUuid(), platform);

            log.info("注册并上线 accountId={} nickname={} role={}",
                    account.getAccountId(), account.getNickname(), account.getRole());
        } catch (AccountException e) {
            ctx.writeAndFlush(ResponseBuilder.system(e.getMessage()));
        } catch (Exception e) {
            log.error("注册异常", e);
            ctx.writeAndFlush(ResponseBuilder.system("注册失败,请稍后重试"));
        }
    }

}
