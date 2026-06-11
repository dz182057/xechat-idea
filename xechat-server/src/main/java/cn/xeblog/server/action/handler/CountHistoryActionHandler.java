package cn.xeblog.server.action.handler;

import cn.hutool.core.util.StrUtil;
import cn.xeblog.commons.entity.CountHistoryDTO;
import cn.xeblog.commons.entity.HistoryCountDTO;
import cn.xeblog.commons.entity.RecallMessageDTO;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.account.AccountException;
import cn.xeblog.server.account.entity.Account;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.e2ee.E2EEKeyService;
import cn.xeblog.server.e2ee.PrivateMessageService;
import cn.xeblog.server.history.MessageHistoryService;
import lombok.extern.slf4j.Slf4j;

/**
 * 查询当前会话历史总数(COUNT_HISTORY)。
 *
 * @author dz
 * @date 2026/6/10
 */
@Slf4j
@DoAction(Action.COUNT_HISTORY)
public class CountHistoryActionHandler extends AbstractActionHandler<CountHistoryDTO> {

    @Override
    protected void process(User user, CountHistoryDTO body) {
        RecallMessageDTO.ConversationType type = body == null ? null : body.getConversationType();
        if (type == null) {
            user.send(ResponseBuilder.system("查询历史总数需要 conversationType"));
            return;
        }

        try {
            if (type == RecallMessageDTO.ConversationType.PUBLIC) {
                long total = MessageHistoryService.countPublic();
                user.send(ResponseBuilder.build(null,
                        new HistoryCountDTO(type, null, total),
                        MessageType.HISTORY_COUNT));
                return;
            }

            if (user.isGuest()) {
                user.send(ResponseBuilder.system("游客不支持私聊历史"));
                return;
            }
            if (StrUtil.isBlank(body.getPeerAccount())) {
                user.send(ResponseBuilder.system("查询私聊历史总数需要 peerAccount"));
                return;
            }

            Account peer = E2EEKeyService.requirePeerWithPubKey(body.getPeerAccount());
            long total = PrivateMessageService.countConversation(user.getAccountId(), peer.getAccountId());
            user.send(ResponseBuilder.build(null,
                    new HistoryCountDTO(type, peer.getAccount(), total),
                    MessageType.HISTORY_COUNT));
        } catch (AccountException e) {
            user.send(ResponseBuilder.system(e.getMessage()));
        } catch (Exception e) {
            log.error("查询历史总数异常 user={} type={} peer={}",
                    user.getAccount(), type, body == null ? null : body.getPeerAccount(), e);
            user.send(ResponseBuilder.system("查询历史总数失败,请稍后重试"));
        }
    }

}
