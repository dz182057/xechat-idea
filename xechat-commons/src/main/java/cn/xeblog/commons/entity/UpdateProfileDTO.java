package cn.xeblog.commons.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 个人资料更新请求(昵称/头像/密码,任一)。
 *
 * <p>四个字段同次请求只填一类:
 * <ul>
 *     <li>改昵称:仅填 {@link #nickname}</li>
 *     <li>换头像:仅填 {@link #avatarBase64}(限 256KB,服务端裁剪 256x256 PNG)</li>
 *     <li>改密码:同时填 {@link #oldPassword}、{@link #newPassword}、{@link #newE2eeSalt}、
 *     {@link #newIdentityPrivKeyEnvelope}</li>
 *     <li>原设备恢复 E2EE:仅填 {@link #newE2eeSalt} 和 {@link #newIdentityPrivKeyEnvelope}</li>
 * </ul>
 * 成功后服务端会广播 {@link ProfileUpdatedDTO}(改密码不广播)。</p>
 *
 * @author dz
 * @date 2026/5/22
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 新昵称(可空)
     */
    private String nickname;

    /**
     * 新头像 base64(可空,不含 data: 前缀,限 256KB 解码后)
     */
    private String avatarBase64;

    /**
     * 旧密码(改密码时必填)
     */
    private String oldPassword;

    /**
     * 新密码(改密码时必填,至少 8 位含字母+数字)
     */
    private String newPassword;

    /**
     * 新 E2EE salt(改密或原设备恢复重包时填写)
     */
    private String newE2eeSalt;

    /**
     * 用新 masterKey 包裹后的身份私钥 envelope(改密或原设备恢复重包时填写)
     */
    private String newIdentityPrivKeyEnvelope;

}
