package cn.xeblog.commons.entity.react.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 管理员用户操作记录视图。
 *
 * @author dz
 * @date 2026/7/3
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminBehaviorLogDTO implements Serializable {

    private Long id;
    private Long accountId;
    private String account;
    private String nickname;
    private boolean guest;
    private String platform;
    private String clientUuid;
    private String ip;
    private String region;
    private String action;
    private String subAction;
    private String protocol;
    private String resultStatus;
    private String errorMessage;
    private String requestBodyJson;
    private String relatedType;
    private String relatedId;
    private long createdAt;

}
