package cn.xeblog.commons.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * ADMIN_REVOKE_INVITE 请求
 *
 * @author dz
 * @date 2026/5/22
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminRevokeInviteDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String code;

}
