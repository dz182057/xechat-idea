package cn.xeblog.commons.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 私聊加密消息信封(PRIVATE_CHAT 上行 + PRIVATE_USER 下行)。
 *
 * <p>信封格式与 docs/design/e2ee-and-history.md §八对齐:
 * <ul>
 *   <li>会话密钥由双方各自从 ECDH(IdentityPriv, peerIdentityPub) + HKDF-SHA256 派生,无需协商</li>
 *   <li>iv: 12 字节 AES-GCM nonce,客户端每条消息独立随机生成</li>
 *   <li>ciphertext: AES-256-GCM(plaintext, sessionKey, iv);包含 GCM tag</li>
 *   <li>两个字段都用 base64url(无 padding)编码</li>
 * </ul>
 *
 * 服务端只看密文,不参与加解密。落 messages_private 表时直接存 iv + ciphertext。</p>
 *
 * @author dz
 * @date 2026/5/26
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EncryptedEnvelopeDTO implements Serializable {

    /**
     * 信封版本(目前固定 "v1",未来算法迁移用)
     */
    private String version;

    /**
     * 对端账号(上行时是接收方;下行时是发送方,服务端补齐)
     */
    private String peerAccount;

    /**
     * 对端账号 ID(同上,精确路由用)
     */
    private Long peerAccountId;

    /**
     * AES-GCM IV(base64url 12 字节)
     */
    private String iv;

    /**
     * AES-GCM 密文(base64url,含 GCM tag)
     */
    private String ciphertext;

    /**
     * 服务端落库后回填的雪花消息 ID(客户端用作本地缓存去重)
     */
    private Long serverId;

    /**
     * 服务端落库 epoch ms
     */
    private Long serverCreatedAt;

}
