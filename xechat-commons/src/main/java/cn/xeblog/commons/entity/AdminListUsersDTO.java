package cn.xeblog.commons.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * ADMIN_LIST_USERS 请求(管理员查询账号列表)
 *
 * @author dz
 * @date 2026/6/4
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminListUsersDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String account;

    private String nickname;

    private String status;

}
