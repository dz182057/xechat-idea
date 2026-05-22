package cn.xeblog.server.account.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * invite_codes 表实体
 *
 * @author dz
 * @date 2026/5/22
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InviteCode {

    private String code;
    private Long createdBy;
    private long createdAt;
    private Long expiresAt;
    private int maxUses;
    private int usedCount;
    private Long usedBy;
    private Long usedAt;
    private boolean revoked;
    private String note;

}
