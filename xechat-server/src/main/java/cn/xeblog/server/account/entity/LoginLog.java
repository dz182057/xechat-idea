package cn.xeblog.server.account.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * login_logs 表实体。
 *
 * @author dz
 * @date 2026/5/29
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginLog {

    private Long id;
    private Long accountId;
    private String ip;
    private String region;
    private String platform;
    private boolean success;
    private String failReason;
    private long createdAt;

}
