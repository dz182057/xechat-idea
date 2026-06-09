package cn.xeblog.plugin.game.minesweeper;

import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.game.GameRoomMsgDTO;
import cn.xeblog.commons.entity.game.minesweeper.MinesweeperCellDTO;
import cn.xeblog.commons.entity.game.minesweeper.MinesweeperDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.Game;
import cn.xeblog.plugin.action.GameAction;
import cn.xeblog.plugin.action.MessageAction;
import cn.xeblog.plugin.annotation.DoGame;
import cn.xeblog.plugin.cache.DataCache;
import cn.xeblog.plugin.game.AbstractGame;
import com.intellij.openapi.ui.ComboBox;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @description:
 * @author: sherlock
 * @date: 2023-09-11 11:25:53
 */
@DoGame(Game.MINESWEEPER)
public class Minesweeper extends AbstractGame<MinesweeperDTO> {

    static final String COOP_GAME_MODE = "合作排雷";

    private int           level;

    private MinesweeperUI minesweeperUI;

    private boolean       init;

    /** 菜单控件 */
    JMenuItem             jmi_easy, jmi_normal, jmi_hard;

    private JPanel mainPanel;

    private JPanel coopPanel;

    private JPanel coopGridPanel;

    private JLabel coopTitleLabel;

    private JLabel coopStatusLabel;

    private JLabel coopMineLabel;

    private JLabel coopTimeLabel;

    private JButton coopRestartButton;

    private JButton coopSharedMarkButton;

    private JButton coopModeButton;

    private JButton coopHelpButton;

    private JComboBox<String> coopSizePresetBox;

    private JSpinner coopRowsSpinner;

    private JSpinner coopColsSpinner;

    private JSpinner coopMinesSpinner;

    private JButton coopSizeApplyButton;

    private JPanel coopRestartResponsePanel;

    private JLabel coopRestartResponseLabel;

    private JButton coopRestartApproveButton;

    private JButton coopRestartRejectButton;

    private JButton[][] coopButtons;

    private CoopCell[][] coopCells;

    private int coopRows = 9;

    private int coopCols = 9;

    private int coopMines = 10;

    private boolean coopSharedMarkMode;

    private boolean coopConcealedMode = true;

    private String coopTurnPlayerKey;

    private String coopHostPlayerKey;

    private boolean coopBoardGenerated;

    private boolean coopRoundActive;

    private boolean coopAwaitingRestartResponse;

    private String coopPendingRestartFromKey;

    private String coopPendingRestartFromName;

    private boolean coopAwaitingConfigResponse;

    private String coopPendingConfigFromKey;

    private String coopPendingConfigFromName;

    private BoardConfig coopPendingConfig;

    private long coopStartedAt;

    private javax.swing.Timer coopTimer;

    private MinesweeperDTO.Phase coopPhase = MinesweeperDTO.Phase.playing;

    @Override
    protected void start() {
        if (isCoopRoom()) {
            startCoop();
            return;
        }
        startSingle();
    }

    private void startSingle() {
        stopCoopTimer();
        initPanel();
        mainPanel.setLayout(new BorderLayout());

        coopPanel = new JPanel(new BorderLayout());
        coopConcealedMode = true;
        coopSharedMarkMode = false;
        coopRoundActive = false;
        coopBoardGenerated = false;
        coopPhase = MinesweeperDTO.Phase.playing;
        coopStartedAt = System.currentTimeMillis();

        JPanel topPanel = new JPanel();
        coopTitleLabel = new JLabel(createConcealedToolbarLabels(false).title);
        coopStatusLabel = new JLabel("Ready");
        coopMineLabel = new JLabel(formatMineLabel(coopMines));
        coopMineLabel.setToolTipText("剩余雷数");
        coopTimeLabel = new JLabel(formatTimeLabel(0));
        coopTimeLabel.setToolTipText("计时");
        topPanel.add(coopTitleLabel);
        topPanel.add(coopStatusLabel);
        topPanel.add(coopMineLabel);

        coopRestartButton = new JButton(createConcealedToolbarLabels(false).restart);
        coopRestartButton.setToolTipText("重开本局");
        coopRestartButton.addActionListener(e -> restartCoopRound());
        topPanel.add(coopRestartButton);
        topPanel.add(coopTimeLabel);

        coopModeButton = new JButton(createConcealedToolbarLabels(false).mode);
        coopModeButton.addActionListener(e -> {
            coopConcealedMode = !coopConcealedMode;
            renderCoopGrid();
            updateCoopStatus(coopPhase, false, false);
        });
        topPanel.add(coopModeButton);

        coopHelpButton = new JButton("?");
        coopHelpButton.setFocusable(false);
        coopHelpButton.setToolTipText(getCoopHelpText());
        topPanel.add(coopHelpButton);

        initCoopSizeControls(topPanel);

        coopGridPanel = new JPanel();
        coopPanel.add(topPanel, BorderLayout.NORTH);
        coopPanel.add(createCoopGridScrollPane(coopGridPanel), BorderLayout.CENTER);
        mainPanel.add(coopPanel, BorderLayout.CENTER);
        coopCells = null;
        ensureCoopCells(coopRows, coopCols, coopMines);
        renderCoopGrid();
        startCoopTimer();
        mainPanel.setMinimumSize(new Dimension(420, 360));
        mainPanel.updateUI();
        init = false;
    }

    private void startCoop() {
        stopCoopTimer();
        initPanel();
        mainPanel.setLayout(new BorderLayout());

        coopPanel = new JPanel(new BorderLayout());
        coopConcealedMode = true;
        coopRoundActive = false;
        JPanel topPanel = new JPanel();
        coopTitleLabel = new JLabel(createConcealedToolbarLabels(false).title);
        coopStatusLabel = new JLabel("等待同步...");
        coopMineLabel = new JLabel(formatMineLabel(coopMines));
        coopMineLabel.setToolTipText("剩余雷数");
        coopTimeLabel = new JLabel(formatTimeLabel(0));
        coopTimeLabel.setToolTipText("计时");
        topPanel.add(coopTitleLabel);
        topPanel.add(coopStatusLabel);
        topPanel.add(coopMineLabel);

        coopRestartButton = new JButton(createConcealedToolbarLabels(false).restart);
        coopRestartButton.setToolTipText("申请重开本局");
        coopRestartButton.addActionListener(e -> restartCoopRound());
        topPanel.add(coopRestartButton);
        topPanel.add(coopTimeLabel);

        coopSharedMarkButton = new JButton(createConcealedToolbarLabels(false).sharedMark);
        coopSharedMarkButton.addActionListener(e -> {
            coopSharedMarkMode = !coopSharedMarkMode;
            refreshCoopControls();
        });
        topPanel.add(coopSharedMarkButton);

        coopModeButton = new JButton(createConcealedToolbarLabels(false).mode);
        coopModeButton.addActionListener(e -> {
            coopConcealedMode = !coopConcealedMode;
            renderCoopGrid();
            updateCoopStatus(coopPhase, false, false);
        });
        topPanel.add(coopModeButton);

        coopHelpButton = new JButton("?");
        coopHelpButton.setFocusable(false);
        coopHelpButton.setToolTipText(getCoopHelpText());
        topPanel.add(coopHelpButton);

        initCoopSizeControls(topPanel);

        coopRestartResponsePanel = new JPanel();
        coopRestartResponseLabel = new JLabel("对方申请重开");
        coopRestartResponsePanel.add(coopRestartResponseLabel);
        coopRestartApproveButton = new JButton("同意");
        coopRestartApproveButton.addActionListener(e -> respondPendingCoopRequest(true));
        coopRestartRejectButton = new JButton("不同意");
        coopRestartRejectButton.addActionListener(e -> respondPendingCoopRequest(false));
        coopRestartResponsePanel.add(coopRestartApproveButton);
        coopRestartResponsePanel.add(coopRestartRejectButton);
        coopRestartResponsePanel.setVisible(false);

        coopGridPanel = new JPanel();
        coopPanel.add(topPanel, BorderLayout.NORTH);
        coopPanel.add(coopRestartResponsePanel, BorderLayout.SOUTH);
        coopPanel.add(createCoopGridScrollPane(coopGridPanel), BorderLayout.CENTER);
        mainPanel.add(coopPanel, BorderLayout.CENTER);
        ensureCoopCells(coopRows, coopCols, coopMines);
        renderCoopGrid();
        startCoopTimer();
        mainPanel.setMinimumSize(new Dimension(420, 360));
        mainPanel.updateUI();
        init = false;
    }

    @Override
    protected void init() {
        // 是否初始化
        init = true;
        level = 1;

        initPanel();

        mainPanel.setMinimumSize(new Dimension(190, 330));
        mainPanel.setPreferredSize(new Dimension(190, 330));
        JPanel menuJPanel = new JPanel();
        menuJPanel.setBounds(10, 10, 170, 330);
        mainPanel.add(menuJPanel);

        JLabel title = new JLabel("扫雷");
        title.setFont(new Font("", Font.BOLD, 14));
        menuJPanel.add(title);

        Box vBox = Box.createVerticalBox();
        menuJPanel.add(vBox);

        Dimension selectDimension = new Dimension(30, 30);

        vBox.add(Box.createVerticalStrut(20));
        JLabel levelLabel = new JLabel("难度：");
        levelLabel.setFont(new Font("", Font.BOLD, 13));
        vBox.add(levelLabel);
        vBox.add(Box.createVerticalStrut(5));

        ComboBox<String> gameLevelBox = getComboBox(selectDimension);
        gameLevelBox.addActionListener(l -> level = gameLevelBox.getSelectedIndex() + 1);
        vBox.add(gameLevelBox);

        vBox.add(Box.createVerticalStrut(10));
        vBox.add(getStartJButton("开始游戏"));
        if (DataCache.isOnline) {
            List<Integer> numsList = new ArrayList<>();
            numsList.add(2);
            vBox.add(Box.createVerticalStrut(5));
            vBox.add(getCreateRoomButton(numsList, createRoomModeList()));
        }
        vBox.add(getExitButton());

        mainPanel.updateUI();
    }

    static List<String> createRoomModeList() {
        return Collections.singletonList(COOP_GAME_MODE);
    }

    @Override
    protected JPanel getComponent() {
        return mainPanel;
    }

    protected void initPanel() {
        if (mainPanel == null) {
            mainPanel = new JPanel();
        }

        mainPanel.removeAll();
        mainPanel.setLayout(null);
        mainPanel.setPreferredSize(null);
        mainPanel.setEnabled(true);
        mainPanel.setVisible(true);
    }

    // 创建按钮面板
    private JPanel getBottomPanel() {
        JPanel jPanel = new JPanel();
        jPanel.add(getStartJButton("重置本关"));
        jPanel.add(getMenuJButton());
        return jPanel;
    }

    public JButton getMenuJButton() {
        JButton menu = new JButton("主菜单");
        menu.addActionListener(e -> init());
        return menu;
    }

    public JButton getStartJButton(String title) {
        JButton another = new JButton(title);
        another.addActionListener(e -> start());
        return another;
    }

    public ComboBox<String> getComboBox(Dimension dimension) {
        ComboBox<String> comboBox = new ComboBox<>();
        comboBox.setPreferredSize(dimension);
        comboBox.addItem("简单");
        comboBox.addItem("中等");
        comboBox.addItem("困难");
        comboBox.setSelectedItem(this.getLevelStr(level));
        return comboBox;
    }

    private String getLevelStr(int levelInt) {
        switch (levelInt) {
            case 1:
                return "简单";
            case 2:
                return "中等";
            case 3:
                return "困难";
            default:
                return "简单";
        }
    }

    @Override
    protected void allPlayersGameStarted() {
        if (!isCoopRoom() || !isHomeowner()) {
            return;
        }
        coopHostPlayerKey = getHomeownerKey();
        coopTurnPlayerKey = coopHostPlayerKey;
        coopPhase = MinesweeperDTO.Phase.playing;
        coopRoundActive = false;
        coopBoardGenerated = false;
        coopAwaitingRestartResponse = false;
        coopPendingRestartFromKey = null;
        coopPendingRestartFromName = null;
        coopStartedAt = System.currentTimeMillis();

        MinesweeperDTO dto = new MinesweeperDTO();
        dto.setGame(Game.MINESWEEPER);
        dto.setEvent(MinesweeperDTO.Event.INIT);
        dto.setRows(coopRows);
        dto.setCols(coopCols);
        dto.setMines(coopMines);
        dto.setCells(new ArrayList<>());
        dto.setActorKey(coopHostPlayerKey);
        dto.setNextTurnPlayerKey(coopTurnPlayerKey);
        dto.setPhase(MinesweeperDTO.Phase.playing);
        sendMsg(dto);
        applyCoopMessage(dto);
    }

    @Override
    public void handle(MinesweeperDTO body) {
        if (body == null || !isCoopRoom()) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (body.getEvent() == MinesweeperDTO.Event.ACTION_REQUEST) {
                handleCoopActionRequest(body);
            } else if (body.getEvent() == MinesweeperDTO.Event.SYNC_REQUEST) {
                sendCoopSnapshot();
            } else if (body.getEvent() == MinesweeperDTO.Event.RESTART_REQUEST) {
                if (body.getRows() != null && body.getCols() != null && body.getMines() != null) {
                    handleCoopConfigRequest(body);
                } else {
                    handleCoopRestartRequest(body);
                }
            } else if (body.getEvent() == MinesweeperDTO.Event.RESTART_RESPONSE) {
                if (body.getRows() != null && body.getCols() != null && body.getMines() != null) {
                    handleCoopConfigResponse(body);
                } else {
                    handleCoopRestartResponse(body);
                }
            } else {
                applyCoopMessage(body);
            }
        });
    }

    private void handleCoopActionRequest(MinesweeperDTO body) {
        if (!isHomeowner()) {
            return;
        }
        String actorKey = body.getActorKey();
        if (actorKey == null || !actorKey.equals(coopTurnPlayerKey)) {
            return;
        }
        if (body.getX() == null || body.getY() == null || body.getAction() == null) {
            return;
        }
        ensureAuthorityBoard(body.getX(), body.getY());
        OpenResult result = body.getAction() == MinesweeperDTO.ActionType.OPEN_AROUND
                ? openAround(body.getX(), body.getY())
                : openCell(body.getX(), body.getY());
        if (result.openedCount > 0 && result.phase == MinesweeperDTO.Phase.playing) {
            coopTurnPlayerKey = getOtherPlayerKey(actorKey);
        }
        sendCoopState(result, actorKey);
        updateCoopStatus(result.phase, result.hitMine, result.won);
        renderCoopGrid();
    }

    private void applyCoopMessage(MinesweeperDTO body) {
        if (body.getRows() != null && body.getCols() != null && body.getMines() != null) {
            ensureCoopCells(body.getRows(), body.getCols(), body.getMines());
        }
        coopHostPlayerKey = getHomeownerKey();
        if (body.getEvent() == MinesweeperDTO.Event.INIT) {
            coopAwaitingRestartResponse = false;
            coopPendingRestartFromKey = null;
            coopPendingRestartFromName = null;
            coopAwaitingConfigResponse = false;
            coopPendingConfigFromKey = null;
            coopPendingConfigFromName = null;
            coopPendingConfig = null;
            coopBoardGenerated = false;
            coopRoundActive = false;
            coopPhase = MinesweeperDTO.Phase.playing;
            coopStartedAt = System.currentTimeMillis();
        }
        if (body.getNextTurnPlayerKey() != null) {
            coopTurnPlayerKey = body.getNextTurnPlayerKey();
        }
        if (body.getCells() != null) {
            Set<String> personalMarks = collectPersonalMarks();
            for (MinesweeperCellDTO cellDTO : body.getCells()) {
                if (!inBounds(cellDTO.getX(), cellDTO.getY())) {
                    continue;
                }
                CoopCell cell = coopCells[cellDTO.getY()][cellDTO.getX()];
                cell.opened = cellDTO.isOpened();
                cell.adjacentMines = cellDTO.getAdjacentMines() == null ? cell.adjacentMines : cellDTO.getAdjacentMines();
                cell.sharedMarked = Boolean.TRUE.equals(cellDTO.getSharedMarked());
                cell.hasMine = Boolean.TRUE.equals(cellDTO.getHasMine()) || cell.hasMine && cellDTO.getHasMine() == null;
                cell.exploded = Boolean.TRUE.equals(cellDTO.getExploded());
            }
            restorePersonalMarks(personalMarks);
        }
        updateCoopStatus(body.getPhase(), Boolean.TRUE.equals(body.getHitMine()), Boolean.TRUE.equals(body.getWon()));
        renderCoopGrid();
    }

    private void ensureCoopCells(int rows, int cols, int mines) {
        if (coopCells != null && coopRows == rows && coopCols == cols && coopMines == mines) {
            return;
        }
        coopRows = rows;
        coopCols = cols;
        coopMines = mines;
        coopCells = new CoopCell[rows][cols];
        coopButtons = new JButton[rows][cols];
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                coopCells[y][x] = new CoopCell(x, y);
            }
        }
    }

    private void renderCoopGrid() {
        if (coopGridPanel == null || coopCells == null) {
            return;
        }
        coopGridPanel.removeAll();
        coopGridPanel.setLayout(new GridLayout(coopRows, coopCols, 1, 1));
        for (int y = 0; y < coopRows; y++) {
            for (int x = 0; x < coopCols; x++) {
                JButton button = createCoopButton(x, y);
                coopButtons[y][x] = button;
                coopGridPanel.add(button);
            }
        }
        if (coopMineLabel != null) {
            coopMineLabel.setText(formatMineLabel(Math.max(0, coopMines - countMarks())));
        }
        refreshCoopControls();
        coopGridPanel.revalidate();
        coopGridPanel.repaint();
    }

    private JScrollPane createCoopGridScrollPane(JPanel gridPanel) {
        JScrollPane scrollPane = new JScrollPane(gridPanel);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(null);
        return scrollPane;
    }

    private JButton createCoopButton(int x, int y) {
        CoopCell cell = coopCells[y][x];
        JButton button = new JButton(renderCellText(cell));
        button.setPreferredSize(new Dimension(28, 28));
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setFont(new Font("", Font.BOLD, 12));
        button.setEnabled(canOperate() || cell.opened);
        styleCoopButton(button, cell);
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!canOperate()) {
                    return;
                }
                if (SwingUtilities.isRightMouseButton(e)) {
                    togglePersonalMark(x, y);
                    return;
                }
                if (e.getClickCount() >= 2) {
                    submitCoopAction(MinesweeperDTO.ActionType.OPEN_AROUND, x, y);
                    return;
                }
                if (coopSharedMarkMode) {
                    toggleSharedMark(x, y);
                    return;
                }
                submitCoopAction(MinesweeperDTO.ActionType.OPEN, x, y);
            }
        });
        return button;
    }

    private void submitCoopAction(MinesweeperDTO.ActionType action, int x, int y) {
        if (!isCoopRoom()) {
            ensureAuthorityBoard(x, y);
            OpenResult result = action == MinesweeperDTO.ActionType.OPEN_AROUND
                    ? openAround(x, y)
                    : openCell(x, y);
            updateCoopStatus(result.phase, result.hitMine, result.won);
            renderCoopGrid();
            return;
        }
        if (isHomeowner()) {
            MinesweeperDTO request = new MinesweeperDTO();
            request.setAction(action);
            request.setActorKey(getMyPlayerKey());
            request.setX(x);
            request.setY(y);
            handleCoopActionRequest(request);
            return;
        }
        MinesweeperDTO dto = new MinesweeperDTO();
        dto.setGame(Game.MINESWEEPER);
        dto.setEvent(MinesweeperDTO.Event.ACTION_REQUEST);
        dto.setAction(action);
        dto.setActorKey(getMyPlayerKey());
        dto.setX(x);
        dto.setY(y);
        sendMsg(dto);
    }

    private void togglePersonalMark(int x, int y) {
        CoopCell cell = coopCells[y][x];
        if (cell.opened) {
            return;
        }
        cell.personalMarked = !cell.personalMarked;
        renderCoopGrid();
    }

    private void toggleSharedMark(int x, int y) {
        if (!isCoopRoom()) {
            togglePersonalMark(x, y);
            return;
        }
        CoopCell cell = coopCells[y][x];
        if (cell.opened) {
            return;
        }
        cell.sharedMarked = !cell.sharedMarked;
        MinesweeperDTO dto = new MinesweeperDTO();
        dto.setGame(Game.MINESWEEPER);
        dto.setEvent(MinesweeperDTO.Event.SHARED_MARK);
        dto.setRows(coopRows);
        dto.setCols(coopCols);
        dto.setMines(coopMines);
        dto.setCells(toVisibleCells(false));
        dto.setActorKey(getMyPlayerKey());
        dto.setNextTurnPlayerKey(coopTurnPlayerKey);
        dto.setPhase(MinesweeperDTO.Phase.playing);
        sendMsg(dto);
        renderCoopGrid();
    }

    private void handleCoopRestartRequest(MinesweeperDTO body) {
        coopPendingRestartFromKey = body.getActorKey();
        coopPendingRestartFromName = body.getActorName() == null ? "对方" : body.getActorName();
        coopAwaitingRestartResponse = false;
        updateCoopStatus(null, false, false);
        refreshCoopControls();
    }

    private void handleCoopConfigRequest(MinesweeperDTO body) {
        if (body.getRows() == null || body.getCols() == null || body.getMines() == null) {
            return;
        }
        coopPendingConfigFromKey = body.getActorKey();
        coopPendingConfigFromName = body.getActorName() == null ? "对方" : body.getActorName();
        coopPendingConfig = normalizeBoardConfig(body.getRows(), body.getCols(), body.getMines());
        coopAwaitingConfigResponse = false;
        updateCoopStatus(null, false, false);
        refreshCoopControls();
    }

    private void respondPendingCoopRequest(boolean approved) {
        if (coopPendingConfigFromKey != null) {
            respondCoopConfig(approved);
            return;
        }
        respondCoopRestart(approved);
    }

    private void respondCoopRestart(boolean approved) {
        if (getRoom() == null || coopPendingRestartFromKey == null) {
            return;
        }
        MinesweeperDTO dto = new MinesweeperDTO();
        dto.setGame(Game.MINESWEEPER);
        dto.setEvent(MinesweeperDTO.Event.RESTART_RESPONSE);
        dto.setActorKey(getMyPlayerKey());
        dto.setActorName(GameAction.getNickname());
        dto.setRestartApproved(approved);
        sendMsg(dto);
        coopPendingRestartFromKey = null;
        coopPendingRestartFromName = null;
        updateCoopStatus(null, false, false);
        refreshCoopControls();
        if (approved && isHomeowner()) {
            sendCoopGameStart();
        }
    }

    private void handleCoopRestartResponse(MinesweeperDTO body) {
        boolean approved = Boolean.TRUE.equals(body.getRestartApproved());
        coopAwaitingRestartResponse = false;
        if (approved && isHomeowner()) {
            sendCoopGameStart();
        } else {
            updateCoopStatus(null, false, false);
            refreshCoopControls();
        }
    }

    private void respondCoopConfig(boolean approved) {
        if (getRoom() == null || coopPendingConfigFromKey == null || coopPendingConfig == null) {
            return;
        }
        MinesweeperDTO dto = new MinesweeperDTO();
        dto.setGame(Game.MINESWEEPER);
        dto.setEvent(MinesweeperDTO.Event.RESTART_RESPONSE);
        dto.setRows(coopPendingConfig.rows);
        dto.setCols(coopPendingConfig.cols);
        dto.setMines(coopPendingConfig.mines);
        dto.setActorKey(getMyPlayerKey());
        dto.setActorName(GameAction.getNickname());
        dto.setRestartApproved(approved);
        sendMsg(dto);
        coopPendingConfigFromKey = null;
        coopPendingConfigFromName = null;
        coopPendingConfig = null;
        updateCoopStatus(null, false, false);
        refreshCoopControls();
    }

    private void handleCoopConfigResponse(MinesweeperDTO body) {
        boolean approved = Boolean.TRUE.equals(body.getRestartApproved());
        coopAwaitingConfigResponse = false;
        if (approved && isHomeowner() && body.getRows() != null && body.getCols() != null && body.getMines() != null) {
            applyCoopBoardConfig(normalizeBoardConfig(body.getRows(), body.getCols(), body.getMines()), true);
        } else {
            updateCoopStatus(null, false, false);
            refreshCoopControls();
        }
    }

    private void restartCoopRound() {
        if (!isCoopRoom()) {
            restartLocalRound();
            return;
        }
        if (getRoom() == null || coopAwaitingRestartResponse || coopAwaitingConfigResponse
                || coopPendingRestartFromKey != null || coopPendingConfigFromKey != null) {
            return;
        }
        MinesweeperDTO dto = new MinesweeperDTO();
        dto.setGame(Game.MINESWEEPER);
        dto.setEvent(MinesweeperDTO.Event.RESTART_REQUEST);
        dto.setActorKey(getMyPlayerKey());
        dto.setActorName(GameAction.getNickname());
        sendMsg(dto);
        coopAwaitingRestartResponse = true;
        updateCoopStatus(null, false, false);
        refreshCoopControls();
    }

    private void restartLocalRound() {
        stopCoopTimer();
        coopCells = null;
        coopBoardGenerated = false;
        coopRoundActive = false;
        coopPhase = MinesweeperDTO.Phase.playing;
        coopStartedAt = System.currentTimeMillis();
        ensureCoopCells(coopRows, coopCols, coopMines);
        renderCoopGrid();
        updateCoopStatus(coopPhase, false, false);
        startCoopTimer();
    }

    private void sendCoopGameStart() {
        if (!isHomeowner() || getRoom() == null) {
            return;
        }
        coopCells = null;
        coopBoardGenerated = false;
        ensureCoopCells(coopRows, coopCols, coopMines);
        GameRoomMsgDTO msg = new GameRoomMsgDTO();
        msg.setRoomId(getRoom().getId());
        msg.setGame(Game.MINESWEEPER);
        msg.setMsgType(GameRoomMsgDTO.MsgType.GAME_START);
        MessageAction.send(msg, Action.GAME_ROOM);
    }

    private void ensureAuthorityBoard(int firstX, int firstY) {
        if (coopBoardGenerated) {
            return;
        }
        ensureCoopCells(coopRows, coopCols, coopMines);
        coopRoundActive = true;
        java.util.List<Point> candidates = new ArrayList<>();
        for (int y = 0; y < coopRows; y++) {
            for (int x = 0; x < coopCols; x++) {
                if (x == firstX && y == firstY) {
                    continue;
                }
                candidates.add(new Point(x, y));
            }
        }
        Collections.shuffle(candidates);
        for (int i = 0; i < Math.min(coopMines, candidates.size()); i++) {
            Point point = candidates.get(i);
            coopCells[point.y][point.x].hasMine = true;
        }
        for (int y = 0; y < coopRows; y++) {
            for (int x = 0; x < coopCols; x++) {
                coopCells[y][x].adjacentMines = countAdjacentMines(x, y);
            }
        }
        coopBoardGenerated = true;
    }

    private OpenResult openCell(int x, int y) {
        OpenResult result = new OpenResult();
        result.phase = MinesweeperDTO.Phase.playing;
        if (!inBounds(x, y)) {
            return result;
        }
        CoopCell cell = coopCells[y][x];
        if (cell.opened || cell.personalMarked || cell.sharedMarked) {
            return result;
        }
        if (cell.hasMine) {
            cell.opened = true;
            cell.exploded = true;
            result.openedCount = 1;
            result.hitMine = true;
            result.phase = MinesweeperDTO.Phase.lost;
            revealMines();
            return result;
        }
        result.openedCount = openSafeArea(x, y);
        if (isWon()) {
            result.won = true;
            result.phase = MinesweeperDTO.Phase.won;
        }
        return result;
    }

    private OpenResult openAround(int x, int y) {
        OpenResult result = new OpenResult();
        result.phase = MinesweeperDTO.Phase.playing;
        if (!inBounds(x, y)) {
            return result;
        }
        CoopCell source = coopCells[y][x];
        if (!source.opened || source.adjacentMines <= 0) {
            return result;
        }
        if (countAdjacentMarks(x, y) != source.adjacentMines) {
            return result;
        }
        for (CoopCell cell : neighbors(x, y)) {
            if (cell.opened || cell.personalMarked || cell.sharedMarked) {
                continue;
            }
            if (cell.hasMine) {
                cell.opened = true;
                cell.exploded = true;
                result.openedCount++;
                result.hitMine = true;
                result.phase = MinesweeperDTO.Phase.lost;
                revealMines();
                return result;
            }
            result.openedCount += openSafeArea(cell.x, cell.y);
        }
        if (isWon()) {
            result.won = true;
            result.phase = MinesweeperDTO.Phase.won;
        }
        return result;
    }

    private int openSafeArea(int x, int y) {
        int opened = 0;
        ArrayDeque<CoopCell> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(coopCells[y][x]);
        while (!queue.isEmpty()) {
            CoopCell cell = queue.poll();
            String key = cell.x + ":" + cell.y;
            if (visited.contains(key)) {
                continue;
            }
            visited.add(key);
            if (cell.opened || cell.hasMine || cell.personalMarked || cell.sharedMarked) {
                continue;
            }
            cell.opened = true;
            opened++;
            if (cell.adjacentMines == 0) {
                queue.addAll(neighbors(cell.x, cell.y));
            }
        }
        return opened;
    }

    private void sendCoopState(OpenResult result, String actorKey) {
        MinesweeperDTO dto = new MinesweeperDTO();
        dto.setGame(Game.MINESWEEPER);
        dto.setEvent(result.phase == MinesweeperDTO.Phase.playing
                ? MinesweeperDTO.Event.STATE_PATCH
                : MinesweeperDTO.Event.GAME_RESULT);
        dto.setRows(coopRows);
        dto.setCols(coopCols);
        dto.setMines(coopMines);
        dto.setCells(toVisibleCells(result.phase != MinesweeperDTO.Phase.playing));
        dto.setActorKey(actorKey);
        dto.setNextTurnPlayerKey(coopTurnPlayerKey);
        dto.setPhase(result.phase);
        dto.setOpenedCount(result.openedCount);
        dto.setHitMine(result.hitMine);
        dto.setWon(result.won);
        sendMsg(dto);
    }

    private void sendCoopSnapshot() {
        if (!isHomeowner() || coopCells == null) {
            return;
        }
        OpenResult result = new OpenResult();
        result.phase = MinesweeperDTO.Phase.playing;
        sendCoopState(result, getMyPlayerKey());
    }

    private java.util.List<MinesweeperCellDTO> toVisibleCells(boolean revealMines) {
        java.util.List<MinesweeperCellDTO> cells = new ArrayList<>();
        for (int y = 0; y < coopRows; y++) {
            for (int x = 0; x < coopCols; x++) {
                CoopCell cell = coopCells[y][x];
                MinesweeperCellDTO dto = new MinesweeperCellDTO();
                dto.setX(x);
                dto.setY(y);
                dto.setOpened(cell.opened);
                dto.setAdjacentMines(cell.opened || revealMines ? cell.adjacentMines : null);
                dto.setSharedMarked(cell.sharedMarked);
                dto.setHasMine(revealMines && cell.hasMine ? true : null);
                dto.setExploded(cell.exploded);
                cells.add(dto);
            }
        }
        return cells;
    }

    private void updateCoopStatus(MinesweeperDTO.Phase phase, boolean hitMine, boolean won) {
        if (coopStatusLabel == null) {
            return;
        }
        if (phase != null) {
            coopPhase = phase;
        }
        if (coopPendingRestartFromKey != null) {
            setCoopStatusText(coopPendingRestartFromName + "申请重开", "Phase WAIT");
            return;
        }
        if (coopPendingConfigFromKey != null && coopPendingConfig != null) {
            setCoopStatusText(coopPendingConfigFromName + "申请改配置", "Phase WAIT");
            return;
        }
        if (coopAwaitingRestartResponse) {
            setCoopStatusText("等待对方同意重开", "Phase WAIT");
            return;
        }
        if (coopAwaitingConfigResponse) {
            setCoopStatusText("等待对方同意改配置", "Phase WAIT");
            return;
        }
        MinesweeperDTO.Phase currentPhase = phase == null ? coopPhase : phase;
        if (currentPhase == MinesweeperDTO.Phase.lost || hitMine) {
            coopRoundActive = false;
            setCoopStatusText("任务失败", "Phase HALT");
            updateCoopTimeLabel();
            stopCoopTimer();
        } else if (currentPhase == MinesweeperDTO.Phase.won || won) {
            coopRoundActive = false;
            setCoopStatusText("任务完成", "Phase DONE");
            updateCoopTimeLabel();
            stopCoopTimer();
        } else if (canOperate()) {
            setCoopStatusText("轮到你", "Turn YOU");
        } else {
            setCoopStatusText("等待对方", "Turn PEER");
        }
    }

    private String renderCellText(CoopCell cell) {
        if (coopConcealedMode) {
            return formatConcealedCellText(cell.opened, cell.hasMine, cell.adjacentMines, cell.personalMarked,
                    cell.sharedMarked);
        }
        if (cell.opened && cell.hasMine) {
            return "*";
        }
        if (cell.opened) {
            return cell.adjacentMines > 0 ? String.valueOf(cell.adjacentMines) : "";
        }
        if (cell.sharedMarked) {
            return "!";
        }
        if (cell.personalMarked) {
            return "?";
        }
        return "";
    }

    static String formatConcealedCellText(boolean opened, boolean hasMine, int adjacentMines, boolean personalMarked,
                                          boolean sharedMarked) {
        if (opened && hasMine) {
            return "!";
        }
        if (opened) {
            return adjacentMines > 0 ? String.valueOf(adjacentMines) : "";
        }
        if (sharedMarked) {
            return "o";
        }
        if (personalMarked) {
            return "x";
        }
        return "·";
    }

    static BoardConfig normalizeBoardConfig(int rows, int cols, int mines) {
        int normalizedRows = clamp(rows, 5, 24);
        int normalizedCols = clamp(cols, 5, 40);
        int normalizedMines = clamp(mines, 1, normalizedRows * normalizedCols - 1);
        return new BoardConfig(normalizedRows, normalizedCols, normalizedMines);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    static ConcealedToolbarLabels createConcealedToolbarLabels(boolean sharedMarkMode) {
        return new ConcealedToolbarLabels("Debug", "R", sharedMarkMode ? "M*" : "M", "V");
    }

    static class ConcealedToolbarLabels {
        final String title;
        final String restart;
        final String sharedMark;
        final String mode;

        private ConcealedToolbarLabels(String title, String restart, String sharedMark, String mode) {
            this.title = title;
            this.restart = restart;
            this.sharedMark = sharedMark;
            this.mode = mode;
        }
    }

    private void refreshCoopControls() {
        refreshCoopModeTexts();
        if (coopRestartButton != null) {
            coopRestartButton.setEnabled(!coopAwaitingRestartResponse && !coopAwaitingConfigResponse
                    && coopPendingRestartFromKey == null && coopPendingConfigFromKey == null);
        }
        if (coopRestartResponsePanel != null) {
            boolean hasRestartRequest = coopPendingRestartFromKey != null;
            boolean hasConfigRequest = coopPendingConfigFromKey != null && coopPendingConfig != null;
            coopRestartResponsePanel.setVisible(hasRestartRequest || hasConfigRequest);
            if (coopRestartResponseLabel != null) {
                if (hasConfigRequest) {
                    coopRestartResponseLabel.setText(coopPendingConfigFromName + "申请改为 "
                            + coopPendingConfig.rows + "x" + coopPendingConfig.cols + "/" + coopPendingConfig.mines);
                } else {
                    coopRestartResponseLabel.setText("对方申请重开");
                }
            }
        }
        refreshCoopSizeControls();
        updateCoopTimeLabel();
    }

    private void startCoopTimer() {
        coopStartedAt = System.currentTimeMillis();
        coopTimer = new javax.swing.Timer(1000, e -> updateCoopTimeLabel());
        coopTimer.start();
        updateCoopTimeLabel();
    }

    private void stopCoopTimer() {
        if (coopTimer != null) {
            coopTimer.stop();
            coopTimer = null;
        }
    }

    private void updateCoopTimeLabel() {
        if (coopTimeLabel == null || coopStartedAt <= 0) {
            return;
        }
        int seconds = (int) Math.min(999, Math.max(0, (System.currentTimeMillis() - coopStartedAt) / 1000));
        coopTimeLabel.setText(formatTimeLabel(seconds));
    }

    private String formatCounter(int value) {
        int normalized = Math.max(0, Math.min(999, value));
        return String.format("%03d", normalized);
    }

    private String formatMineLabel(int value) {
        return coopConcealedMode ? "I " + formatCounter(value) : formatCounter(value);
    }

    private String formatTimeLabel(int value) {
        return coopConcealedMode ? "T " + formatCounter(value) : formatCounter(value);
    }

    private void refreshCoopModeTexts() {
        if (coopTitleLabel != null) {
            coopTitleLabel.setText(coopConcealedMode ? createConcealedToolbarLabels(coopSharedMarkMode).title : getMinesweeperTitle());
        }
        if (coopRestartButton != null) {
            coopRestartButton.setText(coopConcealedMode ? createConcealedToolbarLabels(coopSharedMarkMode).restart : "☺");
            coopRestartButton.setToolTipText(coopConcealedMode ? "R / Refresh：申请重开本局" : "申请重开本局");
        }
        if (coopSharedMarkButton != null) {
            if (coopConcealedMode) {
                coopSharedMarkButton.setText(createConcealedToolbarLabels(coopSharedMarkMode).sharedMark);
                coopSharedMarkButton.setToolTipText("M / Mark：协作标记，双方可见");
            } else {
                coopSharedMarkButton.setText(coopSharedMarkMode ? "退出标记" : "协作标记");
                coopSharedMarkButton.setToolTipText("开启后左键切换双方可见标记");
            }
        }
        if (coopModeButton != null) {
            coopModeButton.setText(coopConcealedMode ? createConcealedToolbarLabels(coopSharedMarkMode).mode : "Cache");
            coopModeButton.setToolTipText(coopConcealedMode ? "V / View：切回正常扫雷界面" : "Cache：切回隐蔽界面");
        }
        if (coopHelpButton != null) {
            coopHelpButton.setToolTipText(getCoopHelpText());
        }
        if (coopMineLabel != null) {
            coopMineLabel.setText(formatMineLabel(Math.max(0, coopMines - countMarks())));
            coopMineLabel.setToolTipText(coopConcealedMode ? "Index：剩余雷数" : "剩余雷数");
        }
        if (coopTimeLabel != null) {
            coopTimeLabel.setToolTipText(coopConcealedMode ? "Elapsed：计时" : "计时");
        }
        styleCoopToolbar();
    }

    private void setCoopStatusText(String classicText, String concealedText) {
        coopStatusLabel.setText(coopConcealedMode ? concealedText : classicText);
    }

    private String getCoopHelpText() {
        return "<html>"
                + "左键 Open：打开格子。<br>"
                + "双击 Open Around：打开已满足标记数的数字格周围。<br>"
                + "右键 Personal Pin：个人标记，仅自己可见。<br>"
                + "Pin / Shared Pin：协作标记，双方可见。<br><br>"
                + "Debug：隐蔽标题。I / Index：剩余雷数。T / Elapsed：计时。<br>"
                + "Phase：当前阶段。Turn YOU/PEER：轮到你/对方。<br>"
                + "R / Refresh：申请重开。M / Mark：协作标记。V / View：切回正常界面。<br>"
                + "P1/P2/P3：预设雷区。r/c/m：行、列、雷数。A：应用到下一局。"
                + "</html>";
    }

    private void styleCoopButton(JButton button, CoopCell cell) {
        button.setFocusPainted(false);
        if (!coopConcealedMode) {
            button.setOpaque(true);
            if (cell.opened) {
                button.setBackground(cell.exploded ? new Color(255, 190, 190) : new Color(238, 238, 238));
            }
            return;
        }
        button.setBorder(BorderFactory.createEmptyBorder());
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        button.setForeground(Color.BLACK);
    }

    private void styleCoopToolbar() {
        if (!coopConcealedMode) {
            return;
        }
        Font smallFont = new Font("", Font.PLAIN, 12);
        if (coopTitleLabel != null) {
            coopTitleLabel.setFont(smallFont);
            coopTitleLabel.setForeground(new Color(80, 87, 96));
        }
        if (coopStatusLabel != null) {
            coopStatusLabel.setFont(smallFont);
            coopStatusLabel.setForeground(new Color(100, 108, 118));
        }
        if (coopMineLabel != null) {
            coopMineLabel.setFont(smallFont);
            coopMineLabel.setForeground(new Color(80, 87, 96));
        }
        if (coopTimeLabel != null) {
            coopTimeLabel.setFont(smallFont);
            coopTimeLabel.setForeground(new Color(80, 87, 96));
        }
        styleSmallToolbarButton(coopRestartButton);
        styleSmallToolbarButton(coopSharedMarkButton);
        styleSmallToolbarButton(coopModeButton);
        styleSmallToolbarButton(coopHelpButton);
        styleSmallToolbarButton(coopSizeApplyButton);
    }

    private void styleSmallToolbarButton(JButton button) {
        if (button == null) {
            return;
        }
        button.setFont(new Font("", Font.PLAIN, 12));
        button.setMargin(new Insets(0, 6, 0, 6));
        button.setPreferredSize(new Dimension(44, 24));
        button.setFocusable(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
    }

    private void initCoopSizeControls(JPanel topPanel) {
        coopSizePresetBox = new JComboBox<>(new String[]{"简单", "中等", "困难"});
        coopSizePresetBox.setToolTipText("简单=9x9/10，中等=16x16/40，困难=16x30/99");
        coopSizePresetBox.addActionListener(e -> applyCoopPreset());
        topPanel.add(coopSizePresetBox);

        coopRowsSpinner = new JSpinner(new SpinnerNumberModel(coopRows, 5, 24, 1));
        coopColsSpinner = new JSpinner(new SpinnerNumberModel(coopCols, 5, 40, 1));
        coopMinesSpinner = new JSpinner(new SpinnerNumberModel(coopMines, 1, coopRows * coopCols - 1, 1));
        topPanel.add(new JLabel("r"));
        topPanel.add(coopRowsSpinner);
        topPanel.add(new JLabel("c"));
        topPanel.add(coopColsSpinner);
        topPanel.add(new JLabel("m"));
        topPanel.add(coopMinesSpinner);

        coopSizeApplyButton = new JButton("A");
        coopSizeApplyButton.setToolTipText("应用自定义行、列、雷数到下一局");
        coopSizeApplyButton.addActionListener(e -> applyCustomCoopBoardConfig());
        topPanel.add(coopSizeApplyButton);
        refreshCoopSizeControls();
    }

    private void applyCoopPreset() {
        if (!canConfigureCoopBoard()) {
            return;
        }
        int index = coopSizePresetBox == null ? 0 : coopSizePresetBox.getSelectedIndex();
        setCoopBoardConfig(presetBoardConfig(index));
    }

    private void applyCustomCoopBoardConfig() {
        if (!canConfigureCoopBoard()) {
            return;
        }
        BoardConfig config = normalizeBoardConfig(
                readSpinnerInt(coopRowsSpinner, coopRows),
                readSpinnerInt(coopColsSpinner, coopCols),
                readSpinnerInt(coopMinesSpinner, coopMines));
        setCoopBoardConfig(config);
    }

    private int readSpinnerInt(JSpinner spinner, int fallback) {
        if (spinner == null) {
            return fallback;
        }
        if (spinner.getEditor() instanceof JSpinner.DefaultEditor) {
            String text = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().getText();
            try {
                return Integer.parseInt(text.trim());
            } catch (Exception ignored) {
            }
        }
        Object value = spinner.getValue();
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private void setCoopBoardConfig(BoardConfig config) {
        if (isCoopRoom()) {
            requestCoopBoardConfig(config);
            return;
        }
        applyCoopBoardConfig(config, false);
    }

    private void applyCoopBoardConfig(BoardConfig config, boolean broadcast) {
        coopRows = config.rows;
        coopCols = config.cols;
        coopMines = config.mines;
        coopCells = null;
        ensureCoopCells(coopRows, coopCols, coopMines);
        syncCoopSizeInputs();
        renderCoopGrid();
        if (broadcast) {
            broadcastCoopInit();
        }
    }

    private void requestCoopBoardConfig(BoardConfig config) {
        if (!isHomeowner() || getRoom() == null || coopAwaitingConfigResponse) {
            return;
        }
        BoardConfig normalized = normalizeBoardConfig(config.rows, config.cols, config.mines);
        MinesweeperDTO dto = new MinesweeperDTO();
        dto.setGame(Game.MINESWEEPER);
        dto.setEvent(MinesweeperDTO.Event.RESTART_REQUEST);
        dto.setRows(normalized.rows);
        dto.setCols(normalized.cols);
        dto.setMines(normalized.mines);
        dto.setActorKey(getMyPlayerKey());
        dto.setActorName(GameAction.getNickname());
        sendMsg(dto);
        coopAwaitingConfigResponse = true;
        updateCoopStatus(null, false, false);
        refreshCoopControls();
    }

    private void broadcastCoopInit() {
        if (!isHomeowner() || getRoom() == null) {
            return;
        }
        String hostKey = getHomeownerKey();
        coopTurnPlayerKey = hostKey;
        MinesweeperDTO dto = new MinesweeperDTO();
        dto.setGame(Game.MINESWEEPER);
        dto.setEvent(MinesweeperDTO.Event.INIT);
        dto.setRows(coopRows);
        dto.setCols(coopCols);
        dto.setMines(coopMines);
        dto.setCells(new ArrayList<>());
        dto.setActorKey(hostKey);
        dto.setNextTurnPlayerKey(hostKey);
        dto.setPhase(MinesweeperDTO.Phase.playing);
        sendMsg(dto);
    }

    private void refreshCoopSizeControls() {
        boolean enabled = canConfigureCoopBoard();
        if (coopSizePresetBox != null) {
            coopSizePresetBox.setEnabled(enabled);
        }
        if (coopRowsSpinner != null) {
            coopRowsSpinner.setEnabled(enabled);
        }
        if (coopColsSpinner != null) {
            coopColsSpinner.setEnabled(enabled);
        }
        if (coopMinesSpinner != null) {
            coopMinesSpinner.setEnabled(enabled);
        }
        if (coopSizeApplyButton != null) {
            coopSizeApplyButton.setEnabled(enabled);
        }
        syncCoopSizeInputs();
    }

    private void syncCoopSizeInputs() {
        if (coopRowsSpinner != null) {
            coopRowsSpinner.setValue(coopRows);
        }
        if (coopColsSpinner != null) {
            coopColsSpinner.setValue(coopCols);
        }
        if (coopMinesSpinner != null) {
            int maxMines = Math.max(1, coopRows * coopCols - 1);
            coopMinesSpinner.setModel(new SpinnerNumberModel(Math.min(coopMines, maxMines), 1, maxMines, 1));
            coopMinesSpinner.setValue(coopMines);
        }
    }

    private boolean canConfigureCoopBoard() {
        return (!isCoopRoom() || isHomeowner()) && !coopRoundActive
                && !coopAwaitingConfigResponse && coopPendingConfigFromKey == null;
    }

    private boolean canOperate() {
        if (!isCoopRoom()) {
            return coopPhase == MinesweeperDTO.Phase.playing;
        }
        String myKey = getMyPlayerKey();
        return myKey != null && myKey.equals(coopTurnPlayerKey);
    }

    private boolean isCoopRoom() {
        return getRoom() != null && COOP_GAME_MODE.equals(getRoom().getGameMode());
    }

    private String getMinesweeperTitle() {
        return isCoopRoom() ? "合作排雷" : "扫雷";
    }

    static BoardConfig presetBoardConfig(int index) {
        if (index == 1) {
            return new BoardConfig(16, 16, 40);
        }
        if (index == 2) {
            return new BoardConfig(16, 30, 99);
        }
        return new BoardConfig(9, 9, 10);
    }

    private String getMyPlayerKey() {
        GameRoom room = getRoom();
        if (room == null) {
            return null;
        }
        for (GameRoom.Player player : room.getUsers().values()) {
            if (GameAction.getNickname().equals(player.getUsername())) {
                return player.getId();
            }
        }
        return null;
    }

    private String getHomeownerKey() {
        return getRoom() != null && getRoom().getHomeowner() != null
                ? getRoom().getHomeowner().getIdentityKey()
                : null;
    }

    private String getOtherPlayerKey(String actorKey) {
        if (getRoom() == null) {
            return actorKey;
        }
        for (GameRoom.Player player : getRoom().getUsers().values()) {
            if (!player.getId().equals(actorKey)) {
                return player.getId();
            }
        }
        return actorKey;
    }

    private int countMarks() {
        int count = 0;
        if (coopCells == null) {
            return 0;
        }
        for (int y = 0; y < coopRows; y++) {
            for (int x = 0; x < coopCols; x++) {
                if (coopCells[y][x].personalMarked || coopCells[y][x].sharedMarked) {
                    count++;
                }
            }
        }
        return count;
    }

    private int countAdjacentMines(int x, int y) {
        int count = 0;
        for (CoopCell cell : neighbors(x, y)) {
            if (cell.hasMine) {
                count++;
            }
        }
        return count;
    }

    private int countAdjacentMarks(int x, int y) {
        int count = 0;
        for (CoopCell cell : neighbors(x, y)) {
            if (cell.personalMarked || cell.sharedMarked) {
                count++;
            }
        }
        return count;
    }

    private java.util.List<CoopCell> neighbors(int x, int y) {
        java.util.List<CoopCell> cells = new ArrayList<>();
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                int nx = x + dx;
                int ny = y + dy;
                if (inBounds(nx, ny)) {
                    cells.add(coopCells[ny][nx]);
                }
            }
        }
        return cells;
    }

    private boolean inBounds(int x, int y) {
        return coopCells != null && y >= 0 && y < coopRows && x >= 0 && x < coopCols;
    }

    private boolean isWon() {
        for (int y = 0; y < coopRows; y++) {
            for (int x = 0; x < coopCols; x++) {
                CoopCell cell = coopCells[y][x];
                if (!cell.hasMine && !cell.opened) {
                    return false;
                }
            }
        }
        return true;
    }

    private void revealMines() {
        for (int y = 0; y < coopRows; y++) {
            for (int x = 0; x < coopCols; x++) {
                if (coopCells[y][x].hasMine) {
                    coopCells[y][x].opened = true;
                }
            }
        }
    }

    private Set<String> collectPersonalMarks() {
        Set<String> marks = new LinkedHashSet<>();
        if (coopCells == null) {
            return marks;
        }
        for (int y = 0; y < coopRows; y++) {
            for (int x = 0; x < coopCols; x++) {
                if (coopCells[y][x].personalMarked) {
                    marks.add(x + ":" + y);
                }
            }
        }
        return marks;
    }

    private void restorePersonalMarks(Set<String> marks) {
        for (int y = 0; y < coopRows; y++) {
            for (int x = 0; x < coopCols; x++) {
                coopCells[y][x].personalMarked = marks.contains(x + ":" + y);
            }
        }
    }

    private static class CoopCell {
        private final int x;
        private final int y;
        private boolean hasMine;
        private int adjacentMines;
        private boolean opened;
        private boolean personalMarked;
        private boolean sharedMarked;
        private boolean exploded;

        private CoopCell(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    private static class OpenResult {
        private int openedCount;
        private boolean hitMine;
        private boolean won;
        private MinesweeperDTO.Phase phase = MinesweeperDTO.Phase.playing;
    }

    static class BoardConfig {
        final int rows;
        final int cols;
        final int mines;

        private BoardConfig(int rows, int cols, int mines) {
            this.rows = rows;
            this.cols = cols;
            this.mines = mines;
        }
    }
}
