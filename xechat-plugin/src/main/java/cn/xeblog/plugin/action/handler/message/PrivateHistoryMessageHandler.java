package cn.xeblog.plugin.action.handler.message;

import cn.hutool.json.JSONUtil;
import cn.xeblog.commons.entity.EncryptedEnvelopeDTO;
import cn.xeblog.commons.entity.UserMsgDTO;
import cn.xeblog.commons.entity.PullPrivateHistoryDTO;
import cn.xeblog.commons.entity.Response;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.plugin.action.ConsoleAction;
import cn.xeblog.plugin.annotation.DoMessage;
import cn.xeblog.plugin.cache.DataCache;
import cn.xeblog.plugin.crypto.E2EECrypto;
import cn.xeblog.plugin.crypto.E2EESessionService;
import cn.xeblog.plugin.enums.Style;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * 私聊密文历史响应(PULL_PRIVATE_HISTORY → PRIVATE_HISTORY)。
 *
 * <p>批量解密 envelopes,按 (isSelf, peer) 渲染到控制台。
 * isSelf 判定:env.senderAccountId == DataCache.accountId。</p>
 *
 * @author dz
 * @date 2026/5/26
 */
@DoMessage(MessageType.PRIVATE_HISTORY)
public class PrivateHistoryMessageHandler extends AbstractMessageHandler<PullPrivateHistoryDTO> {

    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm:ss");

    @Override
    protected void process(Response<PullPrivateHistoryDTO> response) {
        PullPrivateHistoryDTO body = response.getBody();
        if (body == null || body.getEnvelopes() == null || body.getEnvelopes().isEmpty()) {
            ConsoleAction.showSimpleMsg("与该用户的私聊历史为空");
            return;
        }
        String peerAccount = body.getPeerAccount();
        List<EncryptedEnvelopeDTO> envelopes = body.getEnvelopes();

        // 派生(或命中)sessionKey,然后批量解密
        E2EESessionService.ensureSessionKey(peerAccount).whenComplete((entry, err) -> {
            if (err != null) {
                ConsoleAction.showSimpleMsg("[E2EE] 私聊历史解密失败: " + err.getMessage());
                return;
            }
            String peerName = entry.nickname != null ? entry.nickname : entry.account;
            ConsoleAction.atomicExec(() -> {
                ConsoleAction.renderText("====== 与 " + peerName + " 的私聊历史(" + envelopes.size() + " 条) ======\n",
                        Style.SYSTEM_MSG);
                for (EncryptedEnvelopeDTO env : envelopes) {
                    try {
                        String plaintext = E2EECrypto.decryptMessage(entry.sessionKey,
                                env.getIv(), env.getCiphertext());
                        boolean isSelf = env.getSenderAccountId() != null
                                && env.getSenderAccountId() == DataCache.accountId;
                        String time = env.getServerCreatedAt() != null
                                ? TIME_FMT.format(new Date(env.getServerCreatedAt()))
                                : "";
                        String header = isSelf
                                ? String.format("[%s][私聊→%s] 我:", time, peerName)
                                : String.format("[%s][私聊←我] %s:", time, peerName);
                        PrivatePayload payload = decodePayload(plaintext);
                        ConsoleAction.renderText(header, Style.USER_NAME);
                        ConsoleAction.renderText(payload.displayText() + "\n", Style.LIGHT);
                    } catch (Exception ex) {
                        ConsoleAction.renderText("[解密失败] serverId=" + env.getServerId()
                                + ": " + ex.getMessage() + "\n", Style.WARN);
                    }
                }
                ConsoleAction.renderText("====== 历史结束 ======\n", Style.SYSTEM_MSG);
            });
        });
    }

    private PrivatePayload decodePayload(String plaintext) {
        String text = plaintext == null ? "" : plaintext.trim();
        if (!text.startsWith("{")) {
            return new PrivatePayload(plaintext == null ? "" : plaintext, UserMsgDTO.MsgType.TEXT);
        }
        try {
            cn.hutool.json.JSONObject obj = JSONUtil.parseObj(text);
            String content = obj.getStr("content");
            if (content == null) {
                return new PrivatePayload(plaintext, UserMsgDTO.MsgType.TEXT);
            }
            UserMsgDTO.MsgType msgType = UserMsgDTO.MsgType.TEXT;
            try {
                msgType = UserMsgDTO.MsgType.valueOf(obj.getStr("msgType", UserMsgDTO.MsgType.TEXT.name()));
            } catch (Exception ignored) {
            }
            return new PrivatePayload(content, msgType);
        } catch (Exception e) {
            return new PrivatePayload(plaintext, UserMsgDTO.MsgType.TEXT);
        }
    }

    private static class PrivatePayload {
        private final String content;
        private final UserMsgDTO.MsgType msgType;

        private PrivatePayload(String content, UserMsgDTO.MsgType msgType) {
            this.content = content;
            this.msgType = msgType;
        }

        private String displayText() {
            return msgType == UserMsgDTO.MsgType.IMAGE ? "[图片] " + content : content;
        }
    }

}
