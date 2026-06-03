package cn.xeblog.server.friend.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * friends 表实体。
 *
 * @author dz
 * @date 2026/6/3
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendRelation {

    private long ownerAccountId;
    private long friendAccountId;
    private long createdAt;

}
