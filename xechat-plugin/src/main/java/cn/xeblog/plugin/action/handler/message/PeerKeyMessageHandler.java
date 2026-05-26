package cn.xeblog.plugin.action.handler.message;

import cn.xeblog.commons.entity.PeerKeyResponseDTO;
import cn.xeblog.commons.entity.Response;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.plugin.annotation.DoMessage;
import cn.xeblog.plugin.crypto.E2EESessionService;

/**
 * 对端身份公钥响应(GET_PEER_KEY → PEER_KEY)。
 *
 * <p>转交给 {@link E2EESessionService},由它派生 sessionKey 并唤醒等待者。</p>
 *
 * @author dz
 * @date 2026/5/26
 */
@DoMessage(MessageType.PEER_KEY)
public class PeerKeyMessageHandler extends AbstractMessageHandler<PeerKeyResponseDTO> {

    @Override
    protected void process(Response<PeerKeyResponseDTO> response) {
        E2EESessionService.handlePeerKey(response.getBody());
    }

}
