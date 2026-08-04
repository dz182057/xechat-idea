package cn.xeblog.commons.entity.duo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 双人小屋端到端加密载荷。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EncryptedPayloadDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String version;
    private String iv;
    private String ciphertext;
}
