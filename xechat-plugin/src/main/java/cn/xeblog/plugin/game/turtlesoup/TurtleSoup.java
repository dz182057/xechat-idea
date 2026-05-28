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
    private JButton questionButton;
    private JButton guessButton;
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
        mainPanel.setMinimumSize(new Dimension(260, 260));

        JPanel panel = new JPanel();
        panel.setBounds(10, 10, 240, 230);
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

        titleLabel = new JLabel("等待房主分发汤面", JLabel.CENTER);
        titleLabel.setFont(new Font("", Font.BOLD, 14));
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(1, 2, 8, 8));
        surfaceArea = readonlyArea();
        bottomArea = readonlyArea();
        center.add(wrap("汤面", surfaceArea));
        center.add(wrap("汤底", bottomArea));
        mainPanel.add(center, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        logArea = readonlyArea();
        bottom.add(wrap("问答记录", logArea), BorderLayout.CENTER);
        bottom.add(createControlPanel(), BorderLayout.EAST);
        mainPanel.add(bottom, BorderLayout.SOUTH);
        mainPanel.updateUI();

        previewing = false;
        playing = false;
        refreshControls();
    }

    private JPanel createControlPanel() {
        JPanel panel = new JPanel();
        panel.setPreferredSize(new Dimension(250, 260));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        inputArea = new JTextArea(4, 18);
        inputArea.setLineWrap(true);
        questionButton = new JButton("提问");
        questionButton.addActionListener(e -> sendQuestion());
        guessButton = new JButton("猜底");
        guessButton.addActionListener(e -> sendGuess());
        panel.add(new JScrollPane(inputArea));
        panel.add(questionButton);
        panel.add(guessButton);

        String[] answers = {"是", "否", "无关", "接近", "请重问", "不确定", "部分正确"};
        for (String answer : answers) {
            JButton button = new JButton(answer);
            button.addActionListener(e -> sendAnswer(answer));
            panel.add(button);
        }
        customAnswerField = new JTextField();
        JButton customButton = new JButton("自定义回答");
        customButton.addActionListener(e -> sendAnswer(customAnswerField.getText()));
        panel.add(customAnswerField);
        panel.add(customButton);

        correctButton = new JButton("判定正确");
        correctButton.addActionListener(e -> judge(TurtleSoupDTO.GuessResult.CORRECT));
        partialButton = new JButton("判定部分正确");
        partialButton.addActionListener(e -> judge(TurtleSoupDTO.GuessResult.PARTIAL));
        wrongButton = new JButton("判定错误");
        wrongButton.addActionListener(e -> judge(TurtleSoupDTO.GuessResult.WRONG));
        panel.add(correctButton);
        panel.add(partialButton);
        panel.add(wrongButton);

        changeButton = new JButton("换题");
        changeButton.addActionListener(e -> requestStory());
        confirmButton = new JButton("确认题目");
        confirmButton.addActionListener(e -> confirmStory());
        nextButton = new JButton("下一轮");
        nextButton.addActionListener(e -> requestStory());
        JButton recordButton = new JButton("我的记录");
        recordButton.addActionListener(e -> MessageAction.send(new Object(), Action.TURTLE_SOUP_MY_RECORDS));
        panel.add(changeButton);
        panel.add(confirmButton);
        panel.add(nextButton);
        panel.add(recordButton);

        recordArea = readonlyArea();
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
            recordArea.setText(sb.length() == 0 ? "暂无记录" : sb.toString());
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
        if (text.isEmpty()) {
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
        if (text.isEmpty()) {
            return;
        }
        TurtleSoupDTO dto = new TurtleSoupDTO();
        dto.setEvent(TurtleSoupDTO.Event.GUESS);
        dto.setContent(text);
        sendMsg(dto);
        inputArea.setText("");
    }

    private void sendAnswer(String answer) {
        if (answer == null || answer.trim().isEmpty()) {
            return;
        }
        TurtleSoupDTO dto = new TurtleSoupDTO();
        dto.setEvent(TurtleSoupDTO.Event.ANSWER);
        dto.setAnswer(answer.trim());
        sendMsg(dto);
        customAnswerField.setText("");
    }

    private void judge(TurtleSoupDTO.GuessResult result) {
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
        boolean isGuesser = playing && GameAction.getNickname() != null && GameAction.getNickname().equals(guesserName);
        boolean canPreview = previewing && isCurrentHost();
        if (questionButton != null) questionButton.setEnabled(isGuesser);
        if (guessButton != null) guessButton.setEnabled(isGuesser && guessUsed < guessLimit);
        if (inputArea != null) inputArea.setEnabled(isGuesser);
        if (customAnswerField != null) customAnswerField.setEnabled(isHost);
        if (correctButton != null) correctButton.setEnabled(isHost && lastGuessPending);
        if (partialButton != null) partialButton.setEnabled(isHost && lastGuessPending);
        if (wrongButton != null) wrongButton.setEnabled(isHost && lastGuessPending);
        if (changeButton != null) changeButton.setEnabled(canPreview);
        if (confirmButton != null) confirmButton.setEnabled(canPreview);
        if (nextButton != null) nextButton.setEnabled(!previewing && !playing && isHomeowner());
    }

    private boolean isCurrentHost() {
        return GameAction.getNickname() != null && GameAction.getNickname().equals(hostName);
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
