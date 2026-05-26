package cn.xeblog.plugin.factory;

import cn.hutool.core.thread.GlobalThreadPool;
import cn.xeblog.commons.util.ThreadUtils;
import cn.xeblog.plugin.action.InputAction;
import cn.xeblog.plugin.action.LoginService;
import cn.xeblog.plugin.cache.DataCache;
import cn.xeblog.plugin.persistence.PersistenceData;
import cn.xeblog.plugin.persistence.PersistenceService;
import cn.xeblog.plugin.ui.LoginPanel;
import cn.xeblog.plugin.ui.MainWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.AncestorListenerAdapter;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import io.netty.channel.Channel;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.AncestorEvent;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author anlingyi
 * @date 2020/5/26
 */
public class MainWindowFactory implements ToolWindowFactory {

    /** 全局只触发一次 token 自动登录,避免多 Project 同时打开重复尝试 */
    private static final AtomicBoolean TOKEN_BOOTSTRAP_TRIED = new AtomicBoolean(false);

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        DataCache.project = project;

        MainWindow mw = MainWindow.getInstance();
        JPanel wrapper = mw.getWrapperPanel();
        JPanel mainPanel = mw.getMainPanel();
        mainPanel.addAncestorListener(new AncestorListenerAdapter() {
            @Override
            public void ancestorAdded(AncestorEvent event) {
                GlobalThreadPool.execute(() -> {
                    ThreadUtils.spinMoment(800);
                    InputAction.restCursor();
                });
            }
        });

        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(wrapper, "", false);
        toolWindow.getContentManager().addContent(content);

        // 启动时若本地有 token + account + host:port → 静默触发 token 自动登录
        // 登录回包到达后由 LoginResultMessageHandler 统一切到 MAIN 卡;失败则留在 LOGIN
        tryTokenBootstrap(mw.getLoginPanel());
    }

    private static void tryTokenBootstrap(LoginPanel loginPanel) {
        if (!TOKEN_BOOTSTRAP_TRIED.compareAndSet(false, true)) {
            return;
        }
        PersistenceData pd = PersistenceService.getData();
        if (StringUtils.isBlank(pd.getToken()) || StringUtils.isBlank(pd.getAccount())) {
            return;
        }
        if (StringUtils.isBlank(pd.getHost()) || pd.getPort() <= 0) {
            return; // 不知道连哪台服务器
        }

        loginPanel.enterTokenAutoLogin(pd.getAccount());
        LoginService.loginByToken(pd.getHost(), pd.getPort(), new LoginService.Callback() {
            @Override
            public void onConnecting() {
                // LoginPanel.enterTokenAutoLogin 已经设了文案,这里不重复刷
            }

            @Override
            public void onConnected(Channel channel) {
                // 等 LoginResult 回包,LoginResultMessageHandler 负责切 MAIN 卡
            }

            @Override
            public void onFailed(String reason) {
                loginPanel.showError("自动登录失败: " + reason + ",请用账号密码重新登录");
            }
        });
    }

}
