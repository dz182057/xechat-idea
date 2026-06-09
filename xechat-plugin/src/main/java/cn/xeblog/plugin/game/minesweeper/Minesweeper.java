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

    private JLabel coopStatusLabel;

    private JLabel coopMineLabel;

    private JLabel coopTimeLabel;

    private JButton coopRestartButton;

    private JPanel coopRestartResponsePanel;

    private JButton coopRestartApproveButton;

    private JButton coopRestartRejectButton;

    private JButton[][] coopButtons;

    private CoopCell[][] coopCells;

    private int coopRows = 9;

    private int coopCols = 9;

    private int coopMines = 10;

    private boolean coopSharedMarkMode;

    private String coopTurnPlayerKey;

    private String coopHostPlayerKey;

    private boolean coopBoardGenerated;

    private boolean coopAwaitingRestartResponse;

    private String coopPendingRestartFromKey;

    private String coopPendingRestartFromName;

    private long coopStartedAt;

    private javax.swing.Timer coopTimer;

    @Override
    protected void start() {
        if (isCoopRoom()) {
            startCoop();
            return;
        }
        initPanel();
        mainPanel.setLayout(new BorderLayout());
        mainPanel.add(Box.createVerticalStrut(10), BorderLayout.NORTH);
        mainPanel.add(Box.createHorizontalStrut(10), BorderLayout.EAST);

        minesweeperUI = new MinesweeperUI(level);
        mainPanel.add(minesweeperUI, BorderLayout.CENTER);
        mainPanel.add(getBottomPanel(), BorderLayout.SOUTH);

        mainPanel.setMinimumSize(
            new Dimension(minesweeperUI.getTheWidth() + 40, minesweeperUI.getTheHeight() + 50));
        mainPanel.updateUI();

        minesweeperUI.requestFocusInWindow();
        init = false;
    }

    private void startCoop() {
        stopCoopTimer();
        initPanel();
        mainPanel.setLayout(new BorderLayout());

        coopPanel = new JPanel(new BorderLayout());
        JPanel topPanel = new JPanel();
        coopStatusLabel = new JLabel("等待同步...");
        coopMineLabel = new JLabel(formatCounter(coopMines));
        coopMineLabel.setToolTipText("剩余雷数");
        coopTimeLabel = new JLabel(formatCounter(0));
        coopTimeLabel.setToolTipText("计时");
        topPanel.add(new JLabel("合作排雷"));
        topPanel.add(coopStatusLabel);
        topPanel.add(coopMineLabel);

        coopRestartButton = new JButton("☺");
        coopRestartButton.setToolTipText("申请重开本局");
        coopRestartButton.addActionListener(e -> restartCoopRound());
        topPanel.add(coopRestartButton);
        topPanel.add(coopTimeLabel);

        JButton sharedMarkButton = new JButton("协作标记");
        sharedMarkButton.addActionListener(e -> {
            coopSharedMarkMode = !coopSharedMarkMode;
            sharedMarkButton.setText(coopSharedMarkMode ? "退出标记" : "协作标记");
        });
        topPanel.add(sharedMarkButton);

        coopRestartResponsePanel = new JPanel();
        coopRestartResponsePanel.add(new JLabel("对方申请重开"));
        coopRestartApproveButton = new JButton("同意");
        coopRestartApproveButton.addActionListener(e -> respondCoopRestart(true));
        coopRestartRejectButton = new JButton("不同意");
        coopRestartRejectButton.addActionListener(e -> respondCoopRestart(false));
        coopRestartResponsePanel.add(coopRestartApproveButton);
        coopRestartResponsePanel.add(coopRestartRejectButton);
        coopRestartResponsePanel.setVisible(false);

        coopGridPanel = new JPanel();
        coopPanel.add(topPanel, BorderLayout.NORTH);
        coopPanel.add(coopRestartResponsePanel, BorderLayout.SOUTH);
        coopPanel.add(coopGridPanel, BorderLayout.CENTER);
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
                handleCoopRestartRequest(body);
            } else if (body.getEvent() == MinesweeperDTO.Event.RESTART_RESPONSE) {
                handleCoopRestartResponse(body);
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
            coopBoardGenerated = false;
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
            coopMineLabel.setText(formatCounter(Math.max(0, coopMines - countMarks())));
        }
        refreshCoopControls();
        coopGridPanel.revalidate();
        coopGridPanel.repaint();
    }

    private JButton createCoopButton(int x, int y) {
        CoopCell cell = coopCells[y][x];
        JButton button = new JButton(renderCellText(cell));
        button.setPreferredSize(new Dimension(28, 28));
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setFont(new Font("", Font.BOLD, 12));
        button.setEnabled(canOperate() || cell.opened);
        if (cell.opened) {
            button.setBackground(cell.exploded ? new Color(255, 190, 190) : new Color(238, 238, 238));
        }
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

    private void restartCoopRound() {
        if (getRoom() == null || coopAwaitingRestartResponse || coopPendingRestartFromKey != null) {
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
        if (coopPendingRestartFromKey != null) {
            coopStatusLabel.setText(coopPendingRestartFromName + "申请重开");
            return;
        }
        if (coopAwaitingRestartResponse) {
            coopStatusLabel.setText("等待对方同意重开");
            return;
        }
        if (phase == MinesweeperDTO.Phase.lost || hitMine) {
            coopStatusLabel.setText("任务失败");
            updateCoopTimeLabel();
            stopCoopTimer();
        } else if (phase == MinesweeperDTO.Phase.won || won) {
            coopStatusLabel.setText("任务完成");
            updateCoopTimeLabel();
            stopCoopTimer();
        } else if (canOperate()) {
            coopStatusLabel.setText("轮到你");
        } else {
            coopStatusLabel.setText("等待对方");
        }
    }

    private String renderCellText(CoopCell cell) {
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

    private void refreshCoopControls() {
        if (coopRestartButton != null) {
            coopRestartButton.setEnabled(!coopAwaitingRestartResponse && coopPendingRestartFromKey == null);
        }
        if (coopRestartResponsePanel != null) {
            coopRestartResponsePanel.setVisible(coopPendingRestartFromKey != null);
        }
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
        coopTimeLabel.setText(formatCounter(seconds));
    }

    private String formatCounter(int value) {
        int normalized = Math.max(0, Math.min(999, value));
        return String.format("%03d", normalized);
    }

    private boolean canOperate() {
        String myKey = getMyPlayerKey();
        return myKey != null && myKey.equals(coopTurnPlayerKey);
    }

    private boolean isCoopRoom() {
        return getRoom() != null && COOP_GAME_MODE.equals(getRoom().getGameMode());
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
}
