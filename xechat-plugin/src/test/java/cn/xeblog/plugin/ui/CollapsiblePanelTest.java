package cn.xeblog.plugin.ui;

import org.junit.Assert;
import org.junit.Test;

import javax.swing.JPanel;
import java.awt.BorderLayout;

public class CollapsiblePanelTest {

    @Test
    public void toggleShouldOnlyHideOwnContent() {
        JPanel chatContent = new JPanel();
        JPanel gameContent = new JPanel();

        CollapsiblePanel chatPanel = new CollapsiblePanel("聊天", chatContent);
        CollapsiblePanel gamePanel = new CollapsiblePanel("游戏", gameContent);
        JPanel parent = new JPanel(new BorderLayout());
        parent.add(chatPanel, BorderLayout.CENTER);
        parent.add(gamePanel, BorderLayout.EAST);

        chatPanel.toggleCollapsed();

        Assert.assertTrue("收起聊天区时游戏区仍应显示", gameContent.isVisible());
        Assert.assertFalse("收起聊天区时只隐藏聊天内容", chatContent.isVisible());

        gamePanel.toggleCollapsed();

        Assert.assertFalse("收起游戏区时游戏内容应隐藏", gameContent.isVisible());
        Assert.assertFalse("游戏区收起不应恢复聊天内容", chatContent.isVisible());

        chatPanel.toggleCollapsed();

        Assert.assertTrue("再次点击应恢复聊天内容", chatContent.isVisible());
        Assert.assertFalse("恢复聊天区不应影响游戏区", gameContent.isVisible());
    }

    @Test
    public void collapsedPanelShouldHideWholePanel() {
        JPanel content = new JPanel();
        CollapsiblePanel panel = new CollapsiblePanel("聊天", content);

        panel.toggleCollapsed();

        Assert.assertFalse("内容应隐藏", content.isVisible());
        Assert.assertFalse("收起后外层面板不应继续占用内容区", panel.isVisible());
    }

    @Test
    public void collapsedPanelShouldIgnoreContentVisibilityChangedOutside() {
        JPanel content = new JPanel();
        CollapsiblePanel panel = new CollapsiblePanel("游戏", content);

        panel.toggleCollapsed();
        content.setVisible(true);

        Assert.assertFalse("外部代码重新显示内容时,收起状态仍应保持", panel.isVisible());
    }
}
