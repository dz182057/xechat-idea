package cn.xeblog.server.action.handler;

import cn.hutool.core.util.StrUtil;
import cn.xeblog.commons.entity.EncryptedEnvelopeDTO;
import cn.xeblog.commons.entity.Response;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.account.AccountException;
import cn.xeblog.server.account.entity.Account;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.cache.UserCache;
import cn.xeblog.server.e2ee.E2EEKeyService;
import cn.xeblog.server.e2ee.PrivateMessageService;
import cn.xeblog.server.e2ee.entity.PrivateMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 私聊密文消息(PRIVATE_CHAT)。
 *
 * <p>服务端只看密文,不参与加解密:
 * <ol>
 *   <li>校验发送方非游客 + 参数完整</li>
 *   <li>查 peer 公钥确认其已启用 E2EE(顺带验证 peer 存在)</li>
 *   <li>密文落库 messages_private,生成 serverId+createdAt</li>
 *   <li>推 PRIVATE_USER 给 peer 所有在线连接(peerAccount=发送方)</li>
 *   <li>推 PRIVATE_USER 给发送方所有在线连接(peerAccount=接收方,作为回执+多端同步)</li>
 * </ol></p>
 *
 * @author dz
 * @date 2026/5/26
 */
@Slf4j
@DoAction(Action.PRIVATE_CHAT)
public class PrivateChatActionHandler extends AbstractActionHandler<EncryptedEnvelopeDTO> {

    @Override
    protected void process(User user, EncryptedEnvelopeDTO body) {
        if (user.isGuest()) {
            user.send(ResponseBuilder.system("游客不支持私聊"));
            return;
        }
        if (body == null
                || StrUtil.isBlank(body.getPeerAccount())
                || StrUtil.isBlank(body.getIv())
                || StrUtil.isBlank(body.getCiphertext())) {
            user.send(ResponseBuilder.system("私聊消息参数不完整(peerAccount/iv/ciphertext 必填)"));
            return;
        }

        try {
            Account peer = E2EEKeyService.requirePeerWithPubKey(body.getPeerAccount());
            if (peer.getAccountId() == user.getAccountId()) {
                user.send(ResponseBuilder.system("不能给自己发私聊"));
                return;
            }

            PrivateMessage saved = PrivateMessageService.save(
                    user.getAccountId(), peer.getAccountId(),
                    body.getIv(), body.getCiphertext(), body.getVersion());

            // 给接收方:peerAccount=发送方;senderAccountId 也填发送方(与 me 不等 → 客户端判 !isSelf)
            EncryptedEnvelopeDTO toRecipient = new EncryptedEnvelopeDTO(
                    saved.getVersion(),
                    user.getAccount(), user.getAccountId(),
                    saved.getIv(), saved.getCiphertext(),
                    saved.getId(), saved.getCreatedAt(),
                    user.getAccountId());
            Response respToPeer = ResponseBuilder.build(user, toRecipient, MessageType.PRIVATE_USER);

            // 给发送方(含其他端):peerAccount=接收方,senderAccountId=自己 → 客户端判 isSelf=true
            EncryptedEnvelopeDTO toSender = new EncryptedEnvelopeDTO(
                    saved.getVersion(),
                    peer.getAccount(), peer.getAccountId(),
                    saved.getIv(), saved.getCiphertext(),
                    saved.getId(), saved.getCreatedAt(),
                    user.getAccountId());
            Response respToSelf = ResponseBuilder.build(user, toSender, MessageType.PRIVATE_USER);

            List<User> peerConns = UserCache.getByAccount(peer.getAccountId());
            for (User u : peerConns) {
                u.send(respToPeer);
            }
            List<User> selfConns = UserCache.getByAccount(user.getAccountId());
            for (User u : selfConns) {
                u.send(respToSelf);
            }
        } catch (AccountException e) {
            user.send(ResponseBuilder.system(e.getMessage()));
        } catch (Exception e) {
            log.error("处理私聊密文异常 sender={} peer={}",
                    user.getAccount(), body.getPeerAccount(), e);
            user.send(ResponseBuilder.system("发送私聊失败,请稍后重试"));
        }
    }

}
