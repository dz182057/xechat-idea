package cn.xeblog.commons.entity;

import cn.xeblog.commons.enums.Platform;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 游客登录请求体(GUEST_LOGIN)。
 *
 * <p>无账号体系,仅大厅聊天,禁止私聊;游客昵称仅同时在线不可重名(下线即释放),
 * 不占用注册用户名池。</p>
 *
 * @author dz
 * @date 2026/5/25
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GuestLoginDTO implements Serializable {

    /**
     * 游客昵称(字符长度限制同注册用户昵称)
     */
    private String nickname;

    /**
     * 全局唯一 ID(客户端 uuid)
     */
    private String uuid;

    /**
     * 来源平台
     */
    private Platform platform;

    /**
     * 客户端版本(可选,后续断旧客户端用)
     */
    private String pluginVersion;

}
