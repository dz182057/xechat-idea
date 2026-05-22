package cn.xeblog.commons.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 邀请码视图(ADMIN_LIST_INVITES 响应元素)。
 *
 * @author dz
 * @date 2026/5/22
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InviteCodeDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 邀请码
     */
    private String code;

    /**
     * 生成者账号 ID(系统生成时为 null,如 setup-token)
     */
    private Long createdBy;

    /**
     * 生成者昵称(便于 UI 展示)
     */
    private String createdByNickname;

    /**
     * 创建时间(epoch ms)
     */
    private long createdAt;

    /**
     * 过期时间(epoch ms,null 表示永久)
     */
    private Long expiresAt;

    /**
     * 最大使用次数(0=无限)
     */
    private int maxUses;

    /**
     * 已使用次数
     */
    private int usedCount;

    /**
     * 是否已吊销
     */
    private boolean revoked;

    /**
     * 备注
     */
    private String note;

}
