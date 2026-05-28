package cn.xeblog.plugin.listener;

import cn.xeblog.plugin.util.DevFileLogger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

public class DevFileLogStartupActivity implements StartupActivity, DumbAware {

    @Override
    public void runActivity(@NotNull Project project) {
        DevFileLogger.installIfEnabled();
    }
}
