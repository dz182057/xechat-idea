package cn.xeblog.server.action.handler;

import cn.xeblog.commons.entity.HistoryMsgDTO;
import cn.xeblog.commons.entity.PullHistoryDTO;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.history.MessageHistoryService;
import lombok.extern.slf4j.Slf4j;

/**
 * 拉取公共频道历史(PULL_HISTORY)。
 *
 * <p>客户端首次/重连用 sinceMs 增量拉(无本地缓存时填 now - 3*86400_000 拉近 3 天),
 * 向前翻页用 beforeId。返回的 HistoryMsgDTO.msgList 按 id 升序;
 * hasMore=true 表示该批次之前还有更老记录。</p>
 *
 * @author dz
 * @date 2026/5/25
 */
@Slf4j
@DoAction(Action.PULL_HISTORY)
public class PullHistoryActionHandler extends AbstractActionHandler<PullHistoryDTO> {

    @Override
    protected void process(User user, PullHistoryDTO body) {
        Long sinceMs = body == null ? null : body.getSinceMs();
        Long beforeId = body == null ? null : body.getBeforeId();
        int limit = body == null || body.getLimit() == null ? 0 : body.getLimit();
        try {
            HistoryMsgDTO result = MessageHistoryService.queryPublic(sinceMs, beforeId, limit);
            user.send(ResponseBuilder.build(null, result, MessageType.HISTORY_MSG));
        } catch (Exception e) {
            log.error("拉取公共频道历史异常 sinceMs={} beforeId={} limit={}",
                    sinceMs, beforeId, limit, e);
            user.send(ResponseBuilder.system("拉取历史消息失败,请稍后重试"));
        }
    }

}
