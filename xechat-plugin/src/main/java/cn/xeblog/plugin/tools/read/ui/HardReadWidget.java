package cn.xeblog.plugin.tools.read.ui;

import cn.xeblog.plugin.cache.DataCache;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * @author LYF
 * @date 2022-07-27
 */
public class HardReadWidget implements StatusBarWidget.TextPresentation, StatusBarWidget {
    private static final String ID = HardReadWidget.class.getName();
    private StatusBar myStatusBar;
    private String line = "";

    public static HardReadWidget create() {
        return new HardReadWidget();
    }

    @Override
    public @NonNls @NotNull String ID() {
        return ID;
    }

    @Override
    public @Nullable TextPresentation getPresentation() {
        return this;
    }

    public void register() {
        line = "";
        StatusBar statusBar = WindowManager.getInstance().getStatusBar(DataCache.project);
        if (statusBar != null) {
            // 2026.1 起 IdeStatusBarImpl.addWidgetToLeft 已被移除，改用 StatusBar.addWidget(anchor, disposable)
            statusBar.addWidget(this, "before Position", DataCache.project);
        }
    }

    @Override
    public void install(@NotNull StatusBar statusBar) {
        myStatusBar = statusBar;
        // 2026.1 起 StatusBar 不再实现 Disposable，widget 的释放由 IDE 通过 dispose() 触发
    }

    @Override
    public void dispose() {
        if (isInstalled()) {
            myStatusBar.removeWidget(ID);
            line = null;
            myStatusBar = null;
        }
    }

    public boolean isInstalled() {
        return line != null && myStatusBar != null;
    }

    @Override
    public @NotNull String getText() {
        return line;
    }

    public void setText(String txt) {
        line = txt;
        myStatusBar.updateWidget(ID);
    }

    @Override
    public float getAlignment() {
        return Component.LEFT_ALIGNMENT;
    }

    @Override
    public @Nullable String getTooltipText() {
        return null;
    }

    @Override
    public @Nullable Consumer<MouseEvent> getClickConsumer() {
        return null;
    }
}
