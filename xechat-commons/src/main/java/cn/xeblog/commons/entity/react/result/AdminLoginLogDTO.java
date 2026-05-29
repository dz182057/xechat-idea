package cn.xeblog.commons.entity.react.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 管理员登录记录视图。
 *
 * @author dz
 * @date 2026/5/29
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminLoginLogDTO implements Serializable {

    private Long id;
    private Long accountId;
    private String account;
    private String nickname;
    private String ip;
    private String region;
    private String platform;
    private boolean success;
    private String failReason;
    private long createdAt;

}
