package cn.xeblog.plugin.game.turtlesoup;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.CreateGameRoomDTO;
import cn.xeblog.commons.entity.game.turtlesoup.TurtleSoupDTO;
import cn.xeblog.commons.entity.game.turtlesoup.TurtleSoupLogItemDTO;
import cn.xeblog.commons.entity.game.turtlesoup.TurtleSoupNextStoryDTO;
import cn.xeblog.commons.entity.game.turtlesoup.TurtleSoupRecordDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.Game;
import cn.xeblog.plugin.action.GameAction;
import cn.xeblog.plugin.action.MessageAction;
import cn.xeblog.plugin.annotation.DoGame;
import cn.xeblog.plugin.cache.DataCache;
import cn.xeblog.plugin.game.AbstractGame;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 海龟汤。
 */
@DoGame(Game.TURTLE_SOUP)
public class TurtleSoup extends AbstractGame<TurtleSoupDTO> {

    private JPanel mainPanel;
    private JLabel titleLabel;
    private JTextArea surfaceArea;
    private JTextArea bottomArea;
    private JTextArea logArea;
    private JTextArea inputArea;
    private JTextField customAnswerField;
    private JPanel questionPanel;
    private JPanel answerPanel;
    private JPanel judgePanel;
    private JPanel storyControlPanel;
    private JPanel previewControlPanel;
    private final List<JButton> answerButtons = new ArrayList<>();
    private JButton questionButton;
    private JButton guessButton;
    private JButton customAnswerButton;
    private JButton previewChangeButton;
    private JButton previewConfirmButton;
    private JButton correctButton;
    private JButton partialButton;
    private JButton wrongButton;
    private JButton changeButton;
    private JButton confirmButton;
    private JButton nextButton;
    private JTextArea recordArea;

    private int guessLimit = 3;
    private String hostMode = "OWNER";
    private String hostId;
    private String hostName;
    private String guesserId;
    private String guesserName;
    private int guessUsed;
    private boolean lastGuessPending;
    private boolean previewing;
    private boolean playing;

    @Override
    protected void init() {
        showStartPanel();
    }

    @Override
    protected void start() {
        showPlayPanel();
    }

    @Override
    protected void allPlayersGameStarted() {
        if (!isHomeowner() || getRoom() == null) {
            return;
        }
        MessageAction.send(new TurtleSoupNextStoryDTO(getRoom().getId()), Action.TURTLE_SOUP_NEXT_STORY);
    }

    @Override
    public void handle(TurtleSoupDTO body) {
        if (body == null || body.getEvent() == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            switch (body.getEvent()) {
                case PREVIEW_STORY:
                    onPreviewStory(body);
                    break;
                case START_ROUND:
                    onStartRound(body);
                    break;
                case QUESTION:
                    appendLog(body.getGuesserName(), "提问", body.getContent());
                    break;
                case ANSWER:
                    appendLog(body.getHostName(), "回答", body.getAnswer());
                    break;
                case GUESS:
                    guessUsed = body.getGuessUsed();
                    lastGuessPending = true;
                    appendLog(body.getGuesserName(), "猜底", body.getContent());
                    refreshControls();
                    break;
                case JUDGE:
                    lastGuessPending = false;
                    appendLog(body.getHostName(), "判定", resultText(body.getGuessResult()));
                    refreshControls();
                    break;
                case REVEAL:
                    previewing = false;
                    playing = false;
                    lastGuessPending = false;
                    bottomArea.setText(body.getBottom() == null ? "" : body.getBottom());
                    appendLog("系统", "揭底", "本轮结束：" + resultText(body.getGuessResult()));
                    refreshControls();
                    break;
            }
        });
    }

    @Override
    public void playerLeft(User player) {
        super.playerLeft(player);
        SwingUtilities.invokeLater(() -> {
            previewing = false;
            playing = false;
            setTitle("对手已离开，本轮不记录");
            refreshControls();
        });
    }

    @Override
    protected JPanel getComponent() {
        return mainPanel;
    }

    private void showStartPanel() {
        mainPanel = new JPanel();
        mainPanel.setLayout(null);
        mainPanel.setMinimumSize(new Dimension(260, 300));
        mainPanel.setPreferredSize(new Dimension(320, 320));

        JPanel panel = new JPanel();
        panel.setBounds(10, 10, 260, 280);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("海龟汤");
        title.setFont(new Font("", Font.BOLD, 15));
        panel.add(title);
        panel.add(Box.createVerticalStrut(10));
        panel.add(new JLabel("从题库随机抽题，双方轮流主持。"));
        panel.add(Box.createVerticalStrut(10));

        if (DataCache.isOnline) {
            JPanel limitPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            limitPanel.add(new JLabel("猜底机会："));
            JSpinner limitSpinner = new JSpinner(new SpinnerNumberModel(guessLimit, 1, 10, 1));
            limitSpinner.addChangeListener(e -> guessLimit = (Integer) limitSpinner.getValue());
            limitPanel.add(limitSpinner);
            panel.add(limitPanel);

            JComboBox<String> hostBox = new JComboBox<>(new String[]{"我主持", "对方主持", "随机"});
            hostBox.addActionListener(e -> {
                int idx = hostBox.getSelectedIndex();
                hostMode = idx == 1 ? "GUEST" : idx == 2 ? "RANDOM" : "OWNER";
            });
            panel.add(hostBox);
            panel.add(Box.createVerticalStrut(8));

            JButton createRoomButton = new JButton("创建房间");
            createRoomButton.addActionListener(e -> {
                CreateGameRoomDTO dto = new CreateGameRoomDTO(Game.TURTLE_SOUP, 2, "在线PK");
                dto.setTurtleSoupGuessLimit(guessLimit);
                dto.setTurtleSoupHostMode(hostMode);
                MessageAction.send(dto, Action.CREATE_GAME_ROOM);
            });
            panel.add(createRoomButton);
            panel.add(Box.createVerticalStrut(8));

            JButton recordButton = new JButton("查看我的记录");
            recordButton.addActionListener(e -> MessageAction.send(new Object(), Action.TURTLE_SOUP_MY_RECORDS));
            panel.add(recordButton);
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
        mainPanel.setMinimumSize(new Dimension(620, 540));

        JPanel headerPanel = new JPanel(new BorderLayout());
        titleLabel = new JLabel("等待房主分发汤面", JLabel.CENTER);
        titleLabel.setFont(new Font("", Font.BOLD, 14));
        headerPanel.add(titleLabel, BorderLayout.NORTH);
        previewControlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
        previewChangeButton = new JButton("换题");
        previewChangeButton.addActionListener(e -> requestStory());
        previewConfirmButton = new JButton("确认题目");
        previewConfirmButton.addActionListener(e -> confirmStory());
        previewControlPanel.add(previewChangeButton);
        previewControlPanel.add(previewConfirmButton);
        headerPanel.add(previewControlPanel, BorderLayout.SOUTH);
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        JPanel storyPanel = new JPanel(new GridLayout(1, 2, 8, 8));
        surfaceArea = readonlyArea();
        bottomArea = readonlyArea();
        storyPanel.add(wrap("汤面", surfaceArea));
        storyPanel.add(wrap("汤底", bottomArea));

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setMinimumSize(new Dimension(520, 220));
        logArea = readonlyArea();
        bottom.add(wrap("问答记录", logArea), BorderLayout.CENTER);
        JScrollPane controlScrollPane = new JScrollPane(createControlPanel());
        controlScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        controlScrollPane.setPreferredSize(new Dimension(270, 260));
        bottom.add(controlScrollPane, BorderLayout.EAST);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, storyPanel, bottom);
        splitPane.setResizeWeight(0.45);
        splitPane.setBorder(null);
        mainPanel.add(splitPane, BorderLayout.CENTER);
        mainPanel.updateUI();

        previewing = false;
        playing = false;
        refreshControls();
    }

    private JPanel createControlPanel() {
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        questionPanel = new JPanel();
        questionPanel.setLayout(new BoxLayout(questionPanel, BoxLayout.Y_AXIS));
        questionPanel.setBorder(BorderFactory.createTitledBorder("猜题区"));
        inputArea = new JTextArea(4, 18);
        inputArea.setLineWrap(true);
        questionButton = new JButton("提问");
        questionButton.addActionListener(e -> sendQuestion());
        guessButton = new JButton("猜底");
        guessButton.addActionListener(e -> sendGuess());
        questionPanel.add(new JScrollPane(inputArea));
        JPanel questionButtons = new JPanel(new GridLayout(1, 2, 4, 0));
        questionButtons.add(questionButton);
        questionButtons.add(guessButton);
        questionPanel.add(questionButtons);
        panel.add(questionPanel);

        answerPanel = new JPanel();
        answerPanel.setLayout(new BoxLayout(answerPanel, BoxLayout.Y_AXIS));
        answerPanel.setBorder(BorderFactory.createTitledBorder("主持回答"));
        JPanel answerGrid = new JPanel(new GridLayout(0, 2, 4, 4));
        String[] answers = {"是", "否", "无关", "接近", "请重问", "不确定", "部分正确"};
        answerButtons.clear();
        for (String answer : answers) {
            JButton button = new JButton(answer);
            button.addActionListener(e -> sendAnswer(answer));
            answerButtons.add(button);
            answerGrid.add(button);
        }
        answerPanel.add(answerGrid);
        customAnswerField = new JTextField();
        customAnswerButton = new JButton("自定义回答");
        customAnswerButton.addActionListener(e -> sendAnswer(customAnswerField.getText()));
        answerPanel.add(customAnswerField);
        answerPanel.add(customAnswerButton);
        panel.add(answerPanel);

        judgePanel = new JPanel(new GridLayout(0, 1, 0, 4));
        judgePanel.setBorder(BorderFactory.createTitledBorder("正式猜底判定"));
        correctButton = new JButton("判定正确");
        correctButton.addActionListener(e -> judge(TurtleSoupDTO.GuessResult.CORRECT));
        partialButton = new JButton("判定部分正确");
        partialButton.addActionListener(e -> judge(TurtleSoupDTO.GuessResult.PARTIAL));
        wrongButton = new JButton("判定错误");
        wrongButton.addActionListener(e -> judge(TurtleSoupDTO.GuessResult.WRONG));
        judgePanel.add(correctButton);
        judgePanel.add(partialButton);
        judgePanel.add(wrongButton);
        panel.add(judgePanel);

        storyControlPanel = new JPanel(new GridLayout(0, 1, 0, 4));
        storyControlPanel.setBorder(BorderFactory.createTitledBorder("题目控制"));
        changeButton = new JButton("换题");
        changeButton.addActionListener(e -> requestStory());
        confirmButton = new JButton("确认题目");
        confirmButton.addActionListener(e -> confirmStory());
        nextButton = new JButton("下一轮");
        nextButton.addActionListener(e -> requestStory());
        storyControlPanel.add(changeButton);
        storyControlPanel.add(confirmButton);
        storyControlPanel.add(nextButton);
        panel.add(storyControlPanel);

        JButton recordButton = new JButton("我的记录");
        recordButton.addActionListener(e -> MessageAction.send(new Object(), Action.TURTLE_SOUP_MY_RECORDS));
        panel.add(recordButton);

        recordArea = readonlyArea();
        recordArea.setRows(5);
        panel.add(new JScrollPane(recordArea));
        return panel;
    }

    public void onRecords(List<TurtleSoupRecordDTO> records) {
        SwingUtilities.invokeLater(() -> {
            StringBuilder sb = new StringBuilder();
            for (TurtleSoupRecordDTO record : records) {
                sb.append(record.getTitle() == null ? record.getSurface() : record.getTitle()).append("\n");
                sb.append("主持人：").append(record.getHostName())
                        .append("，猜题人：").append(record.getGuesserName())
                        .append("，结果：").append(resultText(record.getResult()));
                if (record.getDifficulty() != null && !record.getDifficulty().isEmpty()) {
                    sb.append("，难度：").append(record.getDifficulty());
                }
                if (record.getTags() != null && !record.getTags().isEmpty()) {
                    sb.append("，标签：").append(record.getTags());
                }
                sb.append("\n");
                sb.append("汤面：").append(record.getSurface()).append("\n");
                sb.append("汤底：").append(record.getBottom()).append("\n");
                for (TurtleSoupLogItemDTO log : record.getLogs()) {
                    sb.append("  ").append(formatLog(log)).append("\n");
                }
                sb.append("----------\n");
            }
            String text = sb.length() == 0 ? "暂无记录" : sb.toString();
            if (recordArea != null) {
                recordArea.setText(text);
                return;
            }
            JTextArea area = readonlyArea();
            area.setText(text);
            area.setCaretPosition(0);
            JScrollPane pane = new JScrollPane(area);
            pane.setPreferredSize(new Dimension(520, 360));
            JOptionPane.showMessageDialog(mainPanel, pane, "海龟汤记录", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    private void onPreviewStory(TurtleSoupDTO body) {
        previewing = true;
        playing = false;
        hostId = body.getHostId();
        hostName = body.getHostName();
        guesserId = body.getGuesserId();
        guesserName = body.getGuesserName();
        guessLimit = body.getGuessLimit();
        guessUsed = 0;
        lastGuessPending = false;
        logArea.setText("");
        if (isCurrentHost() && body.getSurface() != null) {
            surfaceArea.setText(formatStory(body));
            bottomArea.setText("确认题目后可见");
            setTitle("主持人预览：可换题，确认后正式开始");
        } else {
            surfaceArea.setText("主持人正在预览题面，确认后正式开始。");
            bottomArea.setText("");
            setTitle("等待主持人确认题目");
        }
        refreshControls();
    }

    private void onStartRound(TurtleSoupDTO body) {
        previewing = false;
        playing = true;
        hostId = body.getHostId();
        hostName = body.getHostName();
        guesserId = body.getGuesserId();
        guesserName = body.getGuesserName();
        guessLimit = body.getGuessLimit();
        guessUsed = body.getGuessUsed();
        lastGuessPending = false;
        surfaceArea.setText(formatStory(body));
        bottomArea.setText(body.getBottom() == null ? "猜题人不可见" : body.getBottom());
        logArea.setText("");
        setTitle("第 " + body.getRoundNo() + " 轮，主持人：" + hostName + "，猜题人：" + guesserName);
        refreshControls();
    }

    private String formatStory(TurtleSoupDTO body) {
        StringBuilder sb = new StringBuilder();
        if (body.getTitle() != null && !body.getTitle().isEmpty()) {
            sb.append("标题：").append(body.getTitle()).append("\n\n");
        }
        sb.append(body.getSurface() == null ? "" : body.getSurface());
        if (body.getDifficulty() != null && !body.getDifficulty().isEmpty()) {
            sb.append("\n\n难度：").append(body.getDifficulty());
        }
        if (body.getTags() != null && !body.getTags().isEmpty()) {
            sb.append("\n标签：").append(body.getTags());
        }
        return sb.toString();
    }

    private void sendQuestion() {
        String text = inputArea.getText().trim();
        if (text.isEmpty() || !playing || !isCurrentGuesser()) {
            return;
        }
        TurtleSoupDTO dto = new TurtleSoupDTO();
        dto.setEvent(TurtleSoupDTO.Event.QUESTION);
        dto.setContent(text);
        sendMsg(dto);
        inputArea.setText("");
    }

    private void sendGuess() {
        String text = inputArea.getText().trim();
        if (text.isEmpty() || !playing || !isCurrentGuesser() || guessUsed >= guessLimit) {
            return;
        }
        TurtleSoupDTO dto = new TurtleSoupDTO();
        dto.setEvent(TurtleSoupDTO.Event.GUESS);
        dto.setContent(text);
        sendMsg(dto);
        inputArea.setText("");
    }

    private void sendAnswer(String answer) {
        if (answer == null || answer.trim().isEmpty() || !playing || !isCurrentHost() || lastGuessPending) {
            return;
        }
        TurtleSoupDTO dto = new TurtleSoupDTO();
        dto.setEvent(TurtleSoupDTO.Event.ANSWER);
        dto.setAnswer(answer.trim());
        sendMsg(dto);
        customAnswerField.setText("");
    }

    private void judge(TurtleSoupDTO.GuessResult result) {
        if (!playing || !isCurrentHost() || !lastGuessPending) {
            return;
        }
        TurtleSoupDTO dto = new TurtleSoupDTO();
        dto.setEvent(TurtleSoupDTO.Event.JUDGE);
        dto.setGuessResult(result);
        sendMsg(dto);
    }

    private void requestStory() {
        if (getRoom() == null) {
            return;
        }
        MessageAction.send(new TurtleSoupNextStoryDTO(getRoom().getId()), Action.TURTLE_SOUP_NEXT_STORY);
    }

    private void confirmStory() {
        TurtleSoupDTO dto = new TurtleSoupDTO();
        dto.setEvent(TurtleSoupDTO.Event.CONFIRM_STORY);
        sendMsg(dto);
    }

    private void refreshControls() {
        boolean isHost = playing && isCurrentHost();
        boolean isGuesser = playing && isCurrentGuesser();
        boolean canAnswerQuestion = isHost && !lastGuessPending;
        boolean canPreview = previewing && isCurrentHost();
        boolean canNext = !previewing && !playing && isHomeowner();
        if (previewControlPanel != null) previewControlPanel.setVisible(canPreview);
        if (previewChangeButton != null) previewChangeButton.setEnabled(canPreview);
        if (previewConfirmButton != null) previewConfirmButton.setEnabled(canPreview);
        if (questionPanel != null) questionPanel.setVisible(isGuesser);
        if (answerPanel != null) answerPanel.setVisible(canAnswerQuestion);
        if (judgePanel != null) judgePanel.setVisible(isHost && lastGuessPending);
        if (storyControlPanel != null) storyControlPanel.setVisible(canNext);
        if (questionButton != null) questionButton.setEnabled(isGuesser);
        if (guessButton != null) guessButton.setEnabled(isGuesser && guessUsed < guessLimit);
        if (inputArea != null) inputArea.setEnabled(isGuesser);
        for (JButton button : answerButtons) {
            button.setEnabled(canAnswerQuestion);
        }
        if (customAnswerField != null) customAnswerField.setEnabled(canAnswerQuestion);
        if (customAnswerButton != null) customAnswerButton.setEnabled(canAnswerQuestion);
        if (correctButton != null) correctButton.setEnabled(isHost && lastGuessPending);
        if (partialButton != null) partialButton.setEnabled(isHost && lastGuessPending);
        if (wrongButton != null) wrongButton.setEnabled(isHost && lastGuessPending);
        if (changeButton != null) changeButton.setEnabled(canPreview);
        if (confirmButton != null) confirmButton.setEnabled(canPreview);
        if (changeButton != null) changeButton.setVisible(canPreview);
        if (confirmButton != null) confirmButton.setVisible(canPreview);
        if (nextButton != null) nextButton.setEnabled(canNext);
        if (nextButton != null) nextButton.setVisible(canNext);
        if (mainPanel != null) {
            mainPanel.revalidate();
            mainPanel.repaint();
        }
    }

    private boolean isCurrentHost() {
        return GameAction.getNickname() != null && GameAction.getNickname().equals(hostName);
    }

    private boolean isCurrentGuesser() {
        return GameAction.getNickname() != null && GameAction.getNickname().equals(guesserName);
    }

    private void appendLog(String username, String action, String text) {
        logArea.append((username == null ? "-" : username) + " " + action + "：" + (text == null ? "" : text) + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private String formatLog(TurtleSoupLogItemDTO log) {
        if ("QUESTION".equals(log.getType())) return log.getUsername() + " 提问：" + log.getContent();
        if ("ANSWER".equals(log.getType())) return "主持回答：" + log.getAnswer();
        if ("GUESS".equals(log.getType())) return log.getUsername() + " 猜底：" + log.getContent();
        if ("JUDGE".equals(log.getType())) return "主持判定：" + resultText(log.getResult());
        return "";
    }

    private String resultText(Object result) {
        if (result == TurtleSoupDTO.GuessResult.CORRECT || "CORRECT".equals(result)) return "正确";
        if (result == TurtleSoupDTO.GuessResult.PARTIAL || "PARTIAL".equals(result)) return "部分正确";
        return "错误";
    }

    private JTextArea readonlyArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return area;
    }

    private JScrollPane wrap(String title, JTextArea area) {
        JScrollPane pane = new JScrollPane(area);
        pane.setBorder(BorderFactory.createTitledBorder(title));
        return pane;
    }

    private void setTitle(String text) {
        if (titleLabel != null) {
            titleLabel.setText(text);
        }
    }

}
