package cn.xeblog.commons.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 拉取历史聊天记录请求体(PULL_HISTORY)。
 *
 * <p>典型用法:
 * <ul>
 *   <li>首次/重连增量拉取: sinceMs=本地最新 ts(无本地缓存则 now-3*86400_000)</li>
 *   <li>向前翻页: beforeId=当前列表最老消息的 id</li>
 * </ul>
 * 服务端返回 {@link HistoryMsgDTO},其中 hasMore=true 表示当前批次之前还有更老记录。</p>
 *
 * @author dz
 * @date 2026/5/25
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PullHistoryDTO implements Serializable {

    /**
     * 只返回 created_at > sinceMs 的消息(增量拉取)
     */
    private Long sinceMs;

    /**
     * 只返回 id < beforeId 的消息(向前翻页)
     */
    private Long beforeId;

    /**
     * 单次最多返回条数,默认 50,服务端硬上限 200
     */
    private Integer limit;

}
