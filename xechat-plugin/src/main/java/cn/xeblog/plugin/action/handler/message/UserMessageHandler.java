package cn.xeblog.plugin.action.handler.message;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.thread.GlobalThreadPool;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
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
import cn.xeblog.commons.enums.UserStatus;
import cn.xeblog.plugin.action.ConsoleAction;
import cn.xeblog.plugin.action.ReactAction;
import cn.xeblog.plugin.action.handler.ReactResultConsumer;
import cn.xeblog.plugin.annotation.DoMessage;
import cn.xeblog.plugin.cache.DataCache;
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
import java.util.Map;

/**
 * @author anlingyi
 * @date 2020/8/19
 */
@DoMessage(MessageType.USER)
public class UserMessageHandler extends AbstractMessageHandler<UserMsgDTO> {

    private static final String IMAGES_DIR = System.getProperty("user.home") + "/xechat/images";

    @Override
    protected void process(Response<UserMsgDTO> response) {
        User user = response.getUser();
        UserMsgDTO body = response.getBody();
        if (isPrivate(body) && !isPrivateVisibleToMe(user, body)) {
            return;
        }
        boolean isImage = body.getMsgType() == UserMsgDTO.MsgType.IMAGE && !Boolean.TRUE.equals(body.getRecalled());
        if (isImage) {
            renderImage(response);
        } else {
            ConsoleAction.atomicExec(() -> {
                ChatMessageRef ref = ConsoleAction.beginMessage(user, body,
                        conversationType(body), summary(body));
                renderName(response);
                renderQuote(body.getQuote());
                boolean notified = body.hasUser(DataCache.username);
                Style style = Style.DEFAULT;
                String msg = Boolean.TRUE.equals(body.getRecalled())
                        ? (user.getUsername().equals(DataCache.username) ? "你撤回了一条消息" : "对方撤回了一条消息")
                        : (String) body.getContent();
                if (notified) {
                    style = Style.LIGHT;
                    if (!user.getUsername().equals(DataCache.username)) {
                        NotifyUtils.info(user.getUsername(), msg, true);
                    }
                }
                ConsoleAction.renderText(msg + "\n", style);
                ConsoleAction.endMessage(ref);
            });
        }
    }

    private void renderName(Response<UserMsgDTO> response) {
        User user = response.getUser();
        String platform = user.getPlatform() == Platform.WEB ? " ༄" : " ♨";
        String roleDisplay = "";
        if (user.getRole() == User.Role.ADMIN) {
            roleDisplay = " ☆";
        }
        UserStatus status = user.getStatus() == null ? UserStatus.FISHING : user.getStatus();

        ConsoleAction.renderText(
                String.format("[%s][%s] %s (%s)%s%s：", response.getTime(), user.getShortRegion(), user.getUsername(),
                        status.getName(), platform, roleDisplay), Style.USER_NAME);
    }

    private void renderImage(Response<UserMsgDTO> response) {
        UserMsgDTO body = response.getBody();
        String fileName = imageFileName(body);
        String filePath = IMAGES_DIR + "/" + fileName;
        boolean existFile = new File(filePath).exists();

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

                    GlobalThreadPool.execute(() -> {
                        ReactAction.request(new DownloadReact(fileName), React.DOWNLOAD, 300, new ReactResultConsumer<DownloadReactResult>() {
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
                        });
                    });
                }
            });
        };

        if (existFile) {
            existFileRunnable.run();
        } else {
            notExistFileRunnable.run();
        }

        ConsoleAction.atomicExec(() -> {
            ChatMessageRef ref = ConsoleAction.beginMessage(response.getUser(), body,
                    conversationType(body), summary(body));
            ref.setImageFileName(fileName);
            ref.setImageFilePath(filePath);
            ConsoleAction.bindImageMessage(imgLabel, ref);
            renderName(response);
            renderQuote(body.getQuote());
            ConsoleAction.renderImageLabel(imgLabel);
            ConsoleAction.endMessage(ref);
        });
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

    private String summary(UserMsgDTO body) {
        if (body == null) {
            return "";
        }
        if (body.getMsgType() == UserMsgDTO.MsgType.IMAGE) {
            return "[图片]";
        }
        String text = body.getContent() == null ? "" : String.valueOf(body.getContent());
        return text.length() > 80 ? text.substring(0, 80) : text;
    }

    private boolean isPrivate(UserMsgDTO body) {
        return body != null && body.getToUsers() != null && body.getToUsers().length > 0;
    }

    private boolean isPrivateVisibleToMe(User user, UserMsgDTO body) {
        return body.hasUser(DataCache.username)
                || (user != null && user.getUsername() != null && user.getUsername().equals(DataCache.username));
    }

    private RecallMessageDTO.ConversationType conversationType(UserMsgDTO body) {
        return isPrivate(body) ? RecallMessageDTO.ConversationType.PRIVATE : RecallMessageDTO.ConversationType.PUBLIC;
    }

    private String imageFileName(UserMsgDTO body) {
        Object content = body.getContent();
        if (content instanceof CharSequence) {
            String text = String.valueOf(content);
            if (text.trim().startsWith("{")) {
                try {
                    content = JSONUtil.parseObj(text);
                } catch (Exception ignored) {
                    return text;
                }
            } else {
                return text;
            }
        }
        String fileName = readImageField(content, "fileName");
        if (fileName == null) {
            fileName = readImageField(content, "thumbFileName");
        }
        if (fileName == null) {
            fileName = readImageField(content, "compressedFileName");
        }
        return fileName == null ? String.valueOf(content) : fileName;
    }

    private String readImageField(Object content, String key) {
        if (content instanceof JSONObject) {
            return ((JSONObject) content).getStr(key);
        }
        if (content instanceof Map) {
            Object value = ((Map<?, ?>) content).get(key);
            return value == null ? null : String.valueOf(value);
        }
        return null;
    }

}
