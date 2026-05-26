package cn.xeblog.plugin.ui;

import cn.xeblog.plugin.action.LoginService;
import cn.xeblog.plugin.persistence.PersistenceData;
import cn.xeblog.plugin.persistence.PersistenceService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import io.netty.channel.Channel;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/**
 * 登录页(替代命令行 #login)。
 *
 * <p>4 个输入框 + 2 个按钮:host / port / 账号 / 密码 + 「登录」「游客进入」。
 * 启动时若 PersistenceData.token 不为空,会由 MainWindowFactory 触发静默 token 自动登录,
 * 期间本面板的输入禁用 + 显示 loading 文案。</p>
 *
 * <p>登录成功(LoginResultMessageHandler 发出 ONLINE 后)由外部调用 {@link #onLoginSucceeded()}
 * 切换到主界面;登录失败/连接失败由外部调用 {@link #showError(String)}。</p>
 *
 * @author dz
 * @date 2026/5/26
 */
public class LoginPanel extends JPanel {

    private final JBTextField hostField = new JBTextField();
    private final JBTextField portField = new JBTextField();
    private final JBTextField accountField = new JBTextField();
    private final JBPasswordField passwordField = new JBPasswordField();
    private final JButton loginBtn = new JButton("登录");
    private final JButton guestBtn = new JButton("游客进入");
    private final JBLabel statusLabel = new JBLabel(" ");

    /**
     * 是否正在等待登录回包。
     * doLogin/enterTokenAutoLogin 设 true,登录成功/失败后清 false。
     * SystemMessageHandler / channelInactive 用它判断要不要把消息显示到登录页。
     */
    private volatile boolean awaitingLogin;

    public LoginPanel() {
        setLayout(new BorderLayout());
        setBorder(JBUI.Borders.empty(24, 32));

        add(buildForm(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        // 默认值从持久化读
        PersistenceData pd = PersistenceService.getData();
        if (pd.getHost() != null) {
            hostField.setText(pd.getHost());
        }
        if (pd.getPort() > 0) {
            portField.setText(String.valueOf(pd.getPort()));
        }
        if (pd.getAccount() != null) {
            accountField.setText(pd.getAccount());
        }

        loginBtn.addActionListener(e -> doLogin(false));
        guestBtn.addActionListener(e -> doLogin(true));
    }

    private JComponent buildForm() {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = JBUI.insets(6, 4);

        // host + port 同一行
        JPanel hostRow = new JPanel(new BorderLayout(8, 0));
        hostField.getEmptyText().setText("服务器 host");
        portField.getEmptyText().setText("端口");
        portField.setPreferredSize(new Dimension(80, portField.getPreferredSize().height));
        hostRow.add(hostField, BorderLayout.CENTER);
        hostRow.add(portField, BorderLayout.EAST);

        accountField.getEmptyText().setText("账号(留空走游客模式只填昵称)");
        passwordField.getEmptyText().setText("密码(游客无需填写)");

        c.gridx = 0; c.gridy = 0; c.weightx = 1.0;
        form.add(label("服务器"), c);
        c.gridy = 1; form.add(hostRow, c);
        c.gridy = 2; form.add(label("账号 / 昵称"), c);
        c.gridy = 3; form.add(accountField, c);
        c.gridy = 4; form.add(label("密码"), c);
        c.gridy = 5; form.add(passwordField, c);

        // 按钮行
        JPanel btnRow = new JPanel();
        btnRow.setLayout(new BoxLayout(btnRow, BoxLayout.X_AXIS));
        btnRow.add(loginBtn);
        btnRow.add(Box.createHorizontalStrut(8));
        btnRow.add(guestBtn);
        c.gridy = 6; c.insets = JBUI.insets(16, 4, 4, 4);
        form.add(btnRow, c);

        return form;
    }

    private JComponent buildFooter() {
        JPanel p = new JPanel(new BorderLayout());
        statusLabel.setForeground(JBColor.GRAY);
        p.add(statusLabel, BorderLayout.CENTER);
        return p;
    }

    private JBLabel label(String text) {
        JBLabel l = new JBLabel(text);
        l.setForeground(JBColor.GRAY);
        return l;
    }

    private void doLogin(boolean guest) {
        awaitingLogin = true;
        statusLabel.setForeground(JBColor.GRAY);
        statusLabel.setText("正在校验...");
        String host = hostField.getText().trim();
        int port = parsePort(portField.getText().trim());
        String account = accountField.getText().trim();
        String password = new String(passwordField.getPassword());

        LoginService.Callback cb = new LoginService.Callback() {
            @Override
            public void onConnecting() {
                runOnEdt(() -> {
                    statusLabel.setForeground(JBColor.GRAY);
                    statusLabel.setText("正在连接服务器...");
                    setInputsEnabled(false);
                });
            }

            @Override
            public void onConnected(Channel channel) {
                // 连接成功只是 TCP 通了,真正登录回包由 LoginResultMessageHandler 处理。
                // 这里保持 loading,直到 onLoginSucceeded() 被外部触发或收到错误。
                runOnEdt(() -> statusLabel.setText("连接成功,等待登录响应..."));
            }

            @Override
            public void onFailed(String reason) {
                runOnEdt(() -> showError(reason));
            }
        };

        if (guest) {
            // 游客优先用账号字段当昵称(允许用户在一个输入框完成两种模式)
            String name = account.isEmpty() ? "" : account;
            LoginService.loginAsGuest(name, host, port, cb);
        } else {
            LoginService.loginByPassword(account, password, host, port, cb);
        }
    }

    /** SystemMessageHandler / XEChatClientHandler 用来判断要不要把"登录阶段"消息路由到登录页 */
    public boolean isAwaitingLogin() {
        return awaitingLogin;
    }

    private int parsePort(String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** 登录回包到达 + ONLINE 状态切换后由外部调用,清空敏感字段。 */
    public void onLoginSucceeded() {
        awaitingLogin = false;
        runOnEdt(() -> {
            passwordField.setText("");
            statusLabel.setForeground(JBColor.GRAY);
            statusLabel.setText(" ");
            setInputsEnabled(true);
        });
    }

    /** 登录/连接出错由外部调用,显示提示并恢复输入。 */
    public void showError(String reason) {
        awaitingLogin = false;
        runOnEdt(() -> {
            statusLabel.setForeground(JBColor.RED);
            statusLabel.setText(reason == null ? "登录失败" : reason);
            setInputsEnabled(true);
        });
    }

    /** token 自动登录开始时由外部调用:禁用输入并提示。 */
    public void enterTokenAutoLogin(String account) {
        awaitingLogin = true;
        runOnEdt(() -> {
            statusLabel.setForeground(JBColor.GRAY);
            statusLabel.setText("正在用上次的登录态自动连接 (" + account + ") ...");
            setInputsEnabled(false);
        });
    }

    private void setInputsEnabled(boolean enabled) {
        hostField.setEnabled(enabled);
        portField.setEnabled(enabled);
        accountField.setEnabled(enabled);
        passwordField.setEnabled(enabled);
        loginBtn.setEnabled(enabled);
        guestBtn.setEnabled(enabled);
    }

    private static void runOnEdt(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            ApplicationManager.getApplication().invokeLater(r);
        }
    }

}
