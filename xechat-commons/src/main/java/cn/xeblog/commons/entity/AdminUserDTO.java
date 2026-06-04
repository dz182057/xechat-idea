package cn.xeblog.commons.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 管理员账号列表项
 *
 * @author dz
 * @date 2026/6/4
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long accountId;

    private String account;

    private String nickname;

    private String role;

    private String status;

    private long createdAt;

    private Long lastLoginAt;

    private String lastLoginIp;

}
