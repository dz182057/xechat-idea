package cn.xeblog.commons.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 历史聊天记录响应体。
 *
 * <p>用于 PULL_HISTORY 拉取公共频道历史的返回。msgList 按 id 升序(老→新)。
 * hasMore 表示当前批次之前(更老)还有未拉取记录,客户端可继续翻页。</p>
 *
 * @author anlingyi
 * @date 2021/9/11 7:00 下午
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class HistoryMsgDTO implements Serializable {

    private List<Response> msgList;

    /**
     * 当前批次之前(更老)是否还有未拉取记录。
     * <p>客户端向前翻页时若 hasMore=false 即可置灰"加载更多"按钮。</p>
     */
    private boolean hasMore;

    public HistoryMsgDTO(List<Response> msgList) {
        this.msgList = msgList;
    }

}
