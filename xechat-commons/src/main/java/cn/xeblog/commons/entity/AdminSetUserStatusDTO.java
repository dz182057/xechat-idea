package cn.xeblog.commons.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * ADMIN_SET_USER_STATUS 请求(管理员禁用/启用账号)
 *
 * @author dz
 * @date 2026/6/4
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminSetUserStatusDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private long accountId;

    private String status;

}
