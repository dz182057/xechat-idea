package cn.xeblog.server.pet;

import lombok.Data;

@Data
public class PetGameItemUseRecord {

    private String id;

    private String gameId;

    private long accountId;

    private String itemId;

    private String slot;

    private int definitionVersion;

    private String status;

    private String targetUserId;

    private String payloadJson;

    private int rewardBones;

    private long createdAt;

    private Long settledAt;
}
