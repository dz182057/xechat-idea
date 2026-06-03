package cn.xeblog.server.friend.mapper;

import cn.xeblog.server.account.entity.Account;
import cn.xeblog.server.friend.entity.FriendRequest;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 好友关系 Mapper。
 *
 * @author dz
 * @date 2026/6/3
 */
public interface FriendMapper {

    int insertFriend(@Param("ownerAccountId") long ownerAccountId,
                     @Param("friendAccountId") long friendAccountId,
                     @Param("createdAt") long createdAt);

    int existsFriend(@Param("ownerAccountId") long ownerAccountId,
                     @Param("friendAccountId") long friendAccountId);

    List<Account> listFriends(@Param("ownerAccountId") long ownerAccountId);

    int insertRequest(@Param("id") long id,
                      @Param("fromAccountId") long fromAccountId,
                      @Param("toAccountId") long toAccountId,
                      @Param("createdAt") long createdAt);

    int existsPendingRequest(@Param("fromAccountId") long fromAccountId,
                             @Param("toAccountId") long toAccountId);

    FriendRequest findPendingRequest(@Param("requestId") long requestId,
                                     @Param("toAccountId") long toAccountId);

    List<FriendRequest> listPendingRequests(@Param("toAccountId") long toAccountId);

    int markRequestHandled(@Param("requestId") long requestId,
                           @Param("status") String status,
                           @Param("handledAt") long handledAt);

}
