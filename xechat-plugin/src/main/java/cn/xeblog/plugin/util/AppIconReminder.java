package cn.xeblog.plugin.util;

import cn.xeblog.plugin.cache.DataCache;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 应用任务栏图标提醒。
 */
public class AppIconReminder {

    private static final Map<JFrame, WindowAdapter> CLEAR_LISTENERS = Collections.synchronizedMap(new WeakHashMap<>());

    public static void remind() {
        Project project = DataCache.project;
        if (project == null || project.isDisposed()) {
            return;
        }

        ApplicationManager.getApplication().invokeLater(() -> {
            if (!project.isDisposed()) {
                showWindowsTaskbarProgress(WindowManager.getInstance().getFrame(project));
            }
        });
    }

    public static void clear() {
        Project project = DataCache.project;
        if (project == null || project.isDisposed()) {
            return;
        }

        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) {
                return;
            }

            hideWindowsTaskbarProgress(WindowManager.getInstance().getFrame(project));
        });
    }

    private static void showWindowsTaskbarProgress(JFrame frame) {
        if (frame == null || frame.isActive()) {
            return;
        }

        try {
            Class<?> taskBarClass = Class.forName("com.intellij.ui.Win7TaskBar");
            Method setProgressMethod = taskBarClass.getDeclaredMethod("setProgress", JFrame.class, double.class, boolean.class);
            setProgressMethod.setAccessible(true);
            setProgressMethod.invoke(null, frame, 1.0d, false);
            installClearListener(frame);
        } catch (Throwable e) {
            System.err.println("XEChat 任务栏提醒失败：" + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void hideWindowsTaskbarProgress(JFrame frame) {
        if (frame == null) {
            return;
        }

        try {
            uninstallClearListener(frame);
            Class<?> taskBarClass = Class.forName("com.intellij.ui.Win7TaskBar");
            Method hideProgressMethod = taskBarClass.getDeclaredMethod("hideProgress", JFrame.class);
            hideProgressMethod.setAccessible(true);
            hideProgressMethod.invoke(null, frame);
        } catch (Throwable e) {
            System.err.println("XEChat 任务栏提醒清理失败：" + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void installClearListener(JFrame frame) {
        if (CLEAR_LISTENERS.containsKey(frame)) {
            return;
        }

        WindowAdapter listener = new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent e) {
                hideWindowsTaskbarProgress(frame);
            }

            @Override
            public void windowDeiconified(WindowEvent e) {
                hideWindowsTaskbarProgress(frame);
            }

            @Override
            public void windowStateChanged(WindowEvent e) {
                if ((e.getNewState() & JFrame.ICONIFIED) == 0) {
                    hideWindowsTaskbarProgress(frame);
                }
            }
        };
        CLEAR_LISTENERS.put(frame, listener);
        frame.addWindowListener(listener);
        frame.addWindowStateListener(listener);
    }

    private static void uninstallClearListener(JFrame frame) {
        WindowAdapter listener = CLEAR_LISTENERS.remove(frame);
        if (listener == null) {
            return;
        }

        frame.removeWindowListener(listener);
        frame.removeWindowStateListener(listener);
    }

}
