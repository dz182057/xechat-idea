package cn.xeblog.commons.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 对端身份公钥响应(GET_PEER_KEY → PEER_KEY)。
 *
 * <p>identityPubKey 是 X25519 公钥(base64url 编码,32 字节原始)。
 * 客户端拿到后用本地身份私钥 + 此公钥做 ECDH,再 HKDF 派生会话密钥。</p>
 *
 * @author dz
 * @date 2026/5/26
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PeerKeyResponseDTO implements Serializable {

    private String account;

    /**
     * 对端账号的稳定 ID(雪花,字符串化避免 JS 精度丢失)
     */
    private Long accountId;

    private String nickname;

    /**
     * X25519 身份公钥(base64url,无 padding)
     */
    private String identityPubKey;

}
