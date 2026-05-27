package cn.xeblog.plugin.action;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.xeblog.commons.entity.EncryptedEnvelopeDTO;
import cn.xeblog.commons.entity.MessageQuoteDTO;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.UserMsgDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.plugin.cache.DataCache;
import cn.xeblog.plugin.crypto.E2EECrypto;
import cn.xeblog.plugin.crypto.E2EESessionService;
import cn.xeblog.plugin.enums.Command;
import cn.xeblog.plugin.entity.ChatMessageRef;
import cn.xeblog.plugin.listener.MainWindowInitializedEventListener;
import cn.xeblog.plugin.ui.EmojiPicker;
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

    /**
     * leftTopPanel 内部:私聊状态 banner(NORTH),独立于 @ 补全(CENTER)。
     * 让"粘性私聊"和"@ 补全"两种 UI 共存于同一行,通过 BorderLayout 上下叠放。
     */
    private static JPanel privateBannerPanel;
    private static JLabel privateBannerLabel;
    private static JPanel quoteBannerPanel;
    private static JLabel quoteBannerLabel;
    private static ChatMessageRef quoteMessageRef;

    /**
     * leftTopPanel 内部:原本的 @ 补全 JBList 放进这个容器(CENTER)。
     * 把 @ 补全和 banner 解耦,各自管自己的 visible 状态。
     */
    private static JPanel completionContainer;

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

        // emoji 字体回退:同 ConsoleAction,用逻辑字体 Dialog 让 JBR 在 Win 11 上自动 fallback 到
        // Segoe UI Emoji。否则用户在输入框打的 emoji 会显示为方块
        Font baseFont = contentArea.getFont();
        if (baseFont != null) {
            contentArea.setFont(new Font(Font.DIALOG, baseFont.getStyle(), baseFont.getSize()));
        }

        initLeftTopChildren();
        installEmojiButton();
        bindKeyListener();
    }

    /**
     * 在输入区 contentPanel 左侧塞一个 emoji 按钮。
     * 不改 .form 文件,而是运行时把 contentPanel 的布局换成 BorderLayout:WEST=按钮, CENTER=原 scrollpane。
     * 这样按钮高度跟随输入行高,行为自然;.form 的 GridLayoutManager 在加载后已确定 contentPanel 在父网格中的占位。
     */
    private static void installEmojiButton() {
        // contentArea → JViewport → JScrollPane → contentPanel
        Container scrollpane = contentArea.getParent().getParent();
        Container contentPanel = scrollpane.getParent();

        contentPanel.remove(scrollpane);
        contentPanel.setLayout(new BorderLayout(2, 0));

        JButton emojiButton = new JButton("😀");
        emojiButton.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));
        emojiButton.setMargin(new Insets(0, 6, 0, 6));
        emojiButton.setBorderPainted(false);
        emojiButton.setFocusable(false);
        emojiButton.setToolTipText("表情");

        // 单次 lazy 创建 popup,后续复用;每次显示前 contentArea 上侧弹出
        final JPopupMenu[] popupRef = new JPopupMenu[1];
        emojiButton.addActionListener(e -> {
            if (popupRef[0] == null) {
                popupRef[0] = EmojiPicker.create(emoji -> {
                    contentArea.insert(emoji, contentArea.getCaretPosition());
                    contentArea.requestFocusInWindow();
                });
            }
            JPopupMenu popup = popupRef[0];
            // 弹在按钮上方:y 取负 popup 高度,避免遮挡输入区
            popup.show(emojiButton, 0, -popup.getPreferredSize().height);
        });

        contentPanel.add(emojiButton, BorderLayout.WEST);
        contentPanel.add(scrollpane, BorderLayout.CENTER);
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    /**
     * 把 leftTopPanel(BorderLayout)拆为 NORTH=私聊 banner + CENTER=@ 补全容器。
     * 这样 banner 长期挂在那里,@ 补全只占 CENTER,二者互不干扰。
     */
    private static void initLeftTopChildren() {
        leftTopPanel.setLayout(new BorderLayout(0, 0));
        leftTopPanel.removeAll();

        JPanel bannerStack = new JPanel(new GridLayout(0, 1, 0, 0));

        quoteBannerPanel = new JPanel(new BorderLayout(4, 0));
        quoteBannerPanel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 4));
        quoteBannerLabel = new JLabel(" ");
        quoteBannerPanel.add(quoteBannerLabel, BorderLayout.CENTER);
        JButton cancelQuoteBtn = new JButton("取消引用 ×");
        cancelQuoteBtn.setMargin(new Insets(0, 6, 0, 6));
        cancelQuoteBtn.setFocusable(false);
        cancelQuoteBtn.addActionListener(e -> clearQuoteMessage());
        quoteBannerPanel.add(cancelQuoteBtn, BorderLayout.EAST);
        quoteBannerPanel.setVisible(false);
        bannerStack.add(quoteBannerPanel);

        privateBannerPanel = new JPanel(new BorderLayout(4, 0));
        privateBannerPanel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 4));
        privateBannerLabel = new JLabel(" ");
        privateBannerPanel.add(privateBannerLabel, BorderLayout.CENTER);

        JButton closeBtn = new JButton("取消 ×");
        closeBtn.setMargin(new Insets(0, 6, 0, 6));
        closeBtn.setFocusable(false);
        closeBtn.addActionListener(e -> {
            String old = DataCache.stickyPrivateTarget;
            DataCache.stickyPrivateTarget = null;
            hidePrivateBanner();
            if (old != null) {
                ConsoleAction.showSimpleMsg("已退出与 @" + old + " 的私聊模式");
            }
        });
        privateBannerPanel.add(closeBtn, BorderLayout.EAST);
        privateBannerPanel.setVisible(false);
        bannerStack.add(privateBannerPanel);

        completionContainer = new JPanel(new BorderLayout());

        leftTopPanel.add(bannerStack, BorderLayout.NORTH);
        leftTopPanel.add(completionContainer, BorderLayout.CENTER);
        leftTopPanel.setVisible(false);
    }

    /**
     * leftTopPanel 可见性 = banner 可见 OR 补全有内容;
     * 同步重置最小高度,避免 @ 补全期间设的 100px 残留导致 banner 单独显示时上方留大片空白。
     */
    private static void refreshLeftTopVisibility() {
        if (leftTopPanel == null) {
            return;
        }
        boolean bannerOn = (privateBannerPanel != null && privateBannerPanel.isVisible())
                || (quoteBannerPanel != null && quoteBannerPanel.isVisible());
        boolean completionOn = completionContainer != null && completionContainer.getComponentCount() > 0;
        leftTopPanel.setVisible(bannerOn || completionOn);
        if (!completionOn) {
            leftTopPanel.setMinimumSize(new Dimension(0, 0));
        }
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
                    onlineUserList.forEach(user -> {
                        // 过滤掉自己:@ 补全候选不应该出现当前登录用户,避免误选后给自己发私聊
                        if (!user.getUsername().equals(DataCache.username)) {
                            allUserList.add(user.getUsername());
                        }
                    });

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

        // 只清 @ 补全容器,banner 不动
        completionContainer.removeAll();
        refreshLeftTopVisibility();

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
                completionContainer.removeAll();
                refreshLeftTopVisibility();
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
            completionContainer.add(scrollPane, BorderLayout.CENTER);
            refreshLeftTopVisibility();

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
                            // 过滤:不存在的用户 + 自己(双保险,即便补全已过滤,手写 @ 仍可能输入自己)
                            if (DataCache.getUser(toUser) == null || toUser.equals(DataCache.username)) {
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

                    // 显式 @ 优先于 sticky:用户在 sticky 模式下又 @bob,按显式发给 bob,sticky 不变
                    if (toUsers == null && DataCache.stickyPrivateTarget != null) {
                        User stickyPeer = DataCache.getUser(DataCache.stickyPrivateTarget);
                        if (stickyPeer == null) {
                            ConsoleAction.showSimpleMsg("锁定的私聊对象 @" + DataCache.stickyPrivateTarget
                                    + " 已不在线,自动退出私聊模式");
                            DataCache.stickyPrivateTarget = null;
                            hidePrivateBanner();
                            return;
                        }
                        toUsers = new String[]{DataCache.stickyPrivateTarget};
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
                        UserMsgDTO dto = new UserMsgDTO(content, (String[]) null);
                        dto.setQuote(buildQuote());
                        MessageAction.send(dto, Action.CHAT);
                    }
                } else {
                    ConsoleAction.showLoginMsg();
                }
            }
            clean();
            clearQuoteMessage();
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
        String payload = buildPrivatePayload(content);
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
                    E2EECrypto.EncryptedMessage enc = E2EECrypto.encryptMessage(entry.sessionKey, payload);
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

    /**
     * 显示"🔒 私聊中: @peer"banner。EDT 安全:跨线程调用走 invokeLater。
     */
    public static void showPrivateBanner(String peerUsername) {
        Runnable r = () -> {
            if (privateBannerPanel == null || privateBannerLabel == null) {
                return;
            }
            privateBannerLabel.setText(" 🔒 私聊中: @" + peerUsername);
            privateBannerPanel.setVisible(true);
            refreshLeftTopVisibility();
            if (leftTopPanel != null) {
                leftTopPanel.revalidate();
                leftTopPanel.repaint();
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }

    /**
     * 隐藏私聊 banner。EDT 安全。
     */
    public static void hidePrivateBanner() {
        Runnable r = () -> {
            if (privateBannerPanel == null) {
                return;
            }
            privateBannerPanel.setVisible(false);
            refreshLeftTopVisibility();
            if (leftTopPanel != null) {
                leftTopPanel.revalidate();
                leftTopPanel.repaint();
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }

    public static void quoteMessage(ChatMessageRef ref) {
        Runnable r = () -> {
            quoteMessageRef = ref;
            if (quoteBannerPanel == null || quoteBannerLabel == null) {
                return;
            }
            String sender = ref.getUser() == null ? "未知" : ref.getUser().getUsername();
            quoteBannerLabel.setText(" 引用 @" + sender + "：" + ref.getSummary());
            quoteBannerPanel.setVisible(true);
            refreshLeftTopVisibility();
            leftTopPanel.revalidate();
            leftTopPanel.repaint();
            requestFocus();
        };
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }

    private static void clearQuoteMessage() {
        quoteMessageRef = null;
        if (quoteBannerPanel != null) {
            quoteBannerPanel.setVisible(false);
            refreshLeftTopVisibility();
        }
    }

    private static MessageQuoteDTO buildQuote() {
        if (quoteMessageRef == null || quoteMessageRef.getMessageId() == null) {
            return null;
        }
        MessageQuoteDTO quote = new MessageQuoteDTO();
        quote.setMessageId(quoteMessageRef.getMessageId());
        quote.setSender(quoteMessageRef.getUser() == null ? "未知" : quoteMessageRef.getUser().getUsername());
        quote.setMsgType(quoteMessageRef.getMsgType());
        quote.setContent(quoteMessageRef.getSummary());
        return quote;
    }

    private static String buildPrivatePayload(String content) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", content);
        payload.put("quote", buildQuote());
        return JSONUtil.toJsonStr(payload);
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
