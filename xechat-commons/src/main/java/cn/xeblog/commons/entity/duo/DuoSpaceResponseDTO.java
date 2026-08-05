package cn.xeblog.commons.entity.duo;

import cn.xeblog.commons.enums.DuoSpaceEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 双人小屋 WebSocket 响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DuoSpaceResponseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private DuoSpaceEvent event;
    private String requestId;
    private DuoSpaceProfileDTO profile;
    private DuoMemoryPageDTO memories;
    private String error;

    public DuoSpaceResponseDTO(DuoSpaceEvent event, String requestId,
                               DuoSpaceProfileDTO profile, DuoMemoryPageDTO memories) {
        this(event, requestId, profile, memories, null);
    }
}
