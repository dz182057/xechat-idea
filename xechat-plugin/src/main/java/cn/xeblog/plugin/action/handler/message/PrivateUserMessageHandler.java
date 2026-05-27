package cn.xeblog.plugin.action.handler.message;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.thread.GlobalThreadPool;
import cn.hutool.json.JSONUtil;
import cn.xeblog.commons.entity.EncryptedEnvelopeDTO;
import cn.xeblog.commons.entity.MessageQuoteDTO;
import cn.xeblog.commons.entity.RecallMessageDTO;
import cn.xeblog.commons.entity.Response;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.UserMsgDTO;
import cn.xeblog.commons.entity.react.React;
import cn.xeblog.commons.entity.react.request.DownloadReact;
import cn.xeblog.commons.entity.react.result.DownloadReactResult;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.commons.enums.Platform;
import cn.xeblog.plugin.action.ConsoleAction;
import cn.xeblog.plugin.action.ReactAction;
import cn.xeblog.plugin.action.handler.ReactResultConsumer;
import cn.xeblog.plugin.annotation.DoMessage;
import cn.xeblog.plugin.cache.DataCache;
import cn.xeblog.plugin.crypto.E2EECrypto;
import cn.xeblog.plugin.crypto.E2EESessionService;
import cn.xeblog.plugin.entity.ChatMessageRef;
import cn.xeblog.plugin.enums.Style;
import cn.xeblog.plugin.util.NotifyUtils;
import com.intellij.ide.actions.OpenFileAction;
import com.intellij.openapi.application.ApplicationManager;

import javax.swing.*;
import javax.swing.text.StyleConstants;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.io.FileOutputStream;

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

    private static final String IMAGES_DIR = System.getProperty("user.home") + "/xechat/images";

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
            refBody.setMsgType(payload.msgType);
            refBody.setOriginalFileName(payload.originalFileName);
            String content = Boolean.TRUE.equals(response.getBody().getRecalled())
                    ? (isSelf ? "你撤回了一条消息" : "对方撤回了一条消息")
                    : payload.content;
            ChatMessageRef ref = ConsoleAction.beginMessage(isSelf ? DataCache.getCurrentUser() : fromUser,
                    refBody, RecallMessageDTO.ConversationType.PRIVATE, summary(payload, content));
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
            if (!Boolean.TRUE.equals(response.getBody().getRecalled())
                    && payload.msgType == UserMsgDTO.MsgType.IMAGE) {
                renderImage(ref, payload.content);
            } else {
                ConsoleAction.renderText(content + "\n", Style.LIGHT);
            }
            ConsoleAction.endMessage(ref);

            // 收到的私聊触发通知(自己发的不通知)
            if (!isSelf && DataCache.msgNotify != 3) {
                NotifyUtils.info(peerDisplay,
                        "[私聊] " + (payload.msgType == UserMsgDTO.MsgType.IMAGE ? "[图片]" : content),
                        true);
            }
        });
    }

    private void renderImage(ChatMessageRef ref, String fileName) {
        String filePath = IMAGES_DIR + "/" + fileName;
        JLabel imgLabel = createImageLabel(fileName, filePath);
        ref.setImageFileName(fileName);
        ref.setImageFilePath(filePath);
        ConsoleAction.bindImageMessage(imgLabel, ref);
        ConsoleAction.renderImageLabel(imgLabel);
    }

    private JLabel createImageLabel(String fileName, String filePath) {
        JLabel imgLabel = new JLabel();
        imgLabel.setAlignmentY(0.85f);
        imgLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        imgLabel.setForeground(StyleConstants.getForeground(Style.DEFAULT.get()));

        Runnable existFileRunnable = () -> {
            imgLabel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        OpenFileAction.openFile(filePath, DataCache.project);
                    });
                }
            });
            imgLabel.setEnabled(true);
            imgLabel.setText("查看图片 " + shortImageName(fileName));
            imgLabel.setToolTipText("点击查看图片");
            ConsoleAction.updateUI();
        };

        Runnable notExistFileRunnable = () -> {
            imgLabel.setEnabled(true);
            imgLabel.setToolTipText("点击下载图片");
            imgLabel.setText("下载图片 " + shortImageName(fileName));
            imgLabel.addMouseListener(new MouseAdapter() {
                MouseListener mouseListener = this;

                @Override
                public void mouseClicked(MouseEvent e) {
                    imgLabel.removeMouseListener(mouseListener);
                    imgLabel.setEnabled(false);
                    imgLabel.setText("图片下载中 " + shortImageName(fileName) + "...");
                    imgLabel.setToolTipText("图片下载中...");
                    ConsoleAction.updateUI();

                    GlobalThreadPool.execute(() -> ReactAction.request(new DownloadReact(fileName), React.DOWNLOAD, 300,
                            new ReactResultConsumer<DownloadReactResult>() {
                                @Override
                                public void doSucceed(DownloadReactResult body) {
                                    File imageFile = new File(filePath);
                                    if (!imageFile.exists()) {
                                        FileUtil.mkdir(IMAGES_DIR);
                                        try (FileOutputStream out = new FileOutputStream(imageFile)) {
                                            out.write(body.getBytes());
                                        } catch (Exception exception) {
                                            exception.printStackTrace();
                                        }
                                    }
                                    imgLabel.removeMouseListener(mouseListener);
                                    existFileRunnable.run();
                                }

                                @Override
                                public void doFailed(String msg) {
                                    imgLabel.setEnabled(true);
                                    imgLabel.setText("重新下载 " + shortImageName(fileName));
                                    imgLabel.setToolTipText("点击重新下载");
                                    imgLabel.addMouseListener(mouseListener);
                                    ConsoleAction.showSimpleMsg("图片下载失败！原因：" + msg);
                                }
                            }));
                }
            });
        };

        if (new File(filePath).exists()) {
            existFileRunnable.run();
        } else {
            notExistFileRunnable.run();
        }
        return imgLabel;
    }

    private void renderQuote(MessageQuoteDTO quote) {
        if (quote == null) {
            return;
        }
        String sender = quote.getSender() == null ? "未知" : quote.getSender();
        String content = quote.getMsgType() == UserMsgDTO.MsgType.IMAGE
                ? imageQuoteDisplay(quote.getContent())
                : quote.getContent();
        ConsoleAction.renderText("↪ 引用 " + sender + "：" + content + "\n", Style.LIGHT);
    }

    private String imageQuoteDisplay(String content) {
        if (content == null || content.isEmpty() || "[图片]".equals(content)) {
            return "[图片]";
        }
        String fileName = content.startsWith("[图片]") ? content.substring("[图片]".length()).trim() : content;
        if (fileName.length() > 16) {
            fileName = fileName.substring(0, 8) + "..." + fileName.substring(fileName.length() - 8);
        }
        return "[图片] " + fileName;
    }

    private String shortImageName(String fileName) {
        if (fileName == null || fileName.length() <= 16) {
            return fileName;
        }
        return fileName.substring(0, 8) + "..." + fileName.substring(fileName.length() - 8);
    }

    private PrivatePayload decodePayload(String plaintext) {
        String text = plaintext == null ? "" : plaintext.trim();
        if (!text.startsWith("{")) {
            return new PrivatePayload(plaintext == null ? "" : plaintext, null, UserMsgDTO.MsgType.TEXT, null);
        }
        try {
            cn.hutool.json.JSONObject obj = JSONUtil.parseObj(text);
            String content = obj.getStr("content");
            if (content == null) {
                return new PrivatePayload(plaintext, null, UserMsgDTO.MsgType.TEXT, null);
            }
            MessageQuoteDTO quote = parseQuote(obj.get("quote"));
            UserMsgDTO.MsgType msgType = UserMsgDTO.MsgType.TEXT;
            try {
                msgType = UserMsgDTO.MsgType.valueOf(obj.getStr("msgType", UserMsgDTO.MsgType.TEXT.name()));
            } catch (Exception ignored) {
            }
            return new PrivatePayload(content, quote, msgType, obj.getStr("originalFileName"));
        } catch (Exception e) {
            return new PrivatePayload(plaintext, null, UserMsgDTO.MsgType.TEXT, null);
        }
    }

    private MessageQuoteDTO parseQuote(Object rawQuote) {
        if (rawQuote == null || "null".equals(String.valueOf(rawQuote))) {
            return null;
        }
        try {
            return JSONUtil.toBean(rawQuote.toString(), MessageQuoteDTO.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String summary(PrivatePayload payload, String content) {
        if (payload.msgType == UserMsgDTO.MsgType.IMAGE) {
            return "[图片]";
        }
        return content.length() > 80 ? content.substring(0, 80) : content;
    }

    private static class PrivatePayload {
        private final String content;
        private final MessageQuoteDTO quote;
        private final UserMsgDTO.MsgType msgType;
        private final String originalFileName;

        private PrivatePayload(String content, MessageQuoteDTO quote, UserMsgDTO.MsgType msgType, String originalFileName) {
            this.content = content;
            this.quote = quote;
            this.msgType = msgType;
            this.originalFileName = originalFileName;
        }
    }

}
