package cn.xeblog.plugin.game.tacitquiz;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.tacitquiz.*;
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
 * 默契问答。
 */
@DoGame(Game.TACIT_QUIZ)
public class TacitQuiz extends AbstractGame<TacitQuizNextQuestionDTO> {

    private static final int DEFAULT_QUESTION_COUNT = 5;
    private static final String UNKNOWN_OPPONENT_KEY = "__UNKNOWN_OPPONENT__";
    private static final String UNKNOWN_OPPONENT_NAME = "未知对手";

    private JPanel mainPanel;
    private JLabel titleLabel;
    private JPanel optionPanel;
    private JTextArea resultArea;
    private JTextArea recordArea;
    private TacitQuizQuestionDTO currentQuestion;
    private boolean submitted;
    private int selectedQuestionCount = DEFAULT_QUESTION_COUNT;

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
        MessageAction.send(new TacitQuizNextQuestionDTO(getRoom().getId()), Action.TACIT_QUIZ_NEXT_QUESTION);
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
        mainPanel.setMinimumSize(new Dimension(240, 220));
        mainPanel.setPreferredSize(new Dimension(320, 260));

        JPanel panel = new JPanel();
        panel.setBounds(10, 10, 220, 180);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("默契问答");
        title.setFont(new Font("", Font.BOLD, 15));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(12));
        panel.add(new JLabel("不限时选择答案，答完后查看双方选择。"));
        panel.add(Box.createVerticalStrut(12));

        if (DataCache.isOnline) {
            selectedQuestionCount = selectedQuestionCount <= 0 ? DEFAULT_QUESTION_COUNT : selectedQuestionCount;
            JPanel countPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            countPanel.add(new JLabel("本局题数："));
            JSpinner countSpinner = new JSpinner(new SpinnerNumberModel(selectedQuestionCount, 1, 50, 1));
            countSpinner.addChangeListener(e -> selectedQuestionCount = (Integer) countSpinner.getValue());
            countPanel.add(countSpinner);
            panel.add(countPanel);
            panel.add(Box.createVerticalStrut(8));

            JButton createRoomButton = new JButton("创建房间");
            createRoomButton.addActionListener(e -> {
                cn.xeblog.commons.entity.game.CreateGameRoomDTO dto =
                        new cn.xeblog.commons.entity.game.CreateGameRoomDTO(Game.TACIT_QUIZ, 2, "在线PK", selectedQuestionCount);
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
        recordButton.addActionListener(e -> MessageAction.send(new Object(), Action.TACIT_QUIZ_MY_RECORDS));
        buttons.add(recordButton);
        bottom.add(buttons, BorderLayout.SOUTH);

        mainPanel.add(bottom, BorderLayout.SOUTH);
        mainPanel.updateUI();
    }

    public void onQuestion(TacitQuizQuestionDTO question) {
        SwingUtilities.invokeLater(() -> {
            currentQuestion = question;
            submitted = false;
            setTitle("第 " + question.getRoundNo() + "/" + question.getTotalRounds() + " 题：" + question.getQuestion());
            resultArea.setText("");
            optionPanel.removeAll();
            List<String> options = question.getOptions() == null ? new ArrayList<>() : question.getOptions();
            for (int i = 0; i < options.size(); i++) {
                int index = i;
                JButton button = new JButton(options.get(i));
                button.setAlignmentX(Component.LEFT_ALIGNMENT);
                button.addActionListener(e -> submit(index));
                optionPanel.add(button);
                optionPanel.add(Box.createVerticalStrut(8));
            }
            optionPanel.updateUI();
        });
    }

    public void onResult(TacitQuizAnswerResultDTO result) {
        SwingUtilities.invokeLater(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append(result.getQuestion().getQuestion()).append("\n\n");
            for (TacitQuizAnswerViewDTO answer : result.getAnswers()) {
                sb.append(answer.getUsername()).append("：").append(answer.getChoiceText()).append("\n");
            }
            if (!result.isFinished()) {
                sb.append("\n等待下一题...");
            }
            resultArea.setText(sb.toString());
            setTitle(result.isFinished() ? "本局已完成" : "本题结束，等待下一题...");
            for (Component component : optionPanel.getComponents()) {
                component.setEnabled(false);
            }
        });
    }

    public void onRecords(List<TacitQuizRecordDTO> records) {
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
        if (choiceIndex < 0 || choiceIndex >= options.size()) {
            return;
        }
        submitted = true;
        for (Component component : optionPanel.getComponents()) {
            component.setEnabled(false);
        }
        TacitQuizSubmitAnswerDTO dto = new TacitQuizSubmitAnswerDTO(
                getRoom().getId(),
                currentQuestion.getId(),
                choiceIndex,
                options.get(choiceIndex));
        MessageAction.send(dto, Action.TACIT_QUIZ_SUBMIT_ANSWER);
        resultArea.setText("已提交，等待双方答案揭示...");
    }

    private void setTitle(String text) {
        if (titleLabel != null) {
            titleLabel.setText(text);
        }
    }

    static List<RecordGroup> groupRecordsByOpponent(List<TacitQuizRecordDTO> records) {
        Map<String, RecordGroup> groupMap = new LinkedHashMap<>();
        if (records == null) {
            return new ArrayList<>();
        }
        for (TacitQuizRecordDTO record : records) {
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

    private static String formatRecords(List<TacitQuizRecordDTO> records) {
        StringBuilder sb = new StringBuilder();
        for (TacitQuizRecordDTO record : records) {
            sb.append(record.getQuestion()).append("\n");
            List<TacitQuizAnswerViewDTO> answers = record.getAnswers() == null ? new ArrayList<>() : record.getAnswers();
            for (TacitQuizAnswerViewDTO answer : answers) {
                sb.append("  ").append(answer.getUsername()).append("：").append(answer.getChoiceText()).append("\n");
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
        private final List<TacitQuizRecordDTO> records = new ArrayList<>();

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

        List<TacitQuizRecordDTO> getRecords() {
            return records;
        }

        @Override
        public String toString() {
            return UNKNOWN_OPPONENT_KEY.equals(opponentKey) ? opponentName : opponentName + "（" + opponentKey + "）";
        }
    }

}
