package cn.xeblog.server.action.handler;

import cn.xeblog.commons.entity.RecallMessageDTO;
import cn.xeblog.commons.entity.Response;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.action.ChannelAction;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.cache.UserCache;
import cn.xeblog.server.e2ee.PrivateMessageService;
import cn.xeblog.server.e2ee.entity.PrivateMessage;
import cn.xeblog.server.history.MessageHistoryService;
import cn.xeblog.server.history.entity.Message;

/**
 * 撤回消息。服务端只允许撤回 2 分钟内自己发送的消息。
 *
 * @author dz
 * @date 2026/5/27
 */
@DoAction(Action.RECALL_MESSAGE)
public class RecallMessageActionHandler extends AbstractActionHandler<RecallMessageDTO> {

    private static final long RECALL_WINDOW_MS = 2 * 60 * 1000;

    @Override
    protected void process(User user, RecallMessageDTO body) {
        if (body == null || body.getMessageId() == null || body.getConversationType() == null) {
            user.send(ResponseBuilder.system("撤回消息参数不完整"));
            return;
        }

        if (body.getConversationType() == RecallMessageDTO.ConversationType.PUBLIC) {
            recallPublic(user, body.getMessageId());
        } else {
            recallPrivate(user, body.getMessageId());
        }
    }

    private void recallPublic(User user, long messageId) {
        Message msg = MessageHistoryService.findPublic(messageId);
        if (msg == null) {
            user.send(ResponseBuilder.system("消息不存在或已过期"));
            return;
        }
        if (!isPublicSender(user, msg)) {
            user.send(ResponseBuilder.system("只能撤回自己发送的消息"));
            return;
        }
        if (!withinRecallWindow(msg.getCreatedAt())) {
            user.send(ResponseBuilder.system("只能撤回 2 分钟内的消息"));
            return;
        }

        long recalledAt = System.currentTimeMillis();
        MessageHistoryService.markPublicRecalled(messageId, recalledAt);
        RecallMessageDTO dto = new RecallMessageDTO();
        dto.setMessageId(messageId);
        dto.setConversationType(RecallMessageDTO.ConversationType.PUBLIC);
        dto.setSenderAccountId(msg.getSenderAccountId());
        dto.setRecalledAt(recalledAt);
        ChannelAction.send(user, dto, MessageType.MESSAGE_RECALLED);
    }

    private void recallPrivate(User user, long messageId) {
        if (user.isGuest()) {
            user.send(ResponseBuilder.system("游客不支持撤回私聊消息"));
            return;
        }
        PrivateMessage msg = PrivateMessageService.findById(messageId);
        if (msg == null) {
            user.send(ResponseBuilder.system("消息不存在或已过期"));
            return;
        }
        if (msg.getSenderAccountId() != user.getAccountId()) {
            user.send(ResponseBuilder.system("只能撤回自己发送的消息"));
            return;
        }
        if (!withinRecallWindow(msg.getCreatedAt())) {
            user.send(ResponseBuilder.system("只能撤回 2 分钟内的消息"));
            return;
        }

        long recalledAt = System.currentTimeMillis();
        PrivateMessageService.markRecalled(messageId, recalledAt);
        RecallMessageDTO dto = new RecallMessageDTO();
        dto.setMessageId(messageId);
        dto.setConversationType(RecallMessageDTO.ConversationType.PRIVATE);
        dto.setSenderAccountId(msg.getSenderAccountId());
        dto.setRecipientAccountId(msg.getRecipientAccountId());
        dto.setRecalledAt(recalledAt);
        Response resp = ResponseBuilder.build(user, dto, MessageType.MESSAGE_RECALLED);
        for (User u : UserCache.getByAccount(msg.getSenderAccountId())) {
            u.send(resp);
        }
        for (User u : UserCache.getByAccount(msg.getRecipientAccountId())) {
            u.send(resp);
        }
    }

    private boolean isPublicSender(User user, Message msg) {
        if (user.isGuest()) {
            return msg.getSenderGuestUuid() != null && msg.getSenderGuestUuid().equals(user.getUuid());
        }
        return msg.getSenderAccountId() != null && msg.getSenderAccountId() == user.getAccountId();
    }

    private boolean withinRecallWindow(long createdAt) {
        return System.currentTimeMillis() - createdAt <= RECALL_WINDOW_MS;
    }

}
