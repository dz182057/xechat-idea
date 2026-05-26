package cn.xeblog.plugin.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.ScrollPaneConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.function.Consumer;

/**
 * 插件端 emoji 选择面板。
 *
 * <p>表情列表与桌面端 src/renderer/src/components/chat/EmojiPicker.tsx 保持一致,
 * 双端可见到同一套字符,无需协议改动:emoji 直接以 Unicode 字符嵌入 TEXT 消息内容。</p>
 *
 * <p>字体使用 Windows 11 自带 "Segoe UI Emoji",其他平台缺失时 Swing 会回退到系统默认,
 * 此时部分 emoji 会显示为方块,属字体问题非协议问题。</p>
 *
 * @author dz
 * @date 2026/5/26
 */
public final class EmojiPicker {

    private static final String[] GROUP_NAMES = {"表情", "手势", "符号"};

    private static final String[][] GROUPS = new String[][]{
            // 表情(与桌面端 EmojiPicker.tsx 的 "表情" 组逐字符对齐)
            {
                    "😀", "😁", "😂", "🤣", "😃", "😄", "😅", "😆",
                    "😉", "😊", "😋", "😎", "😍", "😘", "🥰", "😗",
                    "🙂", "🤗", "🤩", "🤔", "🤨", "😐", "😑", "😶",
                    "🙄", "😏", "😣", "😥", "😮", "🤐", "😯", "😪",
                    "😫", "😴", "😌", "😛", "😜", "😝", "🤤", "😒",
                    "😓", "😔", "😕", "🙃", "🤑", "😲", "☹️", "🙁",
                    "😖", "😞", "😟", "😤", "😢", "😭", "😦", "😧",
                    "😨", "😩", "🤯", "😬", "😰", "😱", "😳", "🤪",
                    "😵", "😡", "😠", "🤬", "😷", "🤒", "🤕", "🤢",
                    "🤮", "🤧", "😇", "🥳", "🥺", "🤠", "🤡", "🤥"
            },
            // 手势
            {
                    "👍", "👎", "👌", "✌️", "🤞", "🤟", "🤘", "🤙",
                    "👈", "👉", "👆", "👇", "☝️", "✋", "🤚", "🖐️",
                    "🖖", "👋", "🤝", "🙏", "💪", "🦾", "🤳", "💅"
            },
            // 符号
            {
                    "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍",
                    "🤎", "💔", "💕", "💞", "💓", "💗", "💖", "💘",
                    "💝", "💟", "🔥", "⭐", "🌟", "✨", "⚡", "💥",
                    "💯", "✅", "❌", "❓", "❗", "💤", "💢", "💦"
            }
    };

    private EmojiPicker() {
    }

    /**
     * 创建一个可复用的 emoji 选择 popup。点击 emoji 立即触发回调,popup 不自动关闭(支持连续插入);
     * 点击外部或失焦时 JPopupMenu 自身会关闭。
     */
    public static JPopupMenu create(Consumer<String> onPick) {
        JPopupMenu popup = new JPopupMenu();
        popup.setLayout(new BorderLayout());
        popup.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

        Font emojiFont = new Font("Segoe UI Emoji", Font.PLAIN, 16);

        // 单个 emoji 按钮固定 28x28,8 列 + 1px gap ≈ 240px,加 scrollbar + padding 留 ~290px 宽
        final int cellSize = 28;
        final int cols = 8;

        JTabbedPane tabs = new JTabbedPane();
        for (int i = 0; i < GROUPS.length; i++) {
            JPanel grid = new JPanel(new GridLayout(0, cols, 1, 1));
            grid.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
            for (String emoji : GROUPS[i]) {
                JButton btn = new JButton(emoji);
                btn.setFont(emojiFont);
                btn.setMargin(new Insets(0, 0, 0, 0));
                btn.setBorderPainted(false);
                btn.setContentAreaFilled(false);
                btn.setFocusable(false);
                btn.setToolTipText(emoji);
                Dimension cell = new Dimension(cellSize, cellSize);
                btn.setPreferredSize(cell);
                btn.setMinimumSize(cell);
                btn.setMaximumSize(cell);
                btn.addActionListener(ev -> onPick.accept(emoji));
                grid.add(btn);
            }
            JScrollPane sp = new JScrollPane(grid);
            sp.setBorder(null);
            sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            sp.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
            tabs.addTab(GROUP_NAMES[i], sp);
        }

        popup.add(tabs, BorderLayout.CENTER);
        // 宽度 = cols * cellSize + gap*(cols-1) + grid padding(8) + scrollbar(~16) + popup padding(4)
        int width = cols * cellSize + (cols - 1) + 8 + 18 + 4;
        popup.setPreferredSize(new Dimension(width, 240));
        return popup;
    }

}
