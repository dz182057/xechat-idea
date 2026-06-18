package cn.xeblog.plugin.game.quickquiz;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.quickquiz.*;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.Game;
import cn.xeblog.plugin.action.MessageAction;
import cn.xeblog.plugin.annotation.DoGame;
import cn.xeblog.plugin.cache.DataCache;
import cn.xeblog.plugin.game.AbstractGame;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 快问快答。
 */
@DoGame(Game.QUICK_QUIZ)
public class QuickQuiz extends AbstractGame<QuickQuizNextQuestionDTO> {

    private static final int DEFAULT_QUESTION_COUNT = 5;
    private static final String UNKNOWN_OPPONENT_KEY = "__UNKNOWN_OPPONENT__";
    private static final String UNKNOWN_OPPONENT_NAME = "未知对手";

    private JPanel mainPanel;
    private JLabel titleLabel;
    private JPanel optionPanel;
    private JTextArea resultArea;
    private JTextArea recordArea;
    private QuickQuizQuestionDTO currentQuestion;
    private boolean submitted;
    private int selectedQuestionCount = DEFAULT_QUESTION_COUNT;
    private int selectedPlayerCount = 2;
    private int selectedTimeLimitSeconds = 15;
    private int selectedEntryFee = 0;

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
        MessageAction.send(new QuickQuizNextQuestionDTO(getRoom().getId()), Action.QUICK_QUIZ_NEXT_QUESTION);
    }

    @Override
    public void playerLeft(User player) {
        super.playerLeft(player);
        SwingUtilities.invokeLater(() -> setTitle("对手已离开，游戏结束"));
    }

    @Override
    protected JPanel getComponent() {
        return mainPanel;
    }

    private void showStartPanel() {
        mainPanel = new JPanel();
        mainPanel.setLayout(null);
        mainPanel.setMinimumSize(new Dimension(260, 320));
        mainPanel.setPreferredSize(new Dimension(340, 380));

        JPanel panel = new JPanel();
        panel.setBounds(10, 10, 300, 340);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("快问快答");
        title.setFont(new Font("", Font.BOLD, 15));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(12));
        panel.add(new JLabel("限时答题，答对加分，答错扣分，不作答不扣分。"));
        panel.add(Box.createVerticalStrut(12));

        if (DataCache.isOnline) {
            selectedQuestionCount = selectedQuestionCount <= 0 ? DEFAULT_QUESTION_COUNT : selectedQuestionCount;
            selectedPlayerCount = selectedPlayerCount < 2 ? 2 : selectedPlayerCount;
            selectedTimeLimitSeconds = selectedTimeLimitSeconds < 5 ? 15 : selectedTimeLimitSeconds;
            selectedEntryFee = Math.max(0, selectedEntryFee);
            JPanel countPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            countPanel.add(new JLabel("本局题数："));
            JSpinner countSpinner = new JSpinner(new SpinnerNumberModel(selectedQuestionCount, 1, 50, 1));
            countSpinner.addChangeListener(e -> selectedQuestionCount = (Integer) countSpinner.getValue());
            countPanel.add(countSpinner);
            panel.add(countPanel);
            panel.add(Box.createVerticalStrut(8));

            JPanel playerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            playerPanel.add(new JLabel("房间人数："));
            JSpinner playerSpinner = new JSpinner(new SpinnerNumberModel(selectedPlayerCount, 2, 8, 1));
            playerSpinner.addChangeListener(e -> selectedPlayerCount = (Integer) playerSpinner.getValue());
            playerPanel.add(playerSpinner);
            panel.add(playerPanel);
            panel.add(Box.createVerticalStrut(8));

            JPanel timePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            timePanel.add(new JLabel("每题秒数："));
            JSpinner timeSpinner = new JSpinner(new SpinnerNumberModel(selectedTimeLimitSeconds, 5, 120, 5));
            timeSpinner.addChangeListener(e -> selectedTimeLimitSeconds = (Integer) timeSpinner.getValue());
            timePanel.add(timeSpinner);
            panel.add(timePanel);
            panel.add(Box.createVerticalStrut(8));

            JPanel feePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            feePanel.add(new JLabel("报名骨头："));
            JSpinner feeSpinner = new JSpinner(new SpinnerNumberModel(selectedEntryFee, 0, 9999, 1));
            feeSpinner.addChangeListener(e -> selectedEntryFee = (Integer) feeSpinner.getValue());
            feePanel.add(feeSpinner);
            panel.add(feePanel);
            panel.add(Box.createVerticalStrut(8));

            JButton createRoomButton = new JButton("创建房间");
            createRoomButton.addActionListener(e -> {
                cn.xeblog.commons.entity.game.CreateGameRoomDTO dto =
                        new cn.xeblog.commons.entity.game.CreateGameRoomDTO(Game.QUICK_QUIZ,
                                selectedPlayerCount, "知识问答", selectedQuestionCount);
                dto.setQuickQuizTimeLimitSeconds(selectedTimeLimitSeconds);
                dto.setQuickQuizEntryFee(selectedEntryFee);
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
        mainPanel.setMinimumSize(new Dimension(560, 420));
        mainPanel.setPreferredSize(new Dimension(620, 560));

        JPanel top = new JPanel(new BorderLayout());
        titleLabel = new JLabel("等待房主下发题目", JLabel.CENTER);
        titleLabel.setFont(new Font("", Font.BOLD, 14));
        top.add(titleLabel, BorderLayout.CENTER);
        mainPanel.add(top, BorderLayout.NORTH);

        optionPanel = new JPanel();
        optionPanel.setLayout(new BoxLayout(optionPanel, BoxLayout.Y_AXIS));
        optionPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        mainPanel.add(optionPanel, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        resultArea = new JTextArea(6, 30);
        resultArea.setEditable(false);
        resultArea.setLineWrap(true);
        recordArea = new JTextArea(6, 30);
        recordArea.setEditable(false);
        recordArea.setLineWrap(true);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("本题结果", new JScrollPane(resultArea));
        tabs.addTab("我的记录", new JScrollPane(recordArea));
        bottom.add(tabs, BorderLayout.CENTER);

        JPanel buttons = new JPanel();
        JButton recordButton = new JButton("查看我的记录");
        recordButton.addActionListener(e -> MessageAction.send(new Object(), Action.QUICK_QUIZ_MY_RECORDS));
        buttons.add(recordButton);
        bottom.add(buttons, BorderLayout.SOUTH);

        mainPanel.add(bottom, BorderLayout.SOUTH);
        mainPanel.updateUI();
    }

    public void onQuestion(QuickQuizQuestionDTO question) {
        SwingUtilities.invokeLater(() -> {
            currentQuestion = question;
            submitted = false;
            setTitle("第 " + question.getRoundNo() + "/" + question.getTotalRounds() + " 题：" + question.getQuestion());
            resultArea.setText("");
            optionPanel.removeAll();
            List<String> options = question.getOptions() == null ? new ArrayList<>() : question.getOptions();
            for (int i = 0; i < options.size(); i++) {
                int index = i;
                JButton button = new JButton((char) ('A' + i) + ". " + options.get(i));
                button.setAlignmentX(Component.LEFT_ALIGNMENT);
                button.addActionListener(e -> submit(index));
                optionPanel.add(button);
                optionPanel.add(Box.createVerticalStrut(8));
            }
            JButton skipButton = new JButton("不作答");
            skipButton.setAlignmentX(Component.LEFT_ALIGNMENT);
            skipButton.addActionListener(e -> submit(-1));
            optionPanel.add(skipButton);
            optionPanel.updateUI();
        });
    }

    public void onResult(QuickQuizAnswerResultDTO result) {
        SwingUtilities.invokeLater(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append(result.getQuestion().getQuestion()).append("\n\n");
            List<String> options = result.getQuestion().getOptions();
            int correctIndex = result.getQuestion().getCorrectAnswerIndex();
            if (options != null && correctIndex >= 0 && correctIndex < options.size()) {
                sb.append("正确答案：").append((char) ('A' + correctIndex)).append(". ")
                        .append(options.get(correctIndex)).append("\n\n");
            }
            for (QuickQuizAnswerViewDTO answer : result.getAnswers()) {
                sb.append(answer.getUsername()).append("：").append(answer.getChoiceText())
                        .append("（").append(answer.getPointsDelta() >= 0 ? "+" : "")
                        .append(answer.getPointsDelta()).append("，总分 ")
                        .append(answer.getTotalScore()).append("）\n");
            }
            if (result.getRankings() != null && !result.getRankings().isEmpty()) {
                sb.append("\n排行：\n");
                result.getRankings().forEach(ranking -> sb.append(ranking.getUsername())
                        .append("：").append(ranking.getScore()).append(" 分")
                        .append(ranking.isWinner() ? "，胜者 +" + ranking.getRewardBones() + "🦴" : "")
                        .append("\n"));
            }
            if (!result.isFinished()) {
                sb.append("\n等待下一题...");
                if (isHomeowner()) {
                    MessageAction.send(new QuickQuizNextQuestionDTO(getRoom().getId()), Action.QUICK_QUIZ_NEXT_QUESTION);
                }
            } else if (result.getPrizePool() > 0) {
                sb.append("\n奖池：").append(result.getPrizePool()).append("🦴，胜者每人 ")
                        .append(result.getRewardPerWinner()).append("🦴");
            }
            resultArea.setText(sb.toString());
            setTitle(result.isFinished() ? "本局已完成" : "本题结束，等待下一题...");
            for (Component component : optionPanel.getComponents()) {
                component.setEnabled(false);
            }
        });
    }

    public void onRecords(List<QuickQuizRecordDTO> records) {
        SwingUtilities.invokeLater(() -> {
            List<RecordGroup> groups = groupRecordsByOpponent(records);
            if (groups.isEmpty()) {
                recordArea.setText("暂无记录");
                return;
            }
            RecordGroup group = groups.get(0);
            if (groups.size() > 1) {
                Object selected = JOptionPane.showInputDialog(
                        mainPanel,
                        "请选择要查看的对手：",
                        "我的记录",
                        JOptionPane.PLAIN_MESSAGE,
                        null,
                        groups.toArray(),
                        group);
                if (!(selected instanceof RecordGroup)) {
                    recordArea.setText("请选择一个对手查看记录");
                    return;
                }
                group = (RecordGroup) selected;
            }
            recordArea.setText(formatRecords(group.getRecords()));
        });
    }

    private void submit(int choiceIndex) {
        if (currentQuestion == null || submitted) {
            return;
        }
        List<String> options = currentQuestion.getOptions();
        if (choiceIndex >= options.size()) {
            return;
        }
        submitted = true;
        for (Component component : optionPanel.getComponents()) {
            component.setEnabled(false);
        }
        QuickQuizSubmitAnswerDTO dto = new QuickQuizSubmitAnswerDTO(
                getRoom().getId(),
                currentQuestion.getId(),
                choiceIndex,
                choiceIndex < 0 ? "不作答" : options.get(choiceIndex));
        MessageAction.send(dto, Action.QUICK_QUIZ_SUBMIT_ANSWER);
        resultArea.setText("已提交，等待所有玩家答案揭示...");
    }

    private void setTitle(String text) {
        if (titleLabel != null) {
            titleLabel.setText(text);
        }
    }

    static List<RecordGroup> groupRecordsByOpponent(List<QuickQuizRecordDTO> records) {
        Map<String, RecordGroup> groupMap = new LinkedHashMap<>();
        if (records == null) {
            return new ArrayList<>();
        }
        for (QuickQuizRecordDTO record : records) {
            String opponentKey = normalizeOpponentKey(record.getOpponentKey());
            String opponentName = normalizeOpponentName(record.getOpponentName());
            RecordGroup group = groupMap.get(opponentKey);
            if (group == null) {
                group = new RecordGroup(opponentKey, opponentName);
                groupMap.put(opponentKey, group);
            } else if (UNKNOWN_OPPONENT_NAME.equals(group.getOpponentName())
                    && !UNKNOWN_OPPONENT_NAME.equals(opponentName)) {
                group.setOpponentName(opponentName);
            }
            group.getRecords().add(record);
        }
        return new ArrayList<>(groupMap.values());
    }

    private static String formatRecords(List<QuickQuizRecordDTO> records) {
        StringBuilder sb = new StringBuilder();
        for (QuickQuizRecordDTO record : records) {
            sb.append(record.getQuestion()).append("\n");
            List<QuickQuizAnswerViewDTO> answers = record.getAnswers() == null ? new ArrayList<>() : record.getAnswers();
            for (QuickQuizAnswerViewDTO answer : answers) {
                sb.append("  ").append(answer.getUsername()).append("：").append(answer.getChoiceText())
                        .append("（").append(answer.getPointsDelta()).append("）\n");
            }
            sb.append("----------\n");
        }
        return sb.length() == 0 ? "暂无记录" : sb.toString();
    }

    private static String normalizeOpponentKey(String opponentKey) {
        return isBlank(opponentKey) ? UNKNOWN_OPPONENT_KEY : opponentKey.trim();
    }

    private static String normalizeOpponentName(String opponentName) {
        return isBlank(opponentName) ? UNKNOWN_OPPONENT_NAME : opponentName.trim();
    }

    private static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }

    static class RecordGroup {
        private final String opponentKey;
        private String opponentName;
        private final List<QuickQuizRecordDTO> records = new ArrayList<>();

        RecordGroup(String opponentKey, String opponentName) {
            this.opponentKey = opponentKey;
            this.opponentName = opponentName;
        }

        String getOpponentName() {
            return opponentName;
        }

        void setOpponentName(String opponentName) {
            this.opponentName = opponentName;
        }

        List<QuickQuizRecordDTO> getRecords() {
            return records;
        }

        @Override
        public String toString() {
            return UNKNOWN_OPPONENT_KEY.equals(opponentKey) ? opponentName : opponentName + "（" + opponentKey + "）";
        }
    }

}
