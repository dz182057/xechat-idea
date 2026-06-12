package cn.xeblog.plugin.action;

import cn.xeblog.commons.entity.FriendDTO;
import cn.xeblog.plugin.cache.DataCache;
import com.intellij.ui.components.JBList;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;

public class InputActionTest {

    @After
    public void tearDown() throws Exception {
        setStaticField("contentArea", null);
        setStaticField("leftTopPanel", null);
        setStaticField("completionContainer", null);
        setStaticField("jbList", null);
        DataCache.guestMode = false;
        DataCache.stickyPrivateTarget = null;
        DataCache.userMap.clear();
        DataCache.friendMap.clear();
    }

    @Test
    public void cleanShouldClearCompletionPanel() throws Exception {
        JTextArea contentArea = new JTextArea("#exit");
        JPanel leftTopPanel = new JPanel(new BorderLayout());
        JPanel completionContainer = new JPanel(new BorderLayout());
        completionContainer.add(new JLabel("#exit (退出)"), BorderLayout.CENTER);
        leftTopPanel.add(completionContainer, BorderLayout.CENTER);
        leftTopPanel.setVisible(true);

        setStaticField("contentArea", contentArea);
        setStaticField("leftTopPanel", leftTopPanel);
        setStaticField("completionContainer", completionContainer);
        setStaticField("jbList", new JBList<>());

        InputAction.clean();

        Assert.assertEquals("", contentArea.getText());
        Assert.assertFalse("清空输入框时应同步隐藏补全提示框", leftTopPanel.isVisible());
        Assert.assertNull("清空输入框时应清掉补全列表引用", getStaticField("jbList"));
    }

    @Test
    public void getStickyImageToUsersShouldAllowOfflineFriend() throws Exception {
        DataCache.stickyPrivateTarget = "こまつ";
        DataCache.friendMap.put("こまつ",
                new FriendDTO(1L, "koma", "こまつ", 0, false, Collections.emptySet()));

        String[] toUsers = invokeGetStickyImageToUsers();

        Assert.assertArrayEquals(new String[]{"こまつ"}, toUsers);
        Assert.assertEquals("こまつ", DataCache.stickyPrivateTarget);
    }

    private static void setStaticField(String name, Object value) throws Exception {
        Field field = InputAction.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static Object getStaticField(String name) throws Exception {
        Field field = InputAction.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static String[] invokeGetStickyImageToUsers() throws Exception {
        Method method = InputAction.class.getDeclaredMethod("getStickyImageToUsers");
        method.setAccessible(true);
        return (String[]) method.invoke(null);
    }
}
