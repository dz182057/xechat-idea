package cn.xeblog.server.action.handler.account;

import cn.xeblog.commons.entity.IpRegion;
import cn.xeblog.commons.entity.LoginResultDTO;
import cn.xeblog.commons.entity.Response;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.UserStateMsgDTO;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.commons.enums.Permissions;
import cn.xeblog.commons.enums.Platform;
import cn.xeblog.commons.enums.UserStatus;
import cn.xeblog.server.account.entity.Account;
import cn.xeblog.server.account.LoginLogService;
import cn.xeblog.server.action.ChannelAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.cache.UserCache;
import cn.xeblog.server.config.GlobalConfig;
import cn.xeblog.server.e2ee.E2EEKeyService;
import cn.xeblog.server.friend.FriendService;
import cn.xeblog.server.util.IpUtil;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

/**
 * 登录/注册/token 登录的共享"上线"流程。
 *
 * <p>三个入口(REGISTER / LOGIN / LOGIN_WITH_TOKEN)在身份校验通过后调用,
 * 共享逻辑: 建 User → 加 UserCache → 加 ChannelGroup → 发 LoginResult →
 * 发在线列表给自己 → 广播 USER_STATE.ONLINE。</p>
 *
 * @author dz
 * @date 2026/5/22
 */
public final class AccountLoginHelper {

    private static final String ACCOUNT_REPLACED_MESSAGE = "该账号已在当前端其他位置登录,当前连接已下线";

    private AccountLoginHelper() {
    }

    /**
     * 上线流程。
     *
     * @param ctx        channel
     * @param account    DB 中的账号实体
     * @param token      刚创建/续期的 token
     * @param expiresAt  token 过期 epoch ms
     * @param uuid       客户端 uuid
     * @param platform   客户端平台(为 null 时默认 IDEA)
     */
    public static boolean onLoginSuccess(ChannelHandlerContext ctx, Account account,
                                         String token, long expiresAt,
                                         String uuid, Platform platform) {
        String id = ChannelAction.getId(ctx);
        String ip = IpUtil.getIpByCtx(ctx);
        IpRegion region = IpUtil.getRegionByIp(ip);

        User user = new User(id, account.getAccountId(), account.getAccount(),
                account.getNickname(), account.getAvatarVersion(),
                UserStatus.FISHING, ip, region, ctx.channel());
        user.setUuid(uuid);
        user.setRole(Account.ROLE_ADMIN.equals(account.getRole())
                ? User.Role.ADMIN : User.Role.USER);
        user.setPermit(GlobalConfig.getUserPermit(user));
        // 兼容: 旧用户权限不开放时,初始放开全部
        if (user.getPermit() == 0) {
            user.setPermit(Permissions.ALL.getValue());
        }
        user.setPlatform(platform == null ? Platform.IDEA : platform);
        user.setToken(token);
        user.setStealth(initialSessionStealth());

        // 注册用户:回读 envelope 一并下发,客户端拿 e2eeSalt+envelope 派生 masterKey 并解出私钥
        String identityEnvelope = E2EEKeyService.findIdentityEnvelope(account.getAccountId());
        return notifyOnline(user, token, expiresAt,
                account.getE2eeSalt(), account.getIdentityPubKey(), identityEnvelope);
    }

    /**
     * 游客版"上线":无 token / 无 E2EE 字段。
     */
    public static boolean notifyOnline(User user, String token, long expiresAt) {
        return notifyOnline(user, token, expiresAt, null, null, null);
    }

    /**
     * 通用"上线"流程:加缓存/ChannelGroup → 发 LoginResult → 发在线列表给自己 → 广播 ONLINE。
     *
     * <p>账号登录、游客登录、token 登录、注册成功均可复用。E2EE 三字段:游客或老账号传 null,
     * 注册用户从 accounts + key_envelopes 取出来。</p>
     *
     * @param token                   无 token(游客)时传 null
     * @param expiresAt               无 token 时传 0
     * @param e2eeSalt                注册用户的 e2eeSalt,游客/老账号 null
     * @param identityPubKey          注册用户的 X25519 公钥,游客/老账号 null
     * @param identityPrivKeyEnvelope 注册用户的身份私钥信封,游客/老账号 null
     */
    public static boolean notifyOnline(User user, String token, long expiresAt,
                                       String e2eeSalt, String identityPubKey,
                                       String identityPrivKeyEnvelope) {
        boolean samePlatformAlreadyOnline = user.getAccountId() > 0
                && UserCache.hasOnlineByAccountAndPlatform(user.getAccountId(), user.getPlatform());
        List<User> replacedUsers = UserCache.addReplacingAccountClient(user);
        ChannelAction.add(user.getChannel());
        kickReplacedUsers(replacedUsers, user.getId());

        Long accountId = user.getAccountId() > 0 ? user.getAccountId() : null;
        LoginLogService.record(accountId, user.getIp(), user.getPlatform(), true, null);

        // 1) 把 LoginResultDTO 发给自己
        LoginResultDTO dto = new LoginResultDTO(token, expiresAt, copyForLoginResult(user));
        dto.setE2eeSalt(e2eeSalt);
        dto.setIdentityPubKey(identityPubKey);
        dto.setIdentityPrivKeyEnvelope(identityPrivKeyEnvelope);
        Response loginResult = ResponseBuilder.build(null, dto, MessageType.LOGIN_RESULT);
        user.send(loginResult);

        // 2) 给自己发当前在线列表
        ChannelAction.sendOnlineUsers(user);

        // 3) 广播上线状态:新增端通知;同账号同平台重连接管旧连接只刷新在线列表
        if (shouldNotifyOnlineState(user, samePlatformAlreadyOnline)) {
            ChannelAction.sendUserState(user, UserStateMsgDTO.State.ONLINE);
        } else {
            ChannelAction.sendOnlineUsers();
        }
        if (!user.isGuest()) {
            FriendService.pushFriendListRefreshForAccount(user.getAccountId());
        }
        return true;
    }

    static boolean shouldNotifyOnlineState(User user, boolean samePlatformAlreadyOnline) {
        return user == null || user.getAccountId() <= 0L || !samePlatformAlreadyOnline;
    }

    private static void kickReplacedUsers(List<User> replacedUsers, String currentUserId) {
        if (replacedUsers == null || replacedUsers.isEmpty()) {
            return;
        }
        for (User replacedUser : replacedUsers) {
            if (replacedUser == null || currentUserId.equals(replacedUser.getId())) {
                continue;
            }
            replacedUser.send(ResponseBuilder.system(ACCOUNT_REPLACED_MESSAGE));
            ChannelAction.cleanUser(replacedUser.getId());
            if (replacedUser.getChannel() != null) {
                replacedUser.getChannel().close();
            }
        }
    }

    /**
     * 隐身只针对当前在线会话，每次重新登录默认恢复可见。
     */
    static boolean initialSessionStealth() {
        return false;
    }

    /**
     * LoginResultDTO.user 不应携带 channel/ip/region(transient 已排除,但再清一遍保险)。
     */
    private static User copyForLoginResult(User src) {
        User dst = new User();
        dst.setUuid(src.getUuid());
        dst.setId(src.getId());
        dst.setAccountId(src.getAccountId());
        dst.setAccount(src.getAccount());
        dst.setNickname(src.getNickname());
        dst.setAvatarVersion(src.getAvatarVersion());
        dst.setGuest(src.isGuest());
        dst.setStatus(src.getStatus());
        dst.setShortRegion(src.getShortRegion());
        dst.setRole(src.getRole());
        dst.setPermit(src.getPermit());
        dst.setPlatform(src.getPlatform());
        dst.setStealth(src.isStealth());
        return dst;
    }

}
