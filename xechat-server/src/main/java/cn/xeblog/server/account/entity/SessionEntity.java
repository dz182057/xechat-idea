package cn.xeblog.server.account.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * sessions 表实体(命名为 SessionEntity 避免与 MyBatis SqlSession 冲突)
 *
 * @author dz
 * @date 2026/5/22
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionEntity {

    private String token;
    private long accountId;
    private String platform;
    private String clientUuid;
    private long createdAt;
    private long lastUsedAt;
    private long expiresAt;
    private boolean revoked;
    private String ip;

}
