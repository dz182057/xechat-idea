package cn.xeblog.server.e2ee.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * messages_private 表实体:私聊密文消息。
 *
 * <p>服务端只看密文,不参与解密。conv_min/conv_max 是 (sender, recipient) 的字典序对,
 * 便于按"会话维度"查询(无需关心方向)。</p>
 *
 * @author dz
 * @date 2026/5/26
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrivateMessage {

    private long id;
    private long createdAt;
    private long senderAccountId;
    private long recipientAccountId;
    private long convMin;
    private long convMax;
    /** AES-GCM IV(base64url 12B) */
    private String iv;
    /** AES-256-GCM 密文(base64url,含 GCM tag) */
    private String ciphertext;
    /** 信封版本(默认 v1) */
    private String version;

}
