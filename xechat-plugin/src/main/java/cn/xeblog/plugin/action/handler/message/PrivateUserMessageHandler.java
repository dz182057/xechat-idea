package cn.xeblog.plugin.action.handler.message;

import cn.hutool.json.JSONUtil;
import cn.xeblog.commons.entity.EncryptedEnvelopeDTO;
import cn.xeblog.commons.entity.MessageQuoteDTO;
import cn.xeblog.commons.entity.RecallMessageDTO;
import cn.xeblog.commons.entity.Response;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.UserMsgDTO;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.commons.enums.Platform;
import cn.xeblog.plugin.action.ConsoleAction;
import cn.xeblog.plugin.annotation.DoMessage;
import cn.xeblog.plugin.cache.DataCache;
import cn.xeblog.plugin.crypto.E2EECrypto;
import cn.xeblog.plugin.crypto.E2EESessionService;
import cn.xeblog.plugin.entity.ChatMessageRef;
import cn.xeblog.plugin.enums.Style;
import cn.xeblog.plugin.util.NotifyUtils;

/**
 * E2EE 私聊密文消息(PRIVATE_USER 下行)。
 *
 * <p>服务端推送规则(见 PrivateChatActionHandler):
 * <ul>
 *   <li>给接收方:resp.user = 发送方,env.peerAccount = 发送方 account</li>
 *   <li>给发送方多端同步:resp.user = 发送方(=自己),env.peerAccount = 接收方 account</li>
 * </ul>
 * isSelf 判定优先用 env.senderAccountId == DataCache.accountId(权威字段),
 * 老服务端不带则回退 resp.user.id 比对(plugin 端只有一个 DataCache.uuid 比较即可)。</p>
 *
 * @author dz
 * @date 2026/5/26
 */
@DoMessage(MessageType.PRIVATE_USER)
public class PrivateUserMessageHandler extends AbstractMessageHandler<EncryptedEnvelopeDTO> {

    @Override
    protected void process(Response<EncryptedEnvelopeDTO> response) {
        EncryptedEnvelopeDTO env = response.getBody();
        if (env == null || env.getIv() == null || env.getCiphertext() == null
                || env.getPeerAccount() == null) {
            return;
        }
        User fromUser = response.getUser();
        boolean isSelf;
        if (env.getSenderAccountId() != null) {
            isSelf = env.getSenderAccountId() == DataCache.accountId;
        } else {
            isSelf = fromUser != null && fromUser.getUsername() != null
                    && fromUser.getUsername().equals(DataCache.username);
        }

        E2EESessionService.ensureSessionKey(env.getPeerAccount()).whenComplete((entry, err) -> {
            if (err != null) {
                ConsoleAction.showSimpleMsg("[E2EE] 私聊解密失败(" + env.getPeerAccount()
                        + "): " + err.getMessage());
                return;
            }
            try {
                String plaintext = Boolean.TRUE.equals(env.getRecalled())
                        ? ""
                        : E2EECrypto.decryptMessage(entry.sessionKey, env.getIv(), env.getCiphertext());
                renderPrivate(response, fromUser, entry, plaintext, isSelf);
            } catch (Exception ex) {
                ConsoleAction.showSimpleMsg("[E2EE] 私聊解密失败: " + ex.getMessage());
            }
        });
    }

    /**
     * 渲染私聊消息到控制台。格式参考 UserMessageHandler 但加 "[私聊→peer]" 或 "[私聊←sender]" 区分方向。
     */
    private void renderPrivate(Response<EncryptedEnvelopeDTO> response, User fromUser,
                                E2EESessionService.SessionEntry entry,
                                String plaintext, boolean isSelf) {
        // peer 视角的展示名(对方昵称):
        // - isSelf:env.peerAccount=接收方,从 entry.nickname 拿
        // - !isSelf:env.peerAccount=发送方,从 fromUser.username 拿
        String peerDisplay;
        if (isSelf) {
            peerDisplay = entry.nickname != null ? entry.nickname : entry.account;
        } else {
            peerDisplay = fromUser != null ? fromUser.getUsername() : entry.account;
        }

        ConsoleAction.atomicExec(() -> {
            PrivatePayload payload = decodePayload(plaintext);
            UserMsgDTO refBody = new UserMsgDTO(payload.content);
            refBody.setServerId(response.getBody().getServerId());
            refBody.setServerCreatedAt(response.getBody().getServerCreatedAt());
            refBody.setMsgType(UserMsgDTO.MsgType.TEXT);
            ChatMessageRef ref = ConsoleAction.beginMessage(isSelf ? DataCache.getCurrentUser() : fromUser,
                    refBody, RecallMessageDTO.ConversationType.PRIVATE, payload.content);
            String time = response.getTime();
            String region = fromUser != null ? fromUser.getShortRegion() : "";
            String platform = fromUser != null && fromUser.getPlatform() == Platform.WEB ? " ༄" : " ♨";
            String header;
            if (isSelf) {
                // 我发给 peerDisplay
                header = String.format("[%s][私聊→%s] 我%s:", time, peerDisplay, platform);
            } else {
                // peerDisplay 发给我
                header = String.format("[%s][%s][私聊←我] %s%s:", time, region, peerDisplay, platform);
            }
            ConsoleAction.renderText(header, Style.USER_NAME);
            renderQuote(payload.quote);
            String content = Boolean.TRUE.equals(response.getBody().getRecalled())
                    ? (isSelf ? "你撤回了一条消息" : "对方撤回了一条消息")
                    : payload.content;
            ConsoleAction.renderText(content + "\n", Style.LIGHT);
            ConsoleAction.endMessage(ref);

            // 收到的私聊触发通知(自己发的不通知)
            if (!isSelf && DataCache.msgNotify != 3) {
                NotifyUtils.info(peerDisplay, "[私聊] " + content, true);
            }
        });
    }

    private void renderQuote(MessageQuoteDTO quote) {
        if (quote == null) {
            return;
        }
        String sender = quote.getSender() == null ? "未知" : quote.getSender();
        String content = quote.getMsgType() == UserMsgDTO.MsgType.IMAGE ? "[图片]" : quote.getContent();
        ConsoleAction.renderText("↪ 引用 " + sender + "：" + content + "\n", Style.LIGHT);
    }

    private PrivatePayload decodePayload(String plaintext) {
        if (plaintext == null || !plaintext.startsWith("{")) {
            return new PrivatePayload(plaintext == null ? "" : plaintext, null);
        }
        try {
            cn.hutool.json.JSONObject obj = JSONUtil.parseObj(plaintext);
            String content = obj.getStr("content");
            if (content == null) {
                return new PrivatePayload(plaintext, null);
            }
            MessageQuoteDTO quote = obj.get("quote") == null
                    ? null
                    : JSONUtil.toBean(obj.get("quote").toString(), MessageQuoteDTO.class);
            return new PrivatePayload(content, quote);
        } catch (Exception e) {
            return new PrivatePayload(plaintext, null);
        }
    }

    private static class PrivatePayload {
        private final String content;
        private final MessageQuoteDTO quote;

        private PrivatePayload(String content, MessageQuoteDTO quote) {
            this.content = content;
            this.quote = quote;
        }
    }

}
