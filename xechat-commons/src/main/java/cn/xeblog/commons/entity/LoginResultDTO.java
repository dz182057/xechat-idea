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

}
