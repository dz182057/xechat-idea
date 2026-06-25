package cn.xeblog.server.behavior;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 玩家行为流水记录。
 *
 * @author dz
 * @date 2026/6/24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerBehaviorLog {

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
