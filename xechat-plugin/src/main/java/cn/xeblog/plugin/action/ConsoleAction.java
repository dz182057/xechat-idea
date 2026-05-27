package cn.xeblog.plugin.action;

import cn.hutool.core.util.StrUtil;
import cn.xeblog.commons.entity.RecallMessageDTO;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.UserMsgDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.plugin.cache.DataCache;
import cn.xeblog.plugin.entity.ChatMessageRef;
import cn.xeblog.plugin.entity.TextRender;
import cn.xeblog.plugin.enums.Command;
import cn.xeblog.plugin.enums.Style;
import cn.xeblog.plugin.listener.MainWindowInitializedEventListener;
import cn.xeblog.plugin.mode.ModeContext;
import cn.xeblog.plugin.ui.MainWindow;
import com.intellij.ide.BrowserUtil;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.text.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author anlingyi
 * @date 2020/6/1
 */
public class ConsoleAction implements MainWindowInitializedEventListener {

    private static final Object LOCK = new Object();

    private static JTextPane console;

    private static JPanel panel;

    private static JScrollPane consoleScroll;

    private static volatile boolean isNewLine;

    private static final List<ChatMessageRef> MESSAGE_REFS = new ArrayList<>();

    private static ChatMessageRef selectedMessageRef;

    @Override
    public void afterInit(MainWindow mainWindow) {
        console = mainWindow.getConsoleTextPane();
        panel = mainWindow.getLeftPanel();
        consoleScroll = mainWindow.getConsoleScrollPane();

        console.setEditorKit(new WarpEditorKit());
        SimpleAttributeSet simpleAttributeSet = new SimpleAttributeSet();
        StyleConstants.setLineSpacing(simpleAttributeSet, 0.2f);
        console.setParagraphAttributes(simpleAttributeSet, false);

        // emoji 字体回退:用 Swing 逻辑字体 Dialog,JBR 在 Win 11 上会自动 fallback
        // 到 Segoe UI Emoji / CJK 字体,避免 emoji 显示为方块
        Font baseFont = console.getFont();
        if (baseFont != null) {
            console.setFont(new Font(Font.DIALOG, baseFont.getStyle(), baseFont.getSize()));
        }

        bindPopupMenu();
    }

    public static void updateUI() {
        SwingUtilities.invokeLater(() -> console.updateUI());
    }

    public static void renderText(List<TextRender> list) {
        for (TextRender textRender : list) {
            renderText(textRender.getText(), textRender.getStyle());
        }
    }

    public static void renderText(String text) {
        renderText(text, Style.DEFAULT);
    }

    public static void renderText(String text, Style style) {
        atomicExec(() -> {
            if (isNewLine) {
                ModeContext.getMode().renderTextBefore(text);
            }

            render(text, style.get());

            isNewLine = text.endsWith("\n");
        });
    }

    public static void showSimpleMsg(String msg) {
        renderText(msg + "\n", Style.DEFAULT);
    }

    public static void render(String content, AttributeSet attributeSet) {
        atomicExec(() -> {
            Document document = console.getDocument();
            try {
                document.insertString(document.getLength(), content, attributeSet);
                if (document.getLength() > 10000) {
                    document.remove(0, 2000);
                    document.insertString(0, "...", Style.DEFAULT.get());
                }
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
            gotoConsoleLow();
        });
    }

    public static void renderImageLabel(JLabel label) {
        atomicExec(() -> {
            renderText("[");
            renderComponent(label);
            renderText("]\n");
        });
    }

    public static void renderUrl(String title, String url) {
        JLabel label = new JLabel(title);
        label.setAlignmentY(0.85f);
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.setForeground(StyleConstants.getForeground(Style.DEFAULT.get()));
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                BrowserUtil.browse(url);
            }
        });
        renderComponent(label);
    }

    public static void renderComponent(Component component) {
        JScrollBar verticalScrollBar = consoleScroll.getVerticalScrollBar();
        int beforeScrollVal = verticalScrollBar.getValue();
        updateCaretPosition(-1);
        console.insertComponent(component);
        SwingUtilities.invokeLater(() -> verticalScrollBar.setValue(beforeScrollVal));
    }

    public static ChatMessageRef beginMessage(User user, UserMsgDTO body,
                                              RecallMessageDTO.ConversationType conversationType,
                                              String summary) {
        ChatMessageRef ref = new ChatMessageRef();
        ref.setUser(user);
        ref.setSummary(summary);
        ref.setConversationType(conversationType);
        if (body != null) {
            ref.setMessageId(body.getServerId());
            ref.setMsgType(body.getMsgType());
            ref.setCreatedAt(body.getServerCreatedAt() == null ? System.currentTimeMillis() : body.getServerCreatedAt());
        } else {
            ref.setCreatedAt(System.currentTimeMillis());
        }
        ref.setStartOffset(console.getDocument().getLength());
        return ref;
    }

    public static void endMessage(ChatMessageRef ref) {
        if (ref == null) {
            return;
        }
        ref.setEndOffset(console.getDocument().getLength());
        MESSAGE_REFS.add(ref);
    }

    public static void bindImageMessage(JLabel label, ChatMessageRef ref) {
        ref.setImageLabel(label);
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowMessagePopup(e, ref);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowMessagePopup(e, ref);
            }
        });
    }

    public static void clean() {
        console.setText("");
    }

    public static void setConsoleTitle(String title) {
        synchronized (panel) {
            ((TitledBorder) panel.getBorder()).setTitle(title);
            panel.updateUI();
        }
    }

    public static void gotoConsoleLow() {
        gotoConsoleLow(false);
    }

    public static void gotoConsoleLow(boolean forced) {
        if (!forced) {
            JScrollBar verticalScrollBar = consoleScroll.getVerticalScrollBar();
            if (verticalScrollBar.getValue() + 20 < verticalScrollBar.getMaximum() - verticalScrollBar.getHeight()) {
                return;
            }
        }

        updateCaretPosition(-1);
    }

    public static void showErrorMsg() {
        ConsoleAction.showSimpleMsg("输入的命令有误！帮助命令：" + Command.HELP.getCommand());
    }

    public static void showLoginMsg() {
        ConsoleAction.showSimpleMsg("请先登录！登录命令：" + Command.LOGIN.getCommand() + "，帮助命令：" + Command.HELP.getCommand());
    }

    private static void bindPopupMenu() {
        console.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowConsolePopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowConsolePopup(e);
            }
        });
    }

    private static void maybeShowConsolePopup(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        ChatMessageRef ref = findMessageRef(console.viewToModel(e.getPoint()));
        if (ref != null) {
            showMessagePopup(ref, console, e.getX(), e.getY());
            return;
        }
        buildDefaultPopup().show(console, e.getX(), e.getY());
    }

    private static void maybeShowMessagePopup(MouseEvent e, ChatMessageRef ref) {
        if (e.isPopupTrigger()) {
            showMessagePopup(ref, e.getComponent(), e.getX(), e.getY());
        }
    }

    private static ChatMessageRef findMessageRef(int offset) {
        for (int i = MESSAGE_REFS.size() - 1; i >= 0; i--) {
            ChatMessageRef ref = MESSAGE_REFS.get(i);
            if (offset >= ref.getStartOffset() && offset <= ref.getEndOffset()) {
                return ref;
            }
        }
        return null;
    }

    private static void showMessagePopup(ChatMessageRef ref, Component invoker, int x, int y) {
        selectMessage(ref);
        JPopupMenu popup = new JPopupMenu("消息菜单");
        JMenuItem quoteItem = new JMenuItem("引用此消息");
        quoteItem.setEnabled(ref.getMessageId() != null);
        quoteItem.addActionListener(e -> InputAction.quoteMessage(ref));
        popup.add(quoteItem);

        JMenuItem recallItem = new JMenuItem("撤回此消息");
        recallItem.setEnabled(ref.canRecall(DataCache.username));
        recallItem.addActionListener(e -> {
            int ok = JOptionPane.showConfirmDialog(panel,
                    "确定撤回这条消息吗？\n" + ref.getSummary(),
                    "撤回消息",
                    JOptionPane.OK_CANCEL_OPTION);
            if (ok != JOptionPane.OK_OPTION) {
                return;
            }
            RecallMessageDTO dto = new RecallMessageDTO();
            dto.setMessageId(ref.getMessageId());
            dto.setConversationType(ref.getConversationType());
            MessageAction.send(dto, Action.RECALL_MESSAGE);
        });
        popup.add(recallItem);
        popup.show(invoker, x, y);
    }

    private static void selectMessage(ChatMessageRef ref) {
        selectedMessageRef = ref;
        console.requestFocusInWindow();
        console.select(ref.getStartOffset(), ref.getEndOffset());
        for (ChatMessageRef item : MESSAGE_REFS) {
            if (item.getImageLabel() != null) {
                item.getImageLabel().setBorder(null);
            }
        }
        if (ref.getImageLabel() != null) {
            ref.getImageLabel().setBorder(BorderFactory.createLineBorder(new Color(52, 129, 235), 1));
        }
    }

    private static JPopupMenu buildDefaultPopup() {
        JPopupMenu jPopupMenu = new JPopupMenu("右键菜单");
        JMenuItem copyItem = new JMenuItem("复制内容");
        copyItem.addActionListener(ev -> {
            String selectedText = console.getSelectedText();
            if (StrUtil.isBlank(selectedText)) {
                return;
            }

            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            StringSelection contents = new StringSelection(selectedText);
            clipboard.setContents(contents, null);
        });

        JMenuItem searchItem = new JMenuItem("百度搜索");
        searchItem.addActionListener(ev -> {
            String selectedText = console.getSelectedText();
            if (StrUtil.isBlank(selectedText)) {
                return;
            }

            BrowserUtil.browse("https://www.baidu.com/s?wd=" + selectedText);
        });

        JMenuItem openUrlItem = new JMenuItem("打开网址");
        openUrlItem.addActionListener(ev -> {
            String selectedText = console.getSelectedText();
            if (StrUtil.isBlank(selectedText)) {
                return;
            }

            if (!selectedText.startsWith("http")) {
                selectedText = "https://" + selectedText;
            }

            BrowserUtil.browse(selectedText);
        });

        jPopupMenu.add(copyItem);
        jPopupMenu.add(searchItem);
        jPopupMenu.add(openUrlItem);
        jPopupMenu.addSeparator();

        Map<String, Command> commandMap = new LinkedHashMap<>();
        commandMap.put("快速登录", Command.LOGIN);
        commandMap.put("加入游戏", Command.JOIN);
        commandMap.put("清者自清", Command.CLEAN);
        commandMap.put("需要帮助！", Command.HELP);
        commandMap.put("退！退！退！", Command.LOGOUT);

        commandMap.forEach((k, v) -> jPopupMenu.add(k).addActionListener(l -> {
            ConsoleAction.showSimpleMsg(v.getCommand());
            v.exec();
        }));
        return jPopupMenu;
    }

    public static void showSystemMsg(String time, String msg) {
        ConsoleAction.renderText(String.format("[%s] 系统消息：%s\n", time, msg), Style.SYSTEM_MSG);
    }

    private static void updateCaretPosition(int position) {
        atomicExec(() -> {
            int pos = position;
            if (pos == -1) {
                pos = console.getDocument().getLength();
            }
            console.setCaretPosition(pos);
        });
    }

    public static class WarpEditorKit extends StyledEditorKit {

        private ViewFactory defaultFactory = new WarpColumnFactory();

        @Override
        public ViewFactory getViewFactory() {
            return defaultFactory;
        }

        private class WarpColumnFactory implements ViewFactory {

            public View create(Element elem) {
                String kind = elem.getName();
                if (kind != null) {
                    switch (kind) {
                        case AbstractDocument.ContentElementName:
                            return new WarpLabelView(elem);
                        case AbstractDocument.ParagraphElementName:
                            return new ParagraphView(elem);
                        case AbstractDocument.SectionElementName:
                            return new BoxView(elem, View.Y_AXIS);
                        case StyleConstants.ComponentElementName:
                            return new ComponentView(elem);
                        case StyleConstants.IconElementName:
                            return new IconView(elem);
                    }
                }

                return new LabelView(elem);
            }
        }

        private class WarpLabelView extends LabelView {

            public WarpLabelView(Element elem) {
                super(elem);
            }

            @Override
            public float getMinimumSpan(int axis) {
                switch (axis) {
                    case View.X_AXIS:
                        return 0;
                    case View.Y_AXIS:
                        return super.getMinimumSpan(axis);
                    default:
                        throw new IllegalArgumentException("Invalid axis: " + axis);
                }
            }
        }

    }

    public static void atomicExec(Runnable runnable) {
        synchronized (LOCK) {
            runnable.run();
        }
    }

}
