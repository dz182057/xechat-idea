package cn.xeblog.commons.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * ADMIN_USER_LIST 响应
 *
 * @author dz
 * @date 2026/6/4
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserListDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<AdminUserDTO> users;

}
