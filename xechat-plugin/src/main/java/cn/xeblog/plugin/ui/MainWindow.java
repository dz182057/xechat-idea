package cn.xeblog.plugin.ui;

import cn.hutool.core.collection.CollUtil;
import cn.xeblog.commons.util.ClassUtils;
import cn.xeblog.plugin.listener.MainWindowInitializedEventListener;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author anlingyi
 * @date 2020/5/26
 */
public class MainWindow {
    private JPanel mainPanel;
    private JTextPane console;
    private JTextArea contentArea;
    private JPanel leftPanel;
    private JPanel rightPanel;
    private JPanel contentPanel;
    private JScrollPane consoleScroll;
    private JPanel leftTopPanel;

    /** ToolWindow 真正显示的顶层 panel,内部用 CardLayout 切换 LOGIN / MAIN */
    private JPanel wrapperPanel;
    private JPanel panelContent;
    private CardLayout cardLayout;
    private LoginPanel loginPanel;
    private ToolWindow toolWindow;
    private CollapsiblePanel chatPanel;
    private CollapsiblePanel gamePanel;
    private boolean rightPanelAvailable;

    private static final String CARD_LOGIN = "LOGIN";
    private static final String CARD_MAIN = "MAIN";

    private static final MainWindow MAIN_WINDOW;

    static {
        MAIN_WINDOW = new MainWindow();
        MAIN_WINDOW.afterInit();
    }

    private MainWindow() {

    }

    private void afterInit() {
        installCollapsiblePanels();

        // 组装 CardLayout 外层 panel:把原 .form 构造好的 mainPanel 作为 MAIN 卡,新建 LoginPanel 作为 LOGIN 卡
        cardLayout = new CardLayout();
        wrapperPanel = new JPanel(cardLayout);
        loginPanel = new LoginPanel();
        wrapperPanel.add(loginPanel, CARD_LOGIN);
        wrapperPanel.add(mainPanel, CARD_MAIN);
        // 默认起始卡:LOGIN(MainWindowFactory 会按 token 决定是否触发静默自动登录)
        cardLayout.show(wrapperPanel, CARD_LOGIN);

        Set<Class<?>> initClasses = ClassUtils.scanSubClass(PathUtil.getJarPathForClass(MainWindow.class), null,
                MainWindowInitializedEventListener.class);

        if (CollUtil.isNotEmpty(initClasses)) {
            try {
                for (Class<?> initClass : initClasses) {
                    MainWindowInitializedEventListener obj = (MainWindowInitializedEventListener) initClass
                            .getDeclaredConstructor().newInstance();
                    obj.afterInit(this);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void installCollapsiblePanels() {
        mainPanel.remove(leftPanel);
        mainPanel.remove(rightPanel);
        mainPanel.setLayout(new BorderLayout());

        chatPanel = new CollapsiblePanel("聊天", leftPanel);
        gamePanel = new CollapsiblePanel("游戏", rightPanel);
        panelContent = new JPanel(new BorderLayout());
        chatPanel.setToggleListener(this::refreshPanels);
        gamePanel.setToggleListener(this::refreshPanels);

        mainPanel.add(panelContent, BorderLayout.CENTER);
        refreshPanels();
    }

    private void refreshPanels() {
        panelContent.removeAll();

        boolean showChat = !chatPanel.isCollapsed();
        boolean showGame = rightPanelAvailable && !gamePanel.isCollapsed();
        if (showChat && showGame) {
            JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, chatPanel, gamePanel);
            initSplitPane(splitPane);
            panelContent.add(splitPane, BorderLayout.CENTER);
        } else if (showChat) {
            panelContent.add(chatPanel, BorderLayout.CENTER);
        } else if (showGame) {
            panelContent.add(gamePanel, BorderLayout.CENTER);
        }

        refreshTitleActions();
        mainPanel.revalidate();
        mainPanel.repaint();
    }

    private void initSplitPane(JSplitPane splitPane) {
        splitPane.setBorder(null);
        splitPane.setResizeWeight(0.5);
        splitPane.setContinuousLayout(true);
    }

    public void configureToolWindow(ToolWindow toolWindow) {
        this.toolWindow = toolWindow;
        toolWindow.setTitle("XE");
        toolWindow.setStripeTitle("XE");
        refreshTitleActions();
    }

    public void setRightPanelAvailable(boolean available) {
        this.rightPanelAvailable = available;
        if (!available && gamePanel.isCollapsed()) {
            gamePanel.setCollapsed(false);
            rightPanel.setVisible(false);
        }
        refreshPanels();
    }

    private void refreshTitleActions() {
        if (toolWindow == null) {
            return;
        }
        List<AnAction> actions = new ArrayList<>();
        if (chatPanel.isCollapsed()) {
            actions.add(new PanelTitleAction("C", "恢复聊天", () -> chatPanel.setCollapsed(false)));
        } else {
            actions.add(new PanelTitleAction("<", "收起聊天", () -> chatPanel.setCollapsed(true)));
        }
        if (rightPanelAvailable) {
            if (gamePanel.isCollapsed()) {
                actions.add(new PanelTitleAction("G", "恢复游戏", () -> gamePanel.setCollapsed(false)));
            } else {
                actions.add(new PanelTitleAction(">", "收起游戏", () -> gamePanel.setCollapsed(true)));
            }
        }
        toolWindow.setTitleActions(actions);
    }

    private static class PanelTitleAction extends DumbAwareAction {

        private final Runnable action;

        PanelTitleAction(String text, String description, Runnable action) {
            super(text, description, new LetterIcon(text));
            this.action = action;
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            action.run();
        }
    }

    private static class LetterIcon implements Icon {

        private static final int SIZE = 18;

        private final String text;

        LetterIcon(String text) {
            this.text = text;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Color oldColor = g.getColor();
            Font oldFont = g.getFont();
            Font font = oldFont.deriveFont(Font.BOLD, 14f);
            g.setFont(font);
            g.setColor(c == null ? Color.DARK_GRAY : c.getForeground());
            FontMetrics metrics = g.getFontMetrics(font);
            int textX = x + (SIZE - metrics.stringWidth(text)) / 2;
            int textY = y + (SIZE - metrics.getHeight()) / 2 + metrics.getAscent();
            g.drawString(text, textX, textY);
            g.setFont(oldFont);
            g.setColor(oldColor);
        }

        @Override
        public int getIconWidth() {
            return SIZE;
        }

        @Override
        public int getIconHeight() {
            return SIZE;
        }
    }

    public static MainWindow getInstance() {
        return MAIN_WINDOW;
    }

    /** ToolWindow 内容入口:替代原来直接用 mainPanel */
    public JPanel getWrapperPanel() {
        return wrapperPanel;
    }

    public LoginPanel getLoginPanel() {
        return loginPanel;
    }

    /** 切到登录卡(登出/token 自动登录失败时调) */
    public void switchToLogin() {
        // 离线必清:粘性私聊目标只在登录态有效,残留会让下次重登的 banner / 发送路径错乱
        cn.xeblog.plugin.cache.DataCache.stickyPrivateTarget = null;
        runOnEdt(() -> {
            cardLayout.show(wrapperPanel, CARD_LOGIN);
            cn.xeblog.plugin.action.InputAction.hidePrivateBanner();
        });
    }

    /** 切到主界面卡(登录成功 ONLINE 时调) */
    public void switchToMain() {
        runOnEdt(() -> cardLayout.show(wrapperPanel, CARD_MAIN));
    }

    private static void runOnEdt(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            ApplicationManager.getApplication().invokeLater(r);
        }
    }

    public JPanel getMainPanel() {
        return mainPanel;
    }

    public JPanel getRightPanel() {
        return rightPanel;
    }

    public JTextArea getContentArea() {
        return contentArea;
    }

    public JPanel getLeftTopPanel() {
        return leftTopPanel;
    }

    public JTextPane getConsoleTextPane() {
        return console;
    }

    public JPanel getLeftPanel() {
        return leftPanel;
    }

    public JScrollPane getConsoleScrollPane() {
        return consoleScroll;
    }

}
