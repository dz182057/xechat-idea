package cn.xeblog.plugin.ui;

import cn.hutool.core.collection.CollUtil;
import cn.xeblog.commons.util.ClassUtils;
import cn.xeblog.plugin.enums.Command;
import cn.xeblog.plugin.listener.MainWindowInitializedEventListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.PathUtil;

import javax.swing.*;
import java.awt.CardLayout;
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
    private CardLayout cardLayout;
    private LoginPanel loginPanel;

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

        Command.HELP.exec();
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
        runOnEdt(() -> cardLayout.show(wrapperPanel, CARD_LOGIN));
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
