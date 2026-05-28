package cn.xeblog.plugin.game.drawguess;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.drawguess.DrawGuessDTO;
import cn.xeblog.commons.entity.game.drawguess.DrawGuessWordDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.Game;
import cn.xeblog.plugin.action.GameAction;
import cn.xeblog.plugin.action.MessageAction;
import cn.xeblog.plugin.annotation.DoGame;
import cn.xeblog.plugin.cache.DataCache;
import cn.xeblog.plugin.game.AbstractGame;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 你画我猜。
 */
@DoGame(Game.DRAW_GUESS)
public class DrawGuess extends AbstractGame<DrawGuessDTO> {

    private static final int VIEW_WIDTH = 640;
    private static final int VIEW_HEIGHT = 420;
    private static final CanvasSize DEFAULT_CANVAS_SIZE = CanvasSize.SMALL;

    private JPanel mainPanel;
    private DrawCanvas canvas;
    private JLabel tipsLabel;
    private JTextArea guessLog;
    private JTextField wordField;
    private JTextField guessField;
    private JButton startButton;
    private JButton randomWordButton;
    private JButton clearButton;
    private JButton guessButton;
    private JComboBox<String> colorBox;
    private JComboBox<CanvasSize> canvasSizeBox;
    private JSpinner sizeSpinner;

    private final List<DrawGuessDTO.Line> lines = new ArrayList<>();
    private String currentWord = "";
    private String currentHint = "";
    private String drawerName;
    private boolean playing;
    private boolean drawer;
    private boolean randomWordLoading;
    private Point lastPoint;
    private Point resizeStartPoint;
    private Dimension resizeStartSize;
    private boolean resizingCanvas;

    @Override
    public void handle(DrawGuessDTO body) {
        if (body == null || body.getEvent() == null) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            switch (body.getEvent()) {
                case START_ROUND:
                    onStartRound(body);
                    break;
                case DRAW:
                    if (body.getLine() != null) {
                        lines.add(body.getLine());
                        canvas.repaint();
                    }
                    break;
                case CLEAR:
                    lines.clear();
                    canvas.repaint();
                    break;
                case GUESS:
                    onGuess(body);
                    break;
                case CORRECT:
                    onCorrect(body);
                    break;
            }
        });
    }

    @Override
    public void playerLeft(User player) {
        super.playerLeft(player);
        SwingUtilities.invokeLater(() -> {
            playing = false;
            setTips("游戏结束：对手离开了");
            refreshControls();
        });
    }

    @Override
    protected void init() {
        initStartPanel();
    }

    @Override
    protected void start() {
        showPlayPanel();
    }

    @Override
    protected void allPlayersGameStarted() {
        // 你画我猜不需要房主分配棋色；进入画布后任一方都可以出题开局。
    }

    @Override
    protected JPanel getComponent() {
        return mainPanel;
    }

    private void initStartPanel() {
        mainPanel = new JPanel();
        mainPanel.setLayout(null);
        mainPanel.setMinimumSize(new Dimension(260, 220));

        JPanel panel = new JPanel();
        panel.setBounds(10, 10, 220, 180);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("你画我猜");
        title.setFont(new Font("", Font.BOLD, 15));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(12));

        JLabel desc = new JLabel("创建房间后邀请好友，双方准备后进入画布。");
        desc.setFont(new Font("", Font.PLAIN, 12));
        desc.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(desc);
        panel.add(Box.createVerticalStrut(12));

        if (DataCache.isOnline) {
            panel.add(getCreateRoomButton(Arrays.asList(2)));
            panel.add(Box.createVerticalStrut(8));
        }
        panel.add(getExitButton());

        mainPanel.add(panel);
        mainPanel.updateUI();
    }

    private void showPlayPanel() {
        if (mainPanel == null) {
            mainPanel = new JPanel();
        }
        mainPanel.removeAll();
        mainPanel.setLayout(new BorderLayout());
        mainPanel.setMinimumSize(new Dimension(560, 360));

        tipsLabel = new JLabel("等待有人出题并开始画图", JLabel.CENTER);
        tipsLabel.setFont(new Font("", Font.BOLD, 13));
        tipsLabel.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
        mainPanel.add(tipsLabel, BorderLayout.NORTH);

        canvas = new DrawCanvas();
        canvas.setPreferredSize(DEFAULT_CANVAS_SIZE.getDimension());
        canvas.setBackground(Color.WHITE);
        bindCanvasMouse();

        JPanel canvasPanel = new JPanel(new GridBagLayout());
        canvasPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        canvasPanel.add(canvas);
        mainPanel.add(canvasPanel, BorderLayout.CENTER);

        mainPanel.add(createRightPanel(), BorderLayout.EAST);
        mainPanel.updateUI();

        playing = false;
        drawer = false;
        currentWord = "";
        currentHint = "";
        drawerName = null;
        lines.clear();
        refreshControls();
    }

    private JPanel createRightPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setPreferredSize(new Dimension(250, DEFAULT_CANVAS_SIZE.getHeight()));

        guessLog = new JTextArea();
        guessLog.setEditable(false);
        guessLog.setLineWrap(true);
        guessLog.setRows(6);

        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        wordField = new JTextField();
        startButton = new JButton("开始画图");
        startButton.addActionListener(e -> startRound());
        randomWordButton = new JButton("从词库随机");
        randomWordButton.addActionListener(e -> requestRandomWord());
        controls.add(new JLabel("题目："));
        addFullWidth(controls, wordField);
        addFullWidth(controls, startButton);
        addFullWidth(controls, randomWordButton);
        controls.add(Box.createVerticalStrut(10));

        guessField = new JTextField();
        guessButton = new JButton("发送答案");
        guessButton.addActionListener(e -> submitGuess());
        controls.add(new JLabel("猜答案："));
        addFullWidth(controls, guessField);
        addFullWidth(controls, guessButton);
        controls.add(Box.createVerticalStrut(10));

        controls.add(new JLabel("猜词记录："));
        JScrollPane logScroll = new JScrollPane(guessLog);
        logScroll.setPreferredSize(new Dimension(210, 120));
        addFullWidth(controls, logScroll);
        controls.add(Box.createVerticalStrut(10));

        colorBox = new JComboBox<>(new String[]{"黑色", "红色", "橙色", "绿色", "蓝色", "紫色"});
        canvasSizeBox = new JComboBox<>(CanvasSize.values());
        canvasSizeBox.setSelectedItem(DEFAULT_CANVAS_SIZE);
        canvasSizeBox.addActionListener(e -> updateCanvasSize());
        sizeSpinner = new JSpinner(new SpinnerNumberModel(5, 2, 14, 1));
        clearButton = new JButton("清空画布");
        clearButton.addActionListener(e -> clearCanvas());
        controls.add(new JLabel("画布大小："));
        addFullWidth(controls, canvasSizeBox);
        controls.add(new JLabel("画笔颜色："));
        addFullWidth(controls, colorBox);
        controls.add(new JLabel("笔粗："));
        addFullWidth(controls, sizeSpinner);
        addFullWidth(controls, clearButton);
        controls.add(Box.createVerticalGlue());

        JScrollPane scrollPane = new JScrollPane(controls);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private void addFullWidth(JPanel panel, JComponent component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
        panel.add(component);
        panel.add(Box.createVerticalStrut(6));
    }

    private void bindCanvasMouse() {
        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (isResizePoint(e.getPoint())) {
                    resizingCanvas = true;
                    resizeStartPoint = e.getPoint();
                    resizeStartSize = canvas.getSize();
                    canvas.setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
                    return;
                }
                if (!canDraw()) {
                    return;
                }
                lastPoint = toViewPoint(e.getPoint(), canvas.getWidth(), canvas.getHeight());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (resizingCanvas) {
                    resizeCanvas(e.getPoint());
                    return;
                }
                if (!canDraw() || lastPoint == null) {
                    return;
                }
                Point next = toViewPoint(e.getPoint(), canvas.getWidth(), canvas.getHeight());
                if (lastPoint.distance(next) < 1.5) {
                    return;
                }
                DrawGuessDTO.Line line = new DrawGuessDTO.Line(
                        lastPoint.getX(),
                        lastPoint.getY(),
                        next.getX(),
                        next.getY(),
                        getSelectedColor(),
                        (Integer) sizeSpinner.getValue());
                lines.add(line);
                canvas.repaint();
                sendDrawLine(line);
                lastPoint = next;
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                lastPoint = null;
                resizingCanvas = false;
                resizeStartPoint = null;
                resizeStartSize = null;
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                canvas.setCursor(Cursor.getPredefinedCursor(
                        isResizePoint(e.getPoint()) ? Cursor.SE_RESIZE_CURSOR : Cursor.DEFAULT_CURSOR));
            }
        };
        canvas.addMouseListener(adapter);
        canvas.addMouseMotionListener(adapter);
    }

    private void startRound() {
        String word = wordField.getText().trim();
        if (word.isEmpty()) {
            return;
        }
        startRound(word, "");
    }

    private void startRound(String word, String hint) {
        if (playing || word == null || word.trim().isEmpty()) {
            return;
        }
        word = word.trim();
        currentWord = word;
        currentHint = hint == null ? "" : hint.trim();
        drawerName = GameAction.getNickname();
        drawer = true;
        playing = true;
        randomWordLoading = false;
        lines.clear();
        guessLog.setText("");
        setTips("你来画：" + currentWord);
        refreshControls();
        canvas.repaint();

        DrawGuessDTO dto = new DrawGuessDTO();
        dto.setEvent(DrawGuessDTO.Event.START_ROUND);
        dto.setDrawerName(drawerName);
        dto.setMaskedWord(maskWord(word));
        dto.setWordLength(countWordLength(word));
        dto.setHint(currentHint.isEmpty() ? null : currentHint);
        sendMsg(dto);
        wordField.setText("");
    }

    private void requestRandomWord() {
        if (playing || randomWordLoading) {
            return;
        }
        randomWordLoading = true;
        refreshControls();
        MessageAction.send(new Object(), Action.DRAW_GUESS_RANDOM_WORD);
        Timer timer = new Timer(5000, e -> {
            if (randomWordLoading) {
                randomWordLoading = false;
                refreshControls();
            }
        });
        timer.setRepeats(false);
        timer.start();
    }

    public void onRandomWord(DrawGuessWordDTO word) {
        SwingUtilities.invokeLater(() -> {
            randomWordLoading = false;
            if (word == null || word.getWord() == null || playing) {
                refreshControls();
                return;
            }
            startRound(word.getWord(), word.getHint());
        });
    }

    private void submitGuess() {
        if (!playing || drawer) {
            return;
        }
        String text = guessField.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        appendGuess(GameAction.getNickname(), text, false);

        DrawGuessDTO dto = new DrawGuessDTO();
        dto.setEvent(DrawGuessDTO.Event.GUESS);
        dto.setGuesserName(GameAction.getNickname());
        dto.setText(text);
        sendMsg(dto);
        guessField.setText("");
    }

    private void clearCanvas() {
        if (!canDraw()) {
            return;
        }
        lines.clear();
        canvas.repaint();

        DrawGuessDTO dto = new DrawGuessDTO();
        dto.setEvent(DrawGuessDTO.Event.CLEAR);
        sendMsg(dto);
    }

    private void sendDrawLine(DrawGuessDTO.Line line) {
        DrawGuessDTO dto = new DrawGuessDTO();
        dto.setEvent(DrawGuessDTO.Event.DRAW);
        dto.setLine(line);
        sendMsg(dto);
    }

    private void onStartRound(DrawGuessDTO body) {
        boolean isMeDrawer = GameAction.getNickname().equals(body.getDrawerName());
        currentWord = isMeDrawer && currentWord != null ? currentWord : "";
        currentHint = body.getHint() == null ? "" : body.getHint();
        drawerName = body.getDrawerName();
        drawer = isMeDrawer;
        playing = true;
        lines.clear();
        guessLog.setText("");
        canvas.repaint();
        if (drawer) {
            setTips("你来画：" + currentWord);
        } else {
            setTips(drawerName + " 正在画：" + body.getMaskedWord());
        }
        refreshControls();
    }

    private void onGuess(DrawGuessDTO body) {
        String username = body.getGuesserName() == null ? "对方" : body.getGuesserName();
        String text = body.getText() == null ? "" : body.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        appendGuess(username, text, false);
        if (drawer && normalize(text).equals(normalize(currentWord))) {
            DrawGuessDTO dto = new DrawGuessDTO();
            dto.setEvent(DrawGuessDTO.Event.CORRECT);
            dto.setGuesserName(username);
            dto.setWord(currentWord);
            sendMsg(dto);
            finishRound(username, currentWord);
        }
    }

    private void onCorrect(DrawGuessDTO body) {
        String username = body.getGuesserName() == null ? "对方" : body.getGuesserName();
        String word = body.getWord() == null ? currentWord : body.getWord();
        finishRound(username, word);
    }

    private void finishRound(String username, String word) {
        playing = false;
        drawer = false;
        appendGuess("系统", username + " 猜对了，答案是「" + word + "」", true);
        setTips(username + " 猜对了，本轮结束");
        refreshControls();
    }

    private void appendGuess(String username, String text, boolean system) {
        guessLog.append(username + "：" + text + "\n");
        if (system) {
            guessLog.append("----------\n");
        }
        guessLog.setCaretPosition(guessLog.getDocument().getLength());
    }

    private boolean canDraw() {
        return playing && drawer;
    }

    private void refreshControls() {
        boolean canDraw = canDraw();
        boolean canGuess = playing && !drawer;
        if (clearButton != null) {
            clearButton.setEnabled(canDraw);
        }
        if (startButton != null) {
            startButton.setEnabled(!playing);
        }
        if (randomWordButton != null) {
            randomWordButton.setEnabled(!playing && !randomWordLoading);
            randomWordButton.setText(randomWordLoading ? "获取中..." : "从词库随机");
        }
        if (wordField != null) {
            wordField.setEnabled(!playing);
        }
        if (guessButton != null) {
            guessButton.setEnabled(canGuess);
        }
        if (guessField != null) {
            guessField.setEnabled(canGuess);
        }
        if (colorBox != null) {
            colorBox.setEnabled(canDraw);
        }
        if (sizeSpinner != null) {
            sizeSpinner.setEnabled(canDraw);
        }
    }

    private void updateCanvasSize() {
        if (canvas == null || canvasSizeBox == null) {
            return;
        }
        CanvasSize size = (CanvasSize) canvasSizeBox.getSelectedItem();
        if (size == null) {
            return;
        }
        canvas.setPreferredSize(size.getDimension());
        canvas.revalidate();
        canvas.repaint();
    }

    private void resizeCanvas(Point point) {
        if (resizeStartPoint == null || resizeStartSize == null) {
            return;
        }
        int width = Math.max(220, Math.min(900, resizeStartSize.width + point.x - resizeStartPoint.x));
        int height = Math.max(150, Math.min(620, resizeStartSize.height + point.y - resizeStartPoint.y));
        canvas.setPreferredSize(new Dimension(width, height));
        canvas.revalidate();
        canvas.repaint();
    }

    private boolean isResizePoint(Point point) {
        return point.x >= canvas.getWidth() - 14 && point.y >= canvas.getHeight() - 14;
    }

    private void setTips(String text) {
        if (tipsLabel != null) {
            tipsLabel.setText(text);
        }
    }

    private Point toViewPoint(Point point, int width, int height) {
        int x = Math.max(0, Math.min(VIEW_WIDTH, Math.round(point.x * VIEW_WIDTH / (float) width)));
        int y = Math.max(0, Math.min(VIEW_HEIGHT, Math.round(point.y * VIEW_HEIGHT / (float) height)));
        return new Point(x, y);
    }

    private String getSelectedColor() {
        switch (colorBox.getSelectedIndex()) {
            case 1:
                return "#ef4444";
            case 2:
                return "#f97316";
            case 3:
                return "#22c55e";
            case 4:
                return "#3b82f6";
            case 5:
                return "#8b5cf6";
            default:
                return "#111827";
        }
    }

    private String maskWord(String word) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            char ch = word.charAt(i);
            sb.append(Character.isWhitespace(ch) ? ch : '＿');
        }
        return sb.toString();
    }

    private int countWordLength(String word) {
        int len = 0;
        for (int i = 0; i < word.length(); i++) {
            if (!Character.isWhitespace(word.charAt(i))) {
                len++;
            }
        }
        return len;
    }

    private String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "").toLowerCase();
    }

    private class DrawCanvas extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double sx = getWidth() / (double) VIEW_WIDTH;
            double sy = getHeight() / (double) VIEW_HEIGHT;
            for (DrawGuessDTO.Line line : lines) {
                g2.setColor(Color.decode(line.getColor()));
                g2.setStroke(new BasicStroke(line.getSize(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(
                        (int) Math.round(line.getX1() * sx),
                        (int) Math.round(line.getY1() * sy),
                        (int) Math.round(line.getX2() * sx),
                        (int) Math.round(line.getY2() * sy));
            }
            g2.setColor(new Color(180, 180, 180));
            int w = getWidth();
            int h = getHeight();
            g2.drawLine(w - 12, h - 4, w - 4, h - 12);
            g2.drawLine(w - 8, h - 4, w - 4, h - 8);
        }
    }

    private enum CanvasSize {
        SMALL("小（320×210）", 320, 210),
        MEDIUM("中（480×315）", 480, 315),
        LARGE("大（640×420）", 640, 420);

        private final String label;
        private final int width;
        private final int height;

        CanvasSize(String label, int width, int height) {
            this.label = label;
            this.width = width;
            this.height = height;
        }

        private Dimension getDimension() {
            return new Dimension(width, height);
        }

        private int getHeight() {
            return height;
        }

        @Override
        public String toString() {
            return label;
        }
    }

}
