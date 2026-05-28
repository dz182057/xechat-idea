package cn.xeblog.commons.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * ADMIN_RESET_PASSWORD 请求(管理员重置指定账号密码)
 *
 * @author dz
 * @date 2026/5/28
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminResetPasswordDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 登录账号,不是昵称。
     */
    private String account;

    /**
     * 新密码(至少 8 位,含字母和数字)。
     */
    private String newPassword;

}
