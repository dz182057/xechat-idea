package cn.xeblog.commons.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 拉取与某 peer 的私聊密文历史请求(PULL_PRIVATE_HISTORY)。
 *
 * <p>peerAccount 必填(限定为某一对私聊会话);分页同 {@link PullHistoryDTO}。
 * 服务端按 (min(meAccountId, peerAccountId), max(...)) 配对维度查 messages_private 表。</p>
 *
 * @author dz
 * @date 2026/5/26
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PullPrivateHistoryDTO implements Serializable {

    /**
     * 对端账号
     */
    private String peerAccount;

    /**
     * 仅返回 created_at > sinceMs 的消息(增量)
     */
    private Long sinceMs;

    /**
     * 仅返回 id < beforeId 的消息(向前翻页)
     */
    private Long beforeId;

    /**
     * 单次最多返回条数;默认 50,服务端硬上限 200
     */
    private Integer limit;

    /**
     * 服务端返回时填充:本批次密文信封列表(按 id 升序);
     * 上行请求时该字段为 null
     */
    private List<EncryptedEnvelopeDTO> envelopes;

    /**
     * 服务端返回时填充:当前批次之前是否还有更老
     */
    private Boolean hasMore;

}
