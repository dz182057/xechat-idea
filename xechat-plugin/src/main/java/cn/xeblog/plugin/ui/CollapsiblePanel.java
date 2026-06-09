package cn.xeblog.plugin.ui;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;

public class CollapsiblePanel extends JPanel {

    private final JComponent content;
    private Runnable toggleListener;
    private boolean collapsed;

    public CollapsiblePanel(String title, JComponent content) {
        this(title, content, null);
    }

    public CollapsiblePanel(String title, JComponent content, Runnable toggleListener) {
        super(new BorderLayout());
        this.content = content;
        this.toggleListener = toggleListener;

        add(content, BorderLayout.CENTER);
    }

    public void toggleCollapsed() {
        setCollapsed(!collapsed);
    }

    public void setCollapsed(boolean collapsed) {
        if (this.collapsed == collapsed) {
            return;
        }

        this.collapsed = collapsed;
        content.setVisible(!collapsed);
        setVisible(!collapsed);
        revalidate();
        repaint();

        if (toggleListener != null) {
            toggleListener.run();
        }
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    public void setToggleListener(Runnable toggleListener) {
        this.toggleListener = toggleListener;
    }
}
