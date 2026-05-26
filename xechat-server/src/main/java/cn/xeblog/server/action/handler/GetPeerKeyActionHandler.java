package cn.xeblog.server.action.handler;

import cn.xeblog.commons.entity.GetPeerKeyDTO;
import cn.xeblog.commons.entity.PeerKeyResponseDTO;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.account.AccountException;
import cn.xeblog.server.account.entity.Account;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.e2ee.E2EEKeyService;
import lombok.extern.slf4j.Slf4j;

/**
 * 查 peer 身份公钥(GET_PEER_KEY)。
 *
 * <p>客户端首次与某 peer 私聊前先用此 Action 拉对端 X25519 公钥,
 * 拿到后本地 ECDH+HKDF 派生会话密钥。</p>
 *
 * @author dz
 * @date 2026/5/26
 */
@Slf4j
@DoAction(Action.GET_PEER_KEY)
public class GetPeerKeyActionHandler extends AbstractActionHandler<GetPeerKeyDTO> {

    @Override
    protected void process(User user, GetPeerKeyDTO body) {
        if (body == null) {
            user.send(ResponseBuilder.system("GET_PEER_KEY 参数不能为空"));
            return;
        }
        try {
            Account peer = E2EEKeyService.requirePeerWithPubKey(body.getAccount());
            PeerKeyResponseDTO resp = new PeerKeyResponseDTO(
                    peer.getAccount(),
                    peer.getAccountId(),
                    peer.getNickname(),
                    peer.getIdentityPubKey());
            user.send(ResponseBuilder.build(null, resp, MessageType.PEER_KEY));
        } catch (AccountException e) {
            user.send(ResponseBuilder.system(e.getMessage()));
        } catch (Exception e) {
            log.error("查询 peer 公钥异常 peer={}", body.getAccount(), e);
            user.send(ResponseBuilder.system("查询对端公钥失败,请稍后重试"));
        }
    }

}
