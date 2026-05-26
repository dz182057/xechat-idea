package cn.xeblog.server.account.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * accounts 表实体(服务端内部使用,不暴露给客户端)
 *
 * @author dz
 * @date 2026/5/22
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_USER = "USER";

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_FROZEN = "FROZEN";
    public static final String STATUS_DELETED = "DELETED";

    private long accountId;
    private String account;
    private String nickname;
    private String passwordHash;
    private int avatarVersion;
    private String role;
    private int permit;
    private String status;
    private Long deletedAt;
    private long createdAt;
    private String createdIp;
    private Long lastLoginAt;
    private String lastLoginIp;
    /** E2EE: 客户端派生 masterKey 用的 salt(base64url 16B) */
    private String e2eeSalt;
    /** E2EE: X25519 身份公钥(base64url 32B) */
    private String identityPubKey;

}
