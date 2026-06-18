package cn.xeblog.plugin.game.dograce;

import cn.xeblog.commons.entity.game.CreateGameRoomDTO;
import cn.xeblog.commons.entity.game.dograce.DogRaceDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.Game;
import cn.xeblog.plugin.action.MessageAction;
import cn.xeblog.plugin.annotation.DoGame;
import cn.xeblog.plugin.cache.DataCache;
import cn.xeblog.plugin.game.AbstractGame;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

@DoGame(Game.DOG_RACE)
public class DogRace extends AbstractGame<DogRaceDTO> {

    private static final int TRACK_LENGTH = 16;
    private static final String RULE_HINT = "持狗报名 🦴20 + 活力 3 · 赛段注 🦴10 · 暗注 🦴20 · 骨头地块 +🦴5 · 催骰 5s";

    private JPanel mainPanel;
    private JLabel titleLabel;
    private JComboBox<String> dogSelect;
    private JComboBox<String> finalBetSelect;
    private JSpinner tileCellSpinner;
    private JComboBox<String> tileTypeSelect;
    private JTextArea trackArea;
    private JTextArea participantArea;
    private JTextArea broadcastArea;
    private DogRaceDTO latest;
    private String selectedMode = "pure_betting";

    @Override
    protected void init() {
        showStartPanel();
    }

    @Override
    protected void start() {
        showPlayPanel();
    }

    @Override
    public void handle(DogRaceDTO body) {
        if (body == null || body.getEvent() == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            latest = body;
            if (mainPanel == null || trackArea == null) {
                showPlayPanel();
            }
            refreshRaceView();
        });
    }

    @Override
    protected JPanel getComponent() {
        return mainPanel;
    }

    public DogRaceDTO getLatestForTest() {
        return latest;
    }

    public String getRuleHintForTest() {
        return RULE_HINT;
    }

    private void showStartPanel() {
        mainPanel = new JPanel();
        mainPanel.setLayout(null);
        mainPanel.setMinimumSize(new Dimension(260, 240));
        mainPanel.setPreferredSize(new Dimension(320, 280));

        JPanel panel = new JPanel();
        panel.setBounds(10, 10, 260, 220);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("狗狗赛跑");
        title.setFont(new Font("", Font.BOLD, 15));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(10));
        panel.add(new JLabel("5 只狗叠罗汉赛跑，支持下注、地块和催骰。"));
        panel.add(Box.createVerticalStrut(10));

        if (DataCache.isOnline) {
            JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            modePanel.add(new JLabel("赛跑模式："));
            JComboBox<String> modeBox = new JComboBox<>(new String[]{"纯下注", "持狗参赛"});
            modeBox.addActionListener(e -> selectedMode = modeBox.getSelectedIndex() == 1 ? "owned_dog" : "pure_betting");
            modePanel.add(modeBox);
            panel.add(modePanel);
            panel.add(Box.createVerticalStrut(8));

            JButton createRoomButton = new JButton("创建房间");
            createRoomButton.addActionListener(e -> {
                CreateGameRoomDTO dto = new CreateGameRoomDTO(Game.DOG_RACE, 6, "在线PK");
                dto.setDogRaceMode(selectedMode);
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
        mainPanel.setLayout(new BorderLayout(8, 8));
        mainPanel.setMinimumSize(new Dimension(420, 420));
        mainPanel.setPreferredSize(new Dimension(520, 560));

        titleLabel = new JLabel("等待服务端开始狗狗赛跑", JLabel.CENTER);
        titleLabel.setFont(new Font("", Font.BOLD, 14));
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(1, 2, 8, 8));
        trackArea = readonlyArea();
        participantArea = readonlyArea();
        center.add(new JScrollPane(trackArea));
        center.add(new JScrollPane(participantArea));
        mainPanel.add(center, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(6, 6));
        bottom.add(buildActionPanel(), BorderLayout.NORTH);
        broadcastArea = readonlyArea();
        broadcastArea.setRows(6);
        bottom.add(new JScrollPane(broadcastArea), BorderLayout.CENTER);
        mainPanel.add(bottom, BorderLayout.SOUTH);
        mainPanel.updateUI();
    }

    private JPanel buildActionPanel() {
        JPanel panel = new JPanel(new GridLayout(4, 1, 4, 4));

        JLabel ruleHint = new JLabel(RULE_HINT);
        panel.add(ruleHint);

        JPanel targetPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        dogSelect = new JComboBox<>();
        finalBetSelect = new JComboBox<>(new String[]{"冠军", "垫底"});
        targetPanel.add(new JLabel("目标狗狗"));
        targetPanel.add(dogSelect);
        JButton legBetButton = new JButton("赛段下注");
        legBetButton.addActionListener(e -> sendDogAction(DogRaceDTO.Event.BET_LEG_REQ));
        JButton finalBetButton = new JButton("暗注");
        finalBetButton.addActionListener(e -> sendDogAction(DogRaceDTO.Event.BET_FINAL_REQ));
        targetPanel.add(legBetButton);
        targetPanel.add(finalBetSelect);
        targetPanel.add(finalBetButton);
        panel.add(targetPanel);

        JPanel tilePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        tileCellSpinner = new JSpinner(new SpinnerNumberModel(8, 2, 15, 1));
        tileTypeSelect = new JComboBox<>(new String[]{"骨头", "泥坑"});
        JButton tileButton = new JButton("放地块");
        tileButton.addActionListener(e -> sendTileAction());
        tilePanel.add(new JLabel("地块"));
        tilePanel.add(tileCellSpinner);
        tilePanel.add(tileTypeSelect);
        tilePanel.add(tileButton);
        panel.add(tilePanel);

        JPanel rollPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton rollButton = new JButton("催一下");
        rollButton.addActionListener(e -> sendRequest(DogRaceDTO.Event.ROLL_REQ));
        rollPanel.add(rollButton);
        rollPanel.add(getGameOverButton());
        panel.add(rollPanel);
        return panel;
    }

    private JTextArea readonlyArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return area;
    }

    private void sendDogAction(DogRaceDTO.Event event) {
        Object selected = dogSelect == null ? null : dogSelect.getSelectedItem();
        if (selected == null) {
            return;
        }
        DogRaceDTO.Participant dog = findDogByLabel(selected.toString());
        if (dog == null) {
            return;
        }
        DogRaceDTO dto = new DogRaceDTO(getRoom().getId());
        dto.setEvent(event);
        dto.setDogId(dog.getDogId());
        if (event == DogRaceDTO.Event.BET_FINAL_REQ) {
            dto.setBetKind(finalBetSelect.getSelectedIndex() == 1 ? "last" : "champion");
        }
        sendMsg(dto);
    }

    private void sendTileAction() {
        DogRaceDTO dto = new DogRaceDTO(getRoom().getId());
        dto.setEvent(DogRaceDTO.Event.PLACE_TILE_REQ);
        dto.setCell((Integer) tileCellSpinner.getValue());
        dto.setTileType(tileTypeSelect.getSelectedIndex() == 1 ? "mud" : "bone");
        sendMsg(dto);
    }

    private void sendRequest(DogRaceDTO.Event event) {
        DogRaceDTO dto = new DogRaceDTO(getRoom().getId());
        dto.setEvent(event);
        sendMsg(dto);
    }

    private DogRaceDTO.Participant findDogByLabel(String label) {
        if (latest == null || latest.getParticipants() == null) {
            return null;
        }
        for (DogRaceDTO.Participant dog : latest.getParticipants()) {
            if (label.equals(dogLabel(dog))) {
                return dog;
            }
        }
        return null;
    }

    private void refreshRaceView() {
        titleLabel.setText(formatTitle());
        refreshDogSelect();
        trackArea.setText(formatTrack());
        participantArea.setText(formatParticipants());
        broadcastArea.setText(formatBroadcasts());
        mainPanel.updateUI();
    }

    private void refreshDogSelect() {
        if (dogSelect == null || latest == null || latest.getParticipants() == null) {
            return;
        }
        Object selected = dogSelect.getSelectedItem();
        dogSelect.removeAllItems();
        for (DogRaceDTO.Participant dog : latest.getParticipants()) {
            dogSelect.addItem(dogLabel(dog));
        }
        if (selected != null) {
            dogSelect.setSelectedItem(selected);
        }
    }

    private String formatTitle() {
        if (latest == null) {
            return "等待服务端开始狗狗赛跑";
        }
        String mode = "owned_dog".equals(latest.getMode()) ? "持狗参赛" : "纯下注";
        return mode + " · 第 " + Math.max(1, latest.getLegNo()) + " 赛段 · " + phaseText(latest.getPhase());
    }

    private String formatTrack() {
        StringBuilder sb = new StringBuilder("赛道\n");
        for (int cell = 1; cell <= TRACK_LENGTH; cell++) {
            sb.append(String.format("%02d ", cell));
            appendUnitsAt(sb, cell);
            sb.append("\n");
        }
        return sb.toString();
    }

    private void appendUnitsAt(StringBuilder sb, int cell) {
        if (latest == null) {
            return;
        }
        if (latest.getTiles() != null) {
            for (DogRaceDTO.Tile tile : latest.getTiles()) {
                if (tile.getCell() == cell) {
                    sb.append("bone".equals(tile.getTileType()) ? "🦴 " : "泥坑 ");
                }
            }
        }
        if (latest.getParticipants() != null) {
            for (DogRaceDTO.Participant dog : latest.getParticipants()) {
                if (dog.getPosition() == cell) {
                    sb.append("🐶").append(dog.getSlot()).append(" ");
                }
            }
        }
        if (latest.getCats() != null) {
            for (DogRaceDTO.Cat cat : latest.getCats()) {
                if (cat.getPosition() == cell) {
                    sb.append("猫 ");
                }
            }
        }
    }

    private String formatParticipants() {
        if (latest == null || latest.getParticipants() == null || latest.getParticipants().isEmpty()) {
            return "等待参赛狗列表";
        }
        StringBuilder sb = new StringBuilder("参赛狗\n");
        for (DogRaceDTO.Participant dog : latest.getParticipants()) {
            sb.append(dogLabel(dog))
                    .append(" · 第 ").append(dog.getPosition()).append(" 格");
            if (dog.isSkillTriggered() && dog.getSkillName() != null) {
                sb.append(" · 已触发 ").append(dog.getSkillName());
            }
            if (dog.getRank() != null) {
                sb.append(" · #").append(dog.getRank());
            }
            sb.append("\n");
        }
        if (latest.getRankings() != null && !latest.getRankings().isEmpty()) {
            sb.append("\n结算\n");
            for (DogRaceDTO.Ranking ranking : latest.getRankings()) {
                sb.append("#").append(ranking.getRank())
                        .append(" · ").append(findDogName(ranking.getDogId()))
                        .append(" · 骨头 ").append(ranking.getRewardBones() == null ? 0 : ranking.getRewardBones())
                        .append(" · 周榜 ").append(ranking.getWeeklyPoints() == null ? 0 : ranking.getWeeklyPoints())
                        .append("\n");
            }
        }
        return sb.toString();
    }

    private String formatBroadcasts() {
        if (latest == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (latest.getSkillName() != null) {
            sb.append("品种技能触发：").append(latest.getSkillName()).append("\n");
        }
        if (latest.getBroadcast() != null) {
            sb.append(latest.getBroadcast()).append("\n");
        }
        List<String> broadcasts = latest.getBroadcasts();
        if (broadcasts != null && !broadcasts.isEmpty()) {
            sb.append(broadcasts.stream().collect(Collectors.joining("\n")));
        }
        if (latest.getMessage() != null) {
            sb.append(latest.getMessage());
        }
        return sb.toString();
    }

    private String dogLabel(DogRaceDTO.Participant dog) {
        return dog.getSlot() + "号 " + dog.getName();
    }

    private String findDogName(String dogId) {
        if (latest == null || latest.getParticipants() == null) {
            return "狗狗";
        }
        for (DogRaceDTO.Participant dog : latest.getParticipants()) {
            if (dog.getDogId().equals(dogId)) {
                return dog.getName();
            }
        }
        return "狗狗";
    }

    private String phaseText(String phase) {
        if ("running".equals(phase)) {
            return "比赛中";
        }
        if ("legSettle".equals(phase)) {
            return "赛段结算";
        }
        if ("raceSettle".equals(phase)) {
            return "最终结算";
        }
        if ("ready".equals(phase)) {
            return "等待开赛";
        }
        return "等待服务端";
    }
}
