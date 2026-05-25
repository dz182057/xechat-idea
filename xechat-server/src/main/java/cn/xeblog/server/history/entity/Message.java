package cn.xeblog.server.history.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * messages_public 表实体(公共频道聊天记录)。
 *
 * <p>分表方案:公共明文落本表,私聊密文将来落 messages_private(见 e2ee-and-history.md)。
 * sender_account_id 和 sender_guest_uuid 二选一:注册用户填前者,游客填后者。
 * sender_nickname 冗余存当时昵称,避免改名或游客 uuid 过期后历史展示错乱。</p>
 *
 * @author dz
 * @date 2026/5/25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    public static final String MSG_TYPE_TEXT = "TEXT";
    public static final String MSG_TYPE_IMAGE = "IMAGE";

    private long id;
    private long createdAt;
    /** 注册用户消息时填,游客为 null */
    private Long senderAccountId;
    /** 游客消息时填 client uuid,注册用户为 null */
    private String senderGuestUuid;
    private String senderNickname;
    private String msgType;
    private String content;

}
