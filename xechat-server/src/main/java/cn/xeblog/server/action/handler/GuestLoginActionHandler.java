package cn.xeblog.server.action.handler;

import cn.hutool.core.util.StrUtil;
import cn.xeblog.commons.entity.GuestLoginDTO;
import cn.xeblog.commons.entity.IpRegion;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.Permissions;
import cn.xeblog.commons.enums.Platform;
import cn.xeblog.commons.enums.UserStatus;
import cn.xeblog.server.account.AccountException;
import cn.xeblog.server.account.AccountService;
import cn.xeblog.server.action.ChannelAction;
import cn.xeblog.server.action.handler.account.AccountLoginHelper;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.cache.UserCache;
import cn.xeblog.server.util.IpUtil;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;

/**
 * 游客登录(GUEST_LOGIN)。
 *
 * <p>无账号体系,无 token,仅大厅聊天,禁止私聊。游客昵称仅"同时在线不可重名",
 * 不占用注册用户名池(双池命名空间)。下线即释放昵称。</p>
 *
 * <p>校验顺序:body 非空 → 昵称合法 → 游客池占用成功 → 构造 User → 走通用上线流程。</p>
 *
 * @author dz
 * @date 2026/5/25
 */
@Slf4j
@DoAction(Action.GUEST_LOGIN)
public class GuestLoginActionHandler implements ActionHandler<GuestLoginDTO> {

    @Override
    public void handle(ChannelHandlerContext ctx, GuestLoginDTO body) {
        if (ChannelAction.getUser(ctx) != null) {
            ctx.writeAndFlush(ResponseBuilder.system("请勿重复登录"));
            return;
        }
        if (body == null || StrUtil.isBlank(body.getNickname()) || StrUtil.isBlank(body.getUuid())) {
            ctx.writeAndFlush(ResponseBuilder.system("游客登录参数不完整(需 nickname + uuid)"));
            return;
        }

        String nickname = body.getNickname().trim();
        try {
            AccountService.validateNicknameFormat(nickname);
        } catch (AccountException e) {
            ctx.writeAndFlush(ResponseBuilder.system(e.getMessage()));
            return;
        }

        if (!UserCache.tryAcquireGuestNickname(nickname, body.getUuid())) {
            ctx.writeAndFlush(ResponseBuilder.system("该昵称当前已被使用,请换一个"));
            return;
        }

        try {
            String id = ChannelAction.getId(ctx);
            String ip = IpUtil.getIpByCtx(ctx);
            IpRegion region = IpUtil.getRegionByIp(ip);

            User user = new User();
            user.setId(id);
            user.setUuid(body.getUuid());
            user.setNickname(nickname);
            user.setGuest(true);
            user.setStatus(UserStatus.FISHING);
            user.setPlatform(body.getPlatform() == null ? Platform.IDEA : body.getPlatform());
            user.setIp(ip);
            user.setRegion(region);
            user.setChannel(ctx.channel());
            user.setPermit(Permissions.ALL.getValue());
            if (region != null) {
                user.setShortRegion(region.getProvince() == null ? region.getCountry() : region.getProvince());
            }

            AccountLoginHelper.notifyOnline(user, null, 0L);
            log.info("游客 {} 上线 platform={}", nickname, user.getPlatform());
        } catch (Exception e) {
            // 上线失败要回滚游客池占用,避免昵称被永远锁住
            UserCache.releaseGuestNickname(nickname, body.getUuid());
            log.error("游客登录异常 nickname={}", nickname, e);
            ctx.writeAndFlush(ResponseBuilder.system("登录失败,请稍后重试"));
        }
    }

}
