package cn.xeblog.server.friend;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.xeblog.commons.entity.FriendDTO;
import cn.xeblog.commons.entity.FriendListMsgDTO;
import cn.xeblog.commons.entity.FriendRequestDTO;
import cn.xeblog.commons.entity.FriendRequestListMsgDTO;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.commons.enums.Platform;
import cn.xeblog.server.account.AccountException;
import cn.xeblog.server.account.DbInitializer;
import cn.xeblog.server.account.entity.Account;
import cn.xeblog.server.account.mapper.AccountMapper;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.cache.UserCache;
import cn.xeblog.server.friend.entity.FriendRequest;
import cn.xeblog.server.friend.mapper.FriendMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSession;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 好友申请和好友列表服务。
 *
 * @author dz
 * @date 2026/6/3
 */
@Slf4j
public final class FriendService {

    private FriendService() {
    }

    public static void addRequest(User me, String target) {
        if (StrUtil.isBlank(target)) {
            throw new AccountException("请输入对方账号或昵称");
        }

        FriendRequestDTO dto;
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            AccountMapper accountMapper = session.getMapper(AccountMapper.class);
            FriendMapper friendMapper = session.getMapper(FriendMapper.class);
            Account targetAccount = accountMapper.findByAccount(target);
            if (targetAccount == null) {
                targetAccount = accountMapper.findByNickname(target);
            }
            if (targetAccount == null || !Account.STATUS_ACTIVE.equals(targetAccount.getStatus())) {
                throw new AccountException("目标账号不存在");
            }
            if (targetAccount.getAccountId() == me.getAccountId()) {
                throw new AccountException("不能添加自己为好友");
            }
            if (friendMapper.existsFriend(me.getAccountId(), targetAccount.getAccountId()) > 0) {
                throw new AccountException("已经是好友");
            }
            if (friendMapper.existsPendingRequest(me.getAccountId(), targetAccount.getAccountId()) > 0) {
                throw new AccountException("好友申请已发送");
            }

            long now = System.currentTimeMillis();
            long requestId = IdUtil.getSnowflakeNextId();
            friendMapper.insertRequest(requestId, me.getAccountId(), targetAccount.getAccountId(), now);
            session.commit();

            dto = new FriendRequestDTO(requestId, me.getAccountId(), me.getAccount(),
                    me.getNickname(), me.getAvatarVersion(), now);
            log.info("好友申请已创建 from={} to={} requestId={}",
                    me.getAccountId(), targetAccount.getAccountId(), requestId);
            pushToAccount(targetAccount.getAccountId(), dto, MessageType.FRIEND_REQUEST);
        }
    }

    public static void respond(User me, long requestId, boolean accepted) {
        FriendRequest request;
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            FriendMapper mapper = session.getMapper(FriendMapper.class);
            request = mapper.findPendingRequest(requestId, me.getAccountId());
            if (request == null) {
                throw new AccountException("好友申请不存在或已处理");
            }

            long now = System.currentTimeMillis();
            String status = accepted ? FriendRequest.STATUS_ACCEPTED : FriendRequest.STATUS_REJECTED;
            mapper.markRequestHandled(requestId, status, now);
            if (accepted) {
                mapper.insertFriend(request.getFromAccountId(), request.getToAccountId(), now);
                mapper.insertFriend(request.getToAccountId(), request.getFromAccountId(), now);
            }
            session.commit();
        }

        if (accepted) {
            pushFriendListRefreshForAccount(request.getFromAccountId());
            pushFriendListRefreshForAccount(request.getToAccountId());
        }
    }

    public static FriendListMsgDTO listFriends(long ownerAccountId) {
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            FriendMapper mapper = session.getMapper(FriendMapper.class);
            List<FriendDTO> friends = new ArrayList<>();
            for (Account account : mapper.listFriends(ownerAccountId)) {
                friends.add(toFriendDTO(account));
            }
            return new FriendListMsgDTO(friends);
        }
    }

    public static FriendRequestListMsgDTO listPendingRequests(long toAccountId) {
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            FriendMapper friendMapper = session.getMapper(FriendMapper.class);
            AccountMapper accountMapper = session.getMapper(AccountMapper.class);
            List<FriendRequestDTO> requests = new ArrayList<>();
            for (FriendRequest request : friendMapper.listPendingRequests(toAccountId)) {
                Account from = accountMapper.findById(request.getFromAccountId());
                if (from == null) {
                    continue;
                }
                requests.add(new FriendRequestDTO(request.getId(), from.getAccountId(),
                        from.getAccount(), from.getNickname(), from.getAvatarVersion(),
                        request.getCreatedAt()));
            }
            return new FriendRequestListMsgDTO(requests);
        }
    }

    public static void pushFriendListRefreshForAccount(long accountId) {
        Set<Long> targets = new LinkedHashSet<>();
        targets.add(accountId);
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            for (Account friend : session.getMapper(FriendMapper.class).listFriends(accountId)) {
                targets.add(friend.getAccountId());
            }
        }
        for (Long target : targets) {
            pushToAccount(target, null, MessageType.FRIEND_UPDATED);
        }
    }

    private static FriendDTO toFriendDTO(Account account) {
        Set<Platform> platforms = collectVisiblePlatforms(account.getAccountId());
        boolean online = !platforms.isEmpty();
        return new FriendDTO(account.getAccountId(), account.getAccount(), account.getNickname(),
                account.getAvatarVersion(), online, online ? platforms : null);
    }

    static Set<Platform> collectVisiblePlatforms(long accountId) {
        Set<Platform> platforms = EnumSet.noneOf(Platform.class);
        for (User conn : UserCache.getByAccount(accountId)) {
            if (conn.isStealth() || conn.getPlatform() == null) {
                continue;
            }
            platforms.add(conn.getPlatform());
        }
        return platforms;
    }

    private static void pushToAccount(long accountId, Object body, MessageType type) {
        for (User user : UserCache.getByAccount(accountId)) {
            user.send(ResponseBuilder.build(null, body, type));
        }
    }

}
