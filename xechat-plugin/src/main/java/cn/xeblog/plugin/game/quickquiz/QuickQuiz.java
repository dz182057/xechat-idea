package cn.xeblog.plugin.game.quickquiz;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.GameRoomMsgDTO;
import cn.xeblog.commons.entity.game.quickquiz.*;
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
 * 快问快答。
 */
@DoGame(Game.QUICK_QUIZ)
public class QuickQuiz extends AbstractGame<QuickQuizNextQuestionDTO> {

    private JPanel mainPanel;
    private JLabel titleLabel;
    private JLabel timerLabel;
    private JPanel optionPanel;
    private JTextArea resultArea;
    private JTextArea recordArea;
    private JButton readyButton;
    private QuickQuizQuestionDTO currentQuestion;
    private boolean submitted;
    private Timer timer;
    private int selectedQuestionCount = 5;

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
        mainPanel.setMinimumSize(new Dimension(240, 220));
        mainPanel.setPreferredSize(new Dimension(320, 260));

        JPanel panel = new JPanel();
        panel.setBounds(10, 10, 220, 180);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("快问快答");
        title.setFont(new Font("", Font.BOLD, 15));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(12));
        panel.add(new JLabel("10 秒内选择答案，答完后查看双方选择。"));
        panel.add(Box.createVerticalStrut(12));

        if (DataCache.isOnline) {
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
                        new cn.xeblog.commons.entity.game.CreateGameRoomDTO(Game.QUICK_QUIZ, 2, "在线PK", selectedQuestionCount);
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
        timerLabel = new JLabel("", JLabel.CENTER);
        timerLabel.setForeground(new Color(239, 106, 106));
        top.add(titleLabel, BorderLayout.CENTER);
        top.add(timerLabel, BorderLayout.EAST);
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
        readyButton = new JButton("准备下一题");
        readyButton.setEnabled(false);
        readyButton.addActionListener(e -> readyNext());
        JButton recordButton = new JButton("查看我的记录");
        recordButton.addActionListener(e -> MessageAction.send(new Object(), Action.QUICK_QUIZ_MY_RECORDS));
        buttons.add(readyButton);
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
            readyButton.setEnabled(false);
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
            startTimer(question.getDeadlineAt());
        });
    }

    public void onResult(QuickQuizAnswerResultDTO result) {
        SwingUtilities.invokeLater(() -> {
            stopTimer();
            StringBuilder sb = new StringBuilder();
            sb.append(result.getQuestion().getQuestion()).append("\n\n");
            for (QuickQuizAnswerViewDTO answer : result.getAnswers()) {
                sb.append(answer.getUsername()).append("：").append(answer.getChoiceText()).append("\n");
            }
            resultArea.setText(sb.toString());
            readyButton.setEnabled(!result.isFinished());
            setTitle(result.isFinished() ? "本局已完成" : "本题结束，可准备下一题");
            for (Component component : optionPanel.getComponents()) {
                component.setEnabled(false);
            }
        });
    }

    public void onRecords(List<QuickQuizRecordDTO> records) {
        SwingUtilities.invokeLater(() -> {
            StringBuilder sb = new StringBuilder();
            for (QuickQuizRecordDTO record : records) {
                sb.append(record.getQuestion()).append("\n");
                for (QuickQuizAnswerViewDTO answer : record.getAnswers()) {
                    sb.append("  ").append(answer.getUsername()).append("：").append(answer.getChoiceText()).append("\n");
                }
                sb.append("----------\n");
            }
            recordArea.setText(sb.length() == 0 ? "暂无记录" : sb.toString());
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
        QuickQuizSubmitAnswerDTO dto = new QuickQuizSubmitAnswerDTO(
                getRoom().getId(),
                currentQuestion.getId(),
                choiceIndex,
                options.get(choiceIndex));
        MessageAction.send(dto, Action.QUICK_QUIZ_SUBMIT_ANSWER);
        resultArea.setText("已提交，等待双方答案揭示...");
    }

    private void readyNext() {
        if (getRoom() == null) {
            return;
        }
        GameRoomMsgDTO msg = new GameRoomMsgDTO();
        msg.setRoomId(getRoom().getId());
        msg.setGame(Game.QUICK_QUIZ);
        msg.setMsgType(GameRoomMsgDTO.MsgType.PLAYER_READY);
        MessageAction.send(msg, Action.GAME_ROOM);
        readyButton.setEnabled(false);
        setTitle("已准备，等待其他玩家...");
    }

    private void startTimer(long deadlineAt) {
        stopTimer();
        timer = new Timer(250, e -> {
            long left = Math.max(0, deadlineAt - System.currentTimeMillis());
            timerLabel.setText((int) Math.ceil(left / 1000.0) + "s");
            if (left <= 0) {
                stopTimer();
                for (Component component : optionPanel.getComponents()) {
                    component.setEnabled(false);
                }
                if (!submitted) {
                    resultArea.setText("已超时，等待结算...");
                }
            }
        });
        timer.start();
    }

    private void stopTimer() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
        if (timerLabel != null) {
            timerLabel.setText("");
        }
    }

    private void setTitle(String text) {
        if (titleLabel != null) {
            titleLabel.setText(text);
        }
    }

}
