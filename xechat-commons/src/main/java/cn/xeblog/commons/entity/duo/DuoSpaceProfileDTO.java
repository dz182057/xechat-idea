package cn.xeblog.commons.entity.duo;

import cn.xeblog.commons.enums.DuoDecoration;
import cn.xeblog.commons.enums.DuoSpaceStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 双人小屋完整资料。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DuoSpaceProfileDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private DuoSpaceStatus status;
    private String serverDate;
    private String spaceId;
    private DuoInviteDTO invite;
    private DuoPartnerDTO partner;
    private DuoDogSnapshotDTO myDog;
    private DuoDogSnapshotDTO partnerDog;
    private int warmth;
    private List<DuoDecoration> unlockedDecorations;
    private DuoTodayDTO today;
    private List<DuoMemoryDTO> recentMemories;
    private boolean hasMoreMemories;
}
