package cn.xeblog.plugin.action;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import cn.xeblog.commons.entity.EncryptedEnvelopeDTO;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.UserMsgDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.plugin.cache.DataCache;
import cn.xeblog.plugin.crypto.E2EECrypto;
import cn.xeblog.plugin.crypto.E2EESessionService;
import cn.xeblog.plugin.enums.Command;
import cn.xeblog.plugin.listener.MainWindowInitializedEventListener;
import cn.xeblog.plugin.ui.MainWindow;
import cn.xeblog.plugin.util.CommandHistoryUtils;
import cn.xeblog.plugin.util.UploadUtils;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * @author anlingyi
 * @date 2022/8/4 9:48 PM
 */
public class InputAction implements MainWindowInitializedEventListener {

    private static JTextArea contentArea;

    private static JPanel leftTopPanel;

    private static JBList jbList;

    private static boolean isProactive;

    /**
     * 冻结时间
     */
    private final static long FREEZE_TIME = 15 * 1000;
    /**
     * 间隔时间
     */
    private final static long INTERVAL_TIME = 10 * 1000;

    /**
     * 冻结结束时间
     */
    private static long freezeEndTime;

    /**
     * 消息发送计数
     */
    private static int sendCounter = -1;

    /**
     * 消息发送计数开始时间
     */
    private static long sendCounterStartTime;

    @Override
    public void afterInit(MainWindow mainWindow) {
        contentArea = mainWindow.getContentArea();
        leftTopPanel = mainWindow.getLeftTopPanel();

        bindKeyListener();
    }

    private static void bindKeyListener() {
        contentArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                String content = contentArea.getText();

                if (KeyEvent.VK_ENTER == e.getKeyCode()) {
                    // 阻止默认事件
                    e.consume();
                    sendMsg();
                }

                if (e.getKeyCode() == KeyEvent.VK_TAB && leftTopPanel.isVisible()) {
                    e.consume();
                }

                if (e.getKeyCode() == 38 || e.getKeyCode() == 40) {
                    e.consume();
                    if (isProactive && leftTopPanel.isVisible() && jbList != null) {
                        jbList.requestFocus();
                    } else if (StrUtil.isBlank(content) || content.startsWith(Command.COMMAND_PREFIX)) {
                        String cmd = null;
                        if (e.getKeyCode() == 38) {
                            cmd = CommandHistoryUtils.getPrevCommand();
                        } else if (e.getKeyCode() == 40) {
                            cmd = CommandHistoryUtils.getNextCommand();
                        }

                        if (StrUtil.isNotBlank(cmd)) {
                            isProactive = false;
                            contentArea.setText(cmd);
                        }
                    }
                } else {
                    isProactive = true;
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                if ((e.isControlDown() || e.isMetaDown()) && e.getKeyCode() == KeyEvent.VK_V) {
                    if (!DataCache.isOnline) {
                        ConsoleAction.showLoginMsg();
                        return;
                    }

                    // 粘贴图片
                    pasteImage();
                } else {
                    // @用户和命令提示
                    atUserAndCommandTips(e);
                }
            }
        });
    }

    private static void pasteImage() {
        // 粘贴图片
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        Transferable transferable = clipboard.getContents(null);
        try {
            if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                List<File> fileList = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                UploadUtils.uploadImageFile(fileList.get(0));
                clean();
            } else if (transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                Image image = (Image) transferable.getTransferData(DataFlavor.imageFlavor);
                UploadUtils.uploadImage(image);
                clean();
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    private static void atUserAndCommandTips(KeyEvent e) {
        boolean isAt = false;
        List<String> dataList = null;
        String content = contentArea.getText();
        int caretPosition = contentArea.getCaretPosition();
        int atIndex = -1;
        String commandPrefix = Command.COMMAND_PREFIX;
        if (content.startsWith(commandPrefix)) {
            Map<String, String> commandMap = new LinkedHashMap<>();
            for (Command command : Command.values()) {
                commandMap.put(command.getCommand(), command.getCommand() + " (" + command.getDesc() + ")");
            }

            String command = content.substring(1);
            if (StrUtil.isBlank(command)) {
                dataList = new ArrayList<>(commandMap.values());
            } else {
                final List<String> matchList = new ArrayList<>();
                commandMap.forEach((k, v) -> {
                    if (k.toLowerCase().contains(command.toLowerCase()) || command.startsWith(k)) {
                        matchList.add(v);
                    }
                });
                dataList = matchList;
            }
        } else {
            if (DataCache.isOnline) {
                isAt = true;
                String atContent = content.substring(0, caretPosition);
                atIndex = atContent.lastIndexOf("@");
                if (atIndex > -1) {
                    List<User> onlineUserList = new ArrayList<>(DataCache.userMap.values());
                    onlineUserList.sort((u1, u2) -> {
                        int o1 = u1.getRole().ordinal();
                        int o2 = u2.getRole().ordinal();
                        if (o1 < o2) {
                            return -1;
                        }
                        if (o1 == o2) {
                            return 0;
                        }
                        return 1;
                    });

                    List<String> allUserList = new ArrayList<>();
                    onlineUserList.forEach(user -> allUserList.add(user.getUsername()));

                    String name = content.substring(atIndex + 1, caretPosition);
                    if (StrUtil.isNotBlank(name)) {
                        dataList = new ArrayList<>();
                        for (String user : allUserList) {
                            if (user.toLowerCase().contains(name.toLowerCase())) {
                                dataList.add(user);
                            }
                        }
                    }

                    if (atIndex + 1 == caretPosition && CollUtil.isEmpty(dataList)) {
                        dataList = allUserList;
                    }
                }
            }
        }

        leftTopPanel.setVisible(false);
        leftTopPanel.removeAll();

        if (CollectionUtil.isNotEmpty(dataList)) {
            boolean copyIsAt = isAt;
            int copyAtIndex = atIndex;

            Runnable runnable = () -> {
                if (jbList == null) {
                    return;
                }

                Object selectedValue = jbList.getSelectedValue();
                if (selectedValue == null) {
                    return;
                }

                String value = selectedValue.toString();
                if (copyIsAt) {
                    contentArea.replaceRange(value + " ", copyAtIndex + 1, caretPosition);
                } else {
                    contentArea.setText(value.substring(0, value.indexOf(" ")));
                }

                requestFocus();
                leftTopPanel.setVisible(false);
                leftTopPanel.removeAll();
            };

            jbList = new JBList();
            jbList.setListData(dataList.toArray());
            jbList.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (KeyEvent.VK_ENTER == e.getKeyCode()) {
                        runnable.run();
                    }
                }
            });

            jbList.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        runnable.run();
                    }
                }
            });

            JBScrollPane scrollPane = new JBScrollPane(jbList);
            scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

            leftTopPanel.setMinimumSize(new Dimension(0, 100));
            leftTopPanel.add(scrollPane);
            leftTopPanel.setVisible(true);

            if (e.getKeyCode() == KeyEvent.VK_TAB) {
                String value = dataList.get(0);
                if (copyIsAt) {
                    contentArea.replaceRange(value + " ", copyAtIndex + 1, caretPosition);
                } else {
                    contentArea.replaceRange(value.substring(0, value.indexOf(" ")), 0, caretPosition);
                }
            }
        }

        leftTopPanel.updateUI();
    }

    private static void sendMsg() {
        String content = contentArea.getText();
        if (StringUtils.isEmpty(content)) {
            return;
        }

        if (content.length() > 200) {
            ConsoleAction.showSimpleMsg("发送的内容长度不能超过200字符！");
        } else {
            if (content.startsWith(Command.COMMAND_PREFIX)) {
                ConsoleAction.showSimpleMsg(content);
                Command.handle(content);
            } else {
                if (DataCache.isOnline) {
                    if (sendCounter == 0 && System.currentTimeMillis() - sendCounterStartTime < INTERVAL_TIME) {
                        sendCounterStartTime = 0;
                        freezeEndTime = System.currentTimeMillis() + FREEZE_TIME;
                    }

                    long endTime = freezeEndTime - System.currentTimeMillis();
                    if (endTime > 0) {
                        ConsoleAction.showSimpleMsg("消息发送过于频繁，请于" + endTime / 1000 + "s后再发...");
                        return;
                    }

                    String[] toUsers = null;
                    List<String> toUserList = ReUtil.findAll("(@)([^\\s]+)([\\s]*)", content, 2);
                    if (CollectionUtil.isNotEmpty(toUserList)) {
                        List<String> removeList = new ArrayList<>();
                        for (String toUser : toUserList) {
                            if (DataCache.getUser(toUser) == null) {
                                removeList.add(toUser);
                            }
                        }
                        if (!removeList.isEmpty()) {
                            toUserList.removeAll(removeList);
                        }
                        if (!toUserList.isEmpty()) {
                            // 私聊不再带自己:E2EE 模式下 server 会把 PRIVATE_USER 推回发送方多端同步
                            toUsers = ArrayUtil.toArray(new HashSet<>(toUserList), String.class);
                        }
                    }

                    // 游客模式禁止私聊:本地拦截,避免发出后才被 server 拒绝(UX 差)
                    if (DataCache.guestMode && toUsers != null && toUsers.length > 0) {
                        ConsoleAction.showSimpleMsg("游客模式不能私聊,请先用 #login {账号} {密码} 登录账号");
                        return;
                    }

                    if (sendCounter == -1) {
                        sendCounter = 0;
                    }
                    if (++sendCounter >= 6) {
                        sendCounter = 0;
                    }
                    if (sendCounter == 1) {
                        sendCounterStartTime = System.currentTimeMillis();
                    }

                    if (toUsers != null && toUsers.length > 0) {
                        // E2EE 私聊:每个 peer 单独走 PRIVATE_CHAT 加密信封,不再走旧 CHAT 路径
                        sendPrivateE2EE(content, toUsers);
                    } else {
                        // 显式 cast 避免与 UserMsgDTO(Object,MsgType) 重载二义性
                        MessageAction.send(new UserMsgDTO(content, (String[]) null), Action.CHAT);
                    }
                } else {
                    ConsoleAction.showLoginMsg();
                }
            }
            clean();
        }

        ConsoleAction.gotoConsoleLow();
    }

    /**
     * E2EE 私聊发送:每个 peer 一条 PRIVATE_CHAT。
     *
     * <p>步骤:解析 peer username → 反查 account → ensureSessionKey(可能触发 GET_PEER_KEY 异步等待)
     * → encryptMessage → 发 PRIVATE_CHAT。发送方自己的回显由服务端把 PRIVATE_USER 回推后由
     * {@link cn.xeblog.plugin.action.handler.message.PrivateUserMessageHandler} 渲染。</p>
     *
     * <p>identityPrivKey 缺失(token 登录路径) → 直接提示用户重登,不发送任何消息。</p>
     */
    private static void sendPrivateE2EE(String content, String[] toUsers) {
        if (DataCache.identityPrivKey == null) {
            ConsoleAction.showSimpleMsg("E2EE 私钥未解锁,token 登录无法私聊,请 #exit 后用密码重登");
            return;
        }
        for (String peerUsername : toUsers) {
            User peer = DataCache.getUser(peerUsername);
            if (peer == null) {
                ConsoleAction.showSimpleMsg("找不到用户: " + peerUsername);
                continue;
            }
            String peerAccount = peer.getAccount();
            if (StrUtil.isBlank(peerAccount)) {
                ConsoleAction.showSimpleMsg(peerUsername + " 是游客,不能私聊");
                continue;
            }
            DataCache.peerAccountByUsername.put(peerUsername, peerAccount);
            E2EESessionService.ensureSessionKey(peerAccount).whenComplete((entry, err) -> {
                if (err != null) {
                    ConsoleAction.showSimpleMsg("E2EE 派生会话密钥失败(" + peerUsername + "): " + err.getMessage());
                    return;
                }
                try {
                    E2EECrypto.EncryptedMessage enc = E2EECrypto.encryptMessage(entry.sessionKey, content);
                    EncryptedEnvelopeDTO env = new EncryptedEnvelopeDTO();
                    env.setVersion("v1");
                    env.setPeerAccount(peerAccount);
                    env.setPeerAccountId(entry.accountId);
                    env.setIv(enc.iv);
                    env.setCiphertext(enc.ciphertext);
                    MessageAction.send(env, Action.PRIVATE_CHAT);
                } catch (Exception ex) {
                    ConsoleAction.showSimpleMsg("E2EE 加密失败(" + peerUsername + "): " + ex.getMessage());
                }
            });
        }
    }

    public static void clean() {
        contentArea.setText("");
    }

    public static boolean requestFocus() {
        contentArea.requestFocusInWindow();
        return contentArea.isFocusOwner();
    }

    public static boolean restCursor() {
        int len = StrUtil.length(contentArea.getText());
        contentArea.setCaretPosition(len);
        return requestFocus();
    }

}
