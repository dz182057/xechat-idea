package cn.xeblog.server.action.handler;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.duo.DuoSpaceRequestDTO;
import cn.xeblog.commons.entity.duo.DuoSpaceResponseDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.DuoSpaceAction;
import cn.xeblog.commons.enums.DuoSpaceEvent;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.duo.DuoSpaceService;
import lombok.extern.slf4j.Slf4j;

/**
 * 双人小屋 WebSocket 请求入口。
 */
@Slf4j
@DoAction(Action.DUO_SPACE)
public class DuoSpaceActionHandler extends AbstractActionHandler<DuoSpaceRequestDTO> {

    @Override
    protected void process(User user, DuoSpaceRequestDTO body) {
        String requestId = body == null ? null : body.getRequestId();
        if (user.isGuest() || user.getAccountId() <= 0L) {
            sendError(user, requestId, "游客不能使用双人小屋，请登录账号后再进入");
            return;
        }
        DuoSpaceAction action = body == null ? null : body.getAction();
        if (action == null) {
            sendError(user, requestId, "双人小屋操作不能为空");
            return;
        }
        try {
            switch (action) {
                case PROFILE:
                    sendProfile(user, requestId);
                    break;
                case INVITE:
                    DuoSpaceService.invite(user.getAccountId(), value(body.getPartnerAccountId()));
                    sendProfileIfRequested(user, requestId);
                    break;
                case RESPOND_INVITE:
                    DuoSpaceService.respondInvite(user.getAccountId(), Boolean.TRUE.equals(body.getAccept()));
                    sendProfileIfRequested(user, requestId);
                    break;
                case CANCEL_INVITE:
                    DuoSpaceService.cancelInvite(user.getAccountId());
                    sendProfileIfRequested(user, requestId);
                    break;
                case SET_DOG:
                    DuoSpaceService.setDog(user.getAccountId(), body.getDogId());
                    sendProfileIfRequested(user, requestId);
                    break;
                case SUBMIT_INTERACTION:
                    DuoSpaceService.submitInteraction(user.getAccountId(), body.getGesture(),
                            body.getEncryptedPayload(), body.getAttachmentId());
                    sendProfileIfRequested(user, requestId);
                    break;
                case ACK_INTERACTION:
                    DuoSpaceService.ackInteraction(user.getAccountId(), body.getInteractionId());
                    sendProfileIfRequested(user, requestId);
                    break;
                case SUBMIT_DAILY_QUIZ:
                    DuoSpaceService.submitDailyQuiz(user.getAccountId(), body.getChoiceIndex());
                    sendProfileIfRequested(user, requestId);
                    break;
                case LIST_MEMORIES:
                    DuoSpaceResponseDTO memories = new DuoSpaceResponseDTO(
                            DuoSpaceEvent.MEMORIES, body.getRequestId(), null,
                            DuoSpaceService.listMemories(user.getAccountId(), body.getBeforeDate()));
                    user.send(ResponseBuilder.build(null, memories, MessageType.DUO_SPACE));
                    break;
                case CLOSE_SPACE:
                    DuoSpaceService.closeSpace(user.getAccountId());
                    sendProfileIfRequested(user, requestId);
                    break;
                default:
                    sendError(user, requestId, "暂不支持该双人小屋操作");
                    break;
            }
        } catch (IllegalArgumentException e) {
            sendError(user, requestId, e.getMessage());
        } catch (Exception e) {
            log.error("双人小屋操作失败 accountId={} action={}", user.getAccountId(), action, e);
            sendError(user, requestId, "双人小屋操作失败，请稍后重试");
        }
    }

    private void sendProfileIfRequested(User user, String requestId) {
        if (requestId != null && !requestId.trim().isEmpty()) {
            sendProfile(user, requestId);
        }
    }

    private void sendProfile(User user, String requestId) {
        DuoSpaceResponseDTO response = new DuoSpaceResponseDTO(
                DuoSpaceEvent.PROFILE, requestId, DuoSpaceService.profile(user.getAccountId()), null);
        user.send(ResponseBuilder.build(null, response, MessageType.DUO_SPACE));
    }

    private void sendError(User user, String requestId, String message) {
        String safeMessage = message == null || message.trim().isEmpty() ? "双人小屋操作失败，请稍后重试" : message;
        if (requestId == null || requestId.trim().isEmpty()) {
            user.send(ResponseBuilder.system(safeMessage));
            return;
        }
        DuoSpaceResponseDTO response = new DuoSpaceResponseDTO(
                DuoSpaceEvent.PROFILE, requestId, null, null, safeMessage);
        user.send(ResponseBuilder.build(null, response, MessageType.DUO_SPACE));
    }

    private static long value(Long value) {
        return value == null ? 0L : value;
    }
}
