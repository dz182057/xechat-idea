package cn.xeblog.commons.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 登录/注册成功后,服务端返回的结果。
 *
 * <p>客户端持久化 {@link #token},下次启动用 LOGIN_WITH_TOKEN 自动登录。</p>
 *
 * @author dz
 * @date 2026/5/22
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 会话 token(base64url,43 字符)
     */
    private String token;

    /**
     * token 过期时间(epoch ms)
     */
    private long expiresAt;

    /**
     * 登录的用户信息(带 accountId/account/nickname/avatarVersion)
     */
    private User user;

    /**
     * E2EE: 客户端派生 masterKey 用的 salt(base64url,16 字节)。
     * 注册时由客户端生成上传;后续登录从 accounts 表回读。游客登录此字段为 null。
     */
    private String e2eeSalt;

    /**
     * E2EE: 主密钥包裹后的身份私钥(base64url 的 iv||ciphertext)。
     * 客户端用 masterKey 解出 IdentityPrivKey,缓存到内存。游客登录此字段为 null。
     */
    private String identityPrivKeyEnvelope;

    /**
     * E2EE: 当前账号的身份公钥(base64url,32 字节 X25519)。
     * 客户端启动时用作"我的公钥"展示在安全码里,核对历史 fingerprint。游客登录此字段为 null。
     */
    private String identityPubKey;

    /**
     * 便捷构造器(向后兼容):仅 token + expiresAt + user,E2EE 字段保持 null。
     * 游客登录与不需要 E2EE 字段的旧路径用此构造器。
     */
    public LoginResultDTO(String token, long expiresAt, User user) {
        this.token = token;
        this.expiresAt = expiresAt;
        this.user = user;
    }

}
