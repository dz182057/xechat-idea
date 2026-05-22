package cn.xeblog.commons.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * ADMIN_DELETE_USER 请求(管理员注销指定账号)
 *
 * @author dz
 * @date 2026/5/22
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminDeleteUserDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private long accountId;

}
