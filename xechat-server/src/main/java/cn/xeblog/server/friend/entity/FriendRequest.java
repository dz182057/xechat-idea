package cn.xeblog.server.friend.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * friend_requests 表实体。
 *
 * @author dz
 * @date 2026/6/3
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendRequest {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_REJECTED = "REJECTED";

    private long id;
    private long fromAccountId;
    private long toAccountId;
    private String status;
    private long createdAt;
    private Long handledAt;

}
