package cn.xeblog.commons.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * ADMIN_LIST_INVITES 请求
 *
 * @author dz
 * @date 2026/5/22
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminListInvitesDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 是否包含已用满/已吊销/已过期,默认 false 只列可用
     */
    private boolean includeUsed;

}
