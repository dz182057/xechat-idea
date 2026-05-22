package cn.xeblog.commons.entity;

import cn.xeblog.commons.enums.Platform;
import cn.xeblog.commons.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @author anlingyi
 * @date 2022/4/3 4:29 下午
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginDTO implements Serializable {

    /**
     * 昵称(过渡兼容,新版用 account+password 登录)
     *
     * @deprecated 使用 {@link #account} 登录;此字段仅供旧客户端发送,服务端忽略
     */
    @Deprecated
    private String username;

    /**
     * 登录账号([a-zA-Z0-9_]{4,20})
     */
    private String account;

    /**
     * 明文密码(仅 LOGIN 时携带)
     */
    private String password;

    /**
     * 状态
     */
    private UserStatus status;

    /**
     * 是否是重连
     */
    private boolean reconnected;

    /**
     * 插件版本
     */
    private String pluginVersion;

    /**
     * 令牌(LOGIN_WITH_TOKEN 时携带)
     */
    private String token;

    /**
     * 全局唯一ID(客户端 uuid)
     */
    private String uuid;

    /**
     * 来源平台
     */
    private Platform platform;

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public void setStatus(String status) {
        try {
            this.status = UserStatus.valueOf(status);
        } catch (Exception e) {
            this.status = UserStatus.FISHING;
        }
    }

    public void setPlatform(Platform platform) {
        this.platform = platform;
    }

    public void setPlatform(String platform) {
        try {
            this.platform = Platform.valueOf(platform);
        } catch (Exception e) {
            this.platform = Platform.IDEA;
        }
    }

}
