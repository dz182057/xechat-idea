package cn.xeblog.commons.entity;

import cn.xeblog.commons.enums.Platform;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 注册请求。
 *
 * <p>服务端校验邀请码 → 校验账号/昵称合法性 → Argon2 hash 密码 → 入库 →
 * 创建 session 并返回 {@link LoginResultDTO}。</p>
 *
 * @author dz
 * @date 2026/5/22
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 邀请码
     */
    private String inviteCode;

    /**
     * 登录账号([a-zA-Z0-9_]{4,20})
     */
    private String account;

    /**
     * 明文密码(至少 8 位,含字母+数字)
     */
    private String password;

    /**
     * 昵称(≤12 字符,唯一)
     */
    private String nickname;

    /**
     * 客户端 uuid
     */
    private String uuid;

    /**
     * 来源平台
     */
    private Platform platform;

    /**
     * 客户端版本
     */
    private String pluginVersion;

    public void setPlatform(Platform platform) {
        this.platform = platform;
    }

    public void setPlatform(String platform) {
        try {
            this.platform = Platform.valueOf(platform);
        } catch (Exception e) {
            this.platform = Platform.DESKTOP;
        }
    }

}
