package cn.xeblog.commons.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 撤回消息请求与广播。
 *
 * @author dz
 * @date 2026/5/27
 */
@Data
@NoArgsConstructor
public class RecallMessageDTO implements Serializable {

    private Long messageId;

    private ConversationType conversationType;

    private Long senderAccountId;

    private Long recipientAccountId;

    private Long recalledAt;

    public enum ConversationType {
        PUBLIC,
        PRIVATE
    }

}
