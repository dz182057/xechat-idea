package cn.xeblog.commons.entity.duo;

import cn.xeblog.commons.enums.DuoInteractionType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 双人小屋每日互动。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DuoInteractionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String date;
    private long actorAccountId;
    private DuoInteractionType gesture;
    private EncryptedPayloadDTO encryptedPayload;
    private String attachmentId;
    private long createdAt;
    private Long viewedAt;
}
