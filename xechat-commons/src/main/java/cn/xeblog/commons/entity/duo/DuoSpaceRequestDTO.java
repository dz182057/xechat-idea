package cn.xeblog.commons.entity.duo;

import cn.xeblog.commons.enums.DuoInteractionType;
import cn.xeblog.commons.enums.DuoSpaceAction;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 双人小屋请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DuoSpaceRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private DuoSpaceAction action;
    private String requestId;
    private Long partnerAccountId;
    private Boolean accept;
    private String dogId;
    private DuoInteractionType gesture;
    private EncryptedPayloadDTO encryptedPayload;
    private String attachmentId;
    private String interactionId;
    private Integer choiceIndex;
    private String beforeDate;
}
