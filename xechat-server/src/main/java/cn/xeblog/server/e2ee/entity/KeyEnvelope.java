package cn.xeblog.server.e2ee.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * key_envelopes 表实体:主密钥包裹后的私钥(及未来其他密钥)。
 *
 * <p>type='IDENTITY' 存身份私钥;未来如果引入会话密钥包裹,可加 type='SESSION:&lt;peerId&gt;'。
 * 服务端只是不透明的字符串中转站,不参与解密。</p>
 *
 * @author dz
 * @date 2026/5/26
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KeyEnvelope {

    public static final String TYPE_IDENTITY = "IDENTITY";

    private long accountId;
    private String type;
    /** master 包裹后的密文(base64url iv||ciphertext) */
    private String envelope;
    private long createdAt;
    private long updatedAt;

}
