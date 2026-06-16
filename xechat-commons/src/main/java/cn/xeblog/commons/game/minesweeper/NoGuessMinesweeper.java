package cn.xeblog.commons.game.minesweeper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

/**
 * 支持基础规则和局部组合推理的 no-guess 扫雷生成器。
 */
public final class NoGuessMinesweeper {

    private static final int MAX_GENERATE_ATTEMPTS = 600;
    private static final int MAX_COMPONENT_VARIABLES = 18;
    private static final int MAX_ASSIGNMENTS = 2048;

    private NoGuessMinesweeper() {
    }

    public static Board generate(int rows, int cols, int mines, Point firstClick, Random random) {
        int normalizedRows = Math.max(1, rows);
        int normalizedCols = Math.max(1, cols);
        int normalizedMines = Math.max(0, Math.min(mines, normalizedRows * normalizedCols - 1));
        Point safe = inBounds(normalizedRows, normalizedCols, firstClick)
                ? firstClick
                : new Point(normalizedCols / 2, normalizedRows / 2);
        Random rng = random == null ? new Random() : random;
        for (int i = 0; i < MAX_GENERATE_ATTEMPTS; i++) {
            Board board = Board.fromMines(normalizedRows, normalizedCols,
                    pickMinePositions(normalizedRows, normalizedCols, normalizedMines, safe, rng, true));
            if (isSolvable(board, Collections.singletonList(safe))) {
                return board;
            }
        }
        for (int i = 0; i < MAX_GENERATE_ATTEMPTS; i++) {
            Board board = Board.fromMines(normalizedRows, normalizedCols,
                    pickMinePositions(normalizedRows, normalizedCols, normalizedMines, safe, rng, false));
            if (isSolvable(board, Collections.singletonList(safe))) {
                return board;
            }
        }
        throw new IllegalStateException("未能生成可逻辑解的扫雷棋盘");
    }

    public static boolean isSolvable(Board board, List<Point> initialOpen) {
        return solve(board, initialOpen).isSolved();
    }

    public static SolveResult solve(Board board, List<Point> initialOpen) {
        Board work = board.copy();
        Set<String> safeKeys = new HashSet<>();
        Set<String> mineKeys = new HashSet<>();
        if (initialOpen != null) {
            for (Point point : initialOpen) {
                if (!work.inBounds(point.x, point.y)) {
                    continue;
                }
                if (work.cell(point.x, point.y).mine) {
                    return new SolveResult(false, safeKeys, mineKeys);
                }
                openSafeArea(work, point.x, point.y, safeKeys);
            }
        }

        boolean changed = true;
        while (changed) {
            changed = false;
            List<Constraint> constraints = collectConstraints(work, mineKeys);
            for (Constraint constraint : constraints) {
                if (constraint.mineCount < 0 || constraint.mineCount > constraint.keys.size()) {
                    return new SolveResult(false, safeKeys, mineKeys);
                }
                if (constraint.keys.isEmpty()) {
                    continue;
                }
                if (constraint.mineCount == 0) {
                    for (String key : constraint.keys) {
                        Point point = Point.fromKey(key);
                        changed = openSafeArea(work, point.x, point.y, safeKeys) || changed;
                    }
                } else if (constraint.mineCount == constraint.keys.size()) {
                    for (String key : constraint.keys) {
                        if (mineKeys.add(key)) {
                            changed = true;
                        }
                    }
                }
            }
            if (changed) {
                continue;
            }

            Deductions deductions = deduceByCombinations(constraints);
            for (String key : deductions.mineKeys) {
                if (mineKeys.add(key)) {
                    changed = true;
                }
            }
            for (String key : deductions.safeKeys) {
                Point point = Point.fromKey(key);
                changed = openSafeArea(work, point.x, point.y, safeKeys) || changed;
            }
        }

        return new SolveResult(work.allSafeOpened(), safeKeys, mineKeys);
    }

    private static List<Point> pickMinePositions(int rows, int cols, int mines, Point safe, Random random,
                                                 boolean avoidSafeNeighbors) {
        Set<String> excluded = new HashSet<>();
        excluded.add(safe.key());
        if (avoidSafeNeighbors && rows * cols - mines > 9) {
            for (int y = safe.y - 1; y <= safe.y + 1; y++) {
                for (int x = safe.x - 1; x <= safe.x + 1; x++) {
                    if (x >= 0 && x < cols && y >= 0 && y < rows) {
                        excluded.add(new Point(x, y).key());
                    }
                }
            }
        }
        List<Point> candidates = new ArrayList<>();
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                Point point = new Point(x, y);
                if (!excluded.contains(point.key())) {
                    candidates.add(point);
                }
            }
        }
        Collections.shuffle(candidates, random);
        return new ArrayList<>(candidates.subList(0, Math.min(mines, candidates.size())));
    }

    private static List<Constraint> collectConstraints(Board board, Set<String> mineKeys) {
        List<Constraint> constraints = new ArrayList<>();
        for (Cell cell : board.cells()) {
            if (!cell.opened || cell.adjacent <= 0) {
                continue;
            }
            List<String> keys = new ArrayList<>();
            int knownMines = 0;
            for (Cell neighbor : board.neighbors(cell.x, cell.y)) {
                String key = new Point(neighbor.x, neighbor.y).key();
                if (mineKeys.contains(key)) {
                    knownMines++;
                } else if (!neighbor.opened) {
                    keys.add(key);
                }
            }
            constraints.add(new Constraint(keys, cell.adjacent - knownMines));
        }
        return constraints;
    }

    private static Deductions deduceByCombinations(List<Constraint> constraints) {
        Deductions deductions = new Deductions();
        Map<String, Set<String>> graph = new HashMap<>();
        for (Constraint constraint : constraints) {
            for (String key : constraint.keys) {
                graph.computeIfAbsent(key, ignored -> new HashSet<>());
                for (String other : constraint.keys) {
                    if (!key.equals(other)) {
                        graph.get(key).add(other);
                    }
                }
            }
        }
        Set<String> visited = new HashSet<>();
        for (String key : graph.keySet()) {
            if (visited.contains(key)) {
                continue;
            }
            List<String> component = collectComponent(key, graph, visited);
            if (component.isEmpty() || component.size() > MAX_COMPONENT_VARIABLES) {
                continue;
            }
            Set<String> componentKeys = new HashSet<>(component);
            List<Constraint> scoped = new ArrayList<>();
            boolean complete = true;
            for (Constraint constraint : constraints) {
                boolean touchesComponent = false;
                for (String item : constraint.keys) {
                    if (componentKeys.contains(item)) {
                        touchesComponent = true;
                    } else if (touchesComponent) {
                        complete = false;
                    }
                }
                if (touchesComponent) {
                    for (String item : constraint.keys) {
                        if (!componentKeys.contains(item)) {
                            complete = false;
                        }
                    }
                    scoped.add(constraint);
                }
            }
            if (!complete) {
                continue;
            }
            List<boolean[]> assignments = enumerateValidAssignments(component, scoped);
            if (assignments.isEmpty()) {
                continue;
            }
            for (int i = 0; i < component.size(); i++) {
                boolean allMine = true;
                boolean allSafe = true;
                for (boolean[] assignment : assignments) {
                    allMine = allMine && assignment[i];
                    allSafe = allSafe && !assignment[i];
                }
                if (allMine) {
                    deductions.mineKeys.add(component.get(i));
                }
                if (allSafe) {
                    deductions.safeKeys.add(component.get(i));
                }
            }
        }
        return deductions;
    }

    private static List<String> collectComponent(String start, Map<String, Set<String>> graph, Set<String> visited) {
        List<String> result = new ArrayList<>();
        Queue<String> queue = new ArrayDeque<>();
        visited.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            String key = queue.remove();
            result.add(key);
            for (String next : graph.getOrDefault(key, Collections.emptySet())) {
                if (visited.add(next)) {
                    queue.add(next);
                }
            }
        }
        return result;
    }

    private static List<boolean[]> enumerateValidAssignments(List<String> keys, List<Constraint> constraints) {
        Map<String, Integer> indexByKey = new HashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            indexByKey.put(keys.get(i), i);
        }
        List<IndexedConstraint> indexed = new ArrayList<>();
        for (Constraint constraint : constraints) {
            List<Integer> indexes = new ArrayList<>();
            for (String key : constraint.keys) {
                indexes.add(indexByKey.get(key));
            }
            indexed.add(new IndexedConstraint(indexes, constraint.mineCount));
        }
        List<boolean[]> result = new ArrayList<>();
        boolean[] current = new boolean[keys.size()];
        enumerate(0, current, indexed, result);
        return result;
    }

    private static void enumerate(int index, boolean[] current, List<IndexedConstraint> constraints,
                                  List<boolean[]> result) {
        if (result.size() > MAX_ASSIGNMENTS) {
            return;
        }
        if (index == current.length) {
            if (isCompleteAssignmentValid(current, constraints)) {
                result.add(current.clone());
            }
            return;
        }
        current[index] = false;
        if (isPartialAssignmentPossible(current, index, constraints)) {
            enumerate(index + 1, current, constraints, result);
        }
        current[index] = true;
        if (isPartialAssignmentPossible(current, index, constraints)) {
            enumerate(index + 1, current, constraints, result);
        }
        current[index] = false;
    }

    private static boolean isCompleteAssignmentValid(boolean[] current, List<IndexedConstraint> constraints) {
        for (IndexedConstraint constraint : constraints) {
            int mines = 0;
            for (Integer index : constraint.indexes) {
                if (current[index]) {
                    mines++;
                }
            }
            if (mines != constraint.mineCount) {
                return false;
            }
        }
        return true;
    }

    private static boolean isPartialAssignmentPossible(boolean[] current, int assignedUntil,
                                                       List<IndexedConstraint> constraints) {
        for (IndexedConstraint constraint : constraints) {
            int mines = 0;
            int assigned = 0;
            for (Integer index : constraint.indexes) {
                if (index <= assignedUntil) {
                    assigned++;
                    if (current[index]) {
                        mines++;
                    }
                }
            }
            int unassigned = constraint.indexes.size() - assigned;
            if (mines > constraint.mineCount || mines + unassigned < constraint.mineCount) {
                return false;
            }
        }
        return true;
    }

    private static boolean openSafeArea(Board board, int x, int y, Set<String> safeKeys) {
        int before = safeKeys.size();
        Queue<Point> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(new Point(x, y));
        while (!queue.isEmpty()) {
            Point point = queue.remove();
            if (!board.inBounds(point.x, point.y) || !visited.add(point.key())) {
                continue;
            }
            Cell cell = board.cell(point.x, point.y);
            if (cell.opened || cell.mine) {
                continue;
            }
            cell.opened = true;
            safeKeys.add(point.key());
            if (cell.adjacent == 0) {
                for (Cell neighbor : board.neighbors(point.x, point.y)) {
                    if (!neighbor.mine) {
                        queue.add(new Point(neighbor.x, neighbor.y));
                    }
                }
            }
        }
        return safeKeys.size() > before;
    }

    public static final class Board {
        private final int rows;
        private final int cols;
        private final Cell[][] cells;

        private Board(int rows, int cols) {
            this.rows = rows;
            this.cols = cols;
            this.cells = new Cell[rows][cols];
            for (int y = 0; y < rows; y++) {
                for (int x = 0; x < cols; x++) {
                    this.cells[y][x] = new Cell(x, y);
                }
            }
        }

        public static Board fromMines(int rows, int cols, List<Point> mines) {
            Board board = new Board(rows, cols);
            for (Point point : mines) {
                if (board.inBounds(point.x, point.y)) {
                    board.cell(point.x, point.y).mine = true;
                }
            }
            for (Cell cell : board.cells()) {
                if (!cell.mine) {
                    int adjacent = 0;
                    for (Cell neighbor : board.neighbors(cell.x, cell.y)) {
                        if (neighbor.mine) {
                            adjacent++;
                        }
                    }
                    cell.adjacent = adjacent;
                }
            }
            return board;
        }

        public int getRows() {
            return rows;
        }

        public int getCols() {
            return cols;
        }

        public Cell cell(int x, int y) {
            return cells[y][x];
        }

        public boolean inBounds(int x, int y) {
            return x >= 0 && x < cols && y >= 0 && y < rows;
        }

        public List<Cell> cells() {
            List<Cell> result = new ArrayList<>();
            for (int y = 0; y < rows; y++) {
                for (int x = 0; x < cols; x++) {
                    result.add(cells[y][x]);
                }
            }
            return result;
        }

        public List<Cell> neighbors(int x, int y) {
            List<Cell> result = new ArrayList<>();
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) {
                        continue;
                    }
                    int nx = x + dx;
                    int ny = y + dy;
                    if (inBounds(nx, ny)) {
                        result.add(cell(nx, ny));
                    }
                }
            }
            return result;
        }

        private Board copy() {
            Board copy = new Board(rows, cols);
            for (Cell cell : cells()) {
                Cell target = copy.cell(cell.x, cell.y);
                target.mine = cell.mine;
                target.adjacent = cell.adjacent;
                target.opened = cell.opened;
            }
            return copy;
        }

        private boolean allSafeOpened() {
            for (Cell cell : cells()) {
                if (!cell.mine && !cell.opened) {
                    return false;
                }
            }
            return true;
        }
    }

    public static final class Cell {
        private final int x;
        private final int y;
        private boolean mine;
        private int adjacent;
        private boolean opened;

        private Cell(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public boolean isMine() {
            return mine;
        }

        public int getAdjacent() {
            return adjacent;
        }
    }

    public static final class Point {
        private final int x;
        private final int y;

        public Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        private String key() {
            return x + ":" + y;
        }

        private static Point fromKey(String key) {
            String[] parts = key.split(":");
            return new Point(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Point)) {
                return false;
            }
            Point point = (Point) o;
            return x == point.x && y == point.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }

    public static final class SolveResult {
        private final boolean solved;
        private final Set<String> safeKeys;
        private final Set<String> mineKeys;

        private SolveResult(boolean solved, Set<String> safeKeys, Set<String> mineKeys) {
            this.solved = solved;
            this.safeKeys = safeKeys;
            this.mineKeys = mineKeys;
        }

        public boolean isSolved() {
            return solved;
        }

        public Set<String> getSafeKeys() {
            return safeKeys;
        }

        public Set<String> getMineKeys() {
            return mineKeys;
        }
    }

    private static final class Constraint {
        private final List<String> keys;
        private final int mineCount;

        private Constraint(List<String> keys, int mineCount) {
            this.keys = keys;
            this.mineCount = mineCount;
        }
    }

    private static final class IndexedConstraint {
        private final List<Integer> indexes;
        private final int mineCount;

        private IndexedConstraint(List<Integer> indexes, int mineCount) {
            this.indexes = indexes;
            this.mineCount = mineCount;
        }
    }

    private static final class Deductions {
        private final Set<String> safeKeys = new HashSet<>();
        private final Set<String> mineKeys = new HashSet<>();
    }

    private static boolean inBounds(int rows, int cols, Point point) {
        return point != null && point.x >= 0 && point.x < cols && point.y >= 0 && point.y < rows;
    }
}
