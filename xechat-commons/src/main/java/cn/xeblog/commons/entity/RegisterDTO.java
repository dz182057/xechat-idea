package cn.xeblog.commons.entity;

import cn.xeblog.commons.enums.Platform;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 注册请求。
 *
 * <p>服务端校验邀请码 → 校验账号/昵称合法性 → Argon2 hash 密码 → 入库 →
 * 创建 session 并返回 {@link LoginResultDTO}。</p>
 *
 * @author dz
 * @date 2026/5/22
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 邀请码
     */
    private String inviteCode;

    /**
     * 登录账号([a-zA-Z0-9_]{4,20})
     */
    private String account;

    /**
     * 明文密码(至少 8 位,含字母+数字)
     */
    private String password;

    /**
     * 昵称(≤12 字符,唯一)
     */
    private String nickname;

    /**
     * 客户端 uuid
     */
    private String uuid;

    /**
     * 来源平台
     */
    private Platform platform;

    /**
     * 客户端版本
     */
    private String pluginVersion;

    /**
     * E2EE: 客户端本地生成的 X25519 身份公钥(base64url,无 padding,32 字节)。
     * 服务端原样存 accounts.identity_pub_key,后续供其他人 GET_PEER_KEY 拉取。
     */
    private String identityPubKey;

    /**
     * E2EE: 主密钥包裹后的身份私钥(base64url 的 iv||ciphertext)。
     * 由客户端用 Argon2id(password + e2eeSalt) → masterKey,AES-256-GCM 包裹后上传。
     * 服务端原样存 key_envelopes 表(type='IDENTITY'),客户端下次登录拉回本地解。
     */
    private String identityPrivKeyEnvelope;

    /**
     * E2EE: 客户端在派生 masterKey 时实际用的 salt(base64url,16 字节)。
     * 注册时由客户端生成(SecureRandom)并随 REGISTER 一起上传,服务端存 accounts.e2ee_salt。
     * 客户端必须使用与登录密码 hash 完全分离的独立 salt(见 docs/design/e2ee-and-history.md §八)。
     */
    private String e2eeSalt;

    public void setPlatform(Platform platform) {
        this.platform = platform;
    }

    public void setPlatform(String platform) {
        try {
            this.platform = Platform.valueOf(platform);
        } catch (Exception e) {
            this.platform = Platform.DESKTOP;
        }
    }

}
