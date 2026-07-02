package cn.xeblog.server.game.gobang;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.xeblog.server.config.GlobalConfig;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.stream.Stream;

/**
 * 五子棋服务端留痕。
 *
 * <p>只写入服务端数据目录，不提供查询接口，写入失败不影响对局。</p>
 */
@Slf4j
public final class GobangHistoryService {

    static final String HISTORY_DIR_NAME = "gobang-history";

    private static final Object WRITE_LOCK = new Object();
    private static final int DEFAULT_RETENTION_DAYS = 3;
    private static LongSupplier nowSupplier = System::currentTimeMillis;
    private static int retentionDays = DEFAULT_RETENTION_DAYS;
    private static long cleanupIntervalMillis = TimeUnit.HOURS.toMillis(1);
    private static long lastCleanupAt;
    private static boolean enabled = true;

    private GobangHistoryService() {
    }

    public static void record(String roomId, Map<String, Object> payload) {
        if (!enabled || StrUtil.isBlank(roomId) || payload == null || payload.isEmpty()) {
            return;
        }

        long now = nowSupplier.getAsLong();
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("recordedAt", now);
        record.put("recordedAtText", Instant.ofEpochMilli(now).toString());
        record.putAll(payload);

        try {
            synchronized (WRITE_LOCK) {
                cleanupIfNeeded(now);
                Path dir = historyRoot().resolve(dateFolder(now));
                Files.createDirectories(dir);
                Files.writeString(
                        dir.resolve(safeRoomId(roomId) + ".jsonl"),
                        JSONUtil.toJsonStr(record) + System.lineSeparator(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.APPEND);
            }
        } catch (Exception e) {
            log.warn("五子棋留痕写入失败 roomId={}: {}", roomId, e.getMessage());
        }
    }

    static Path historyRoot() {
        return Paths.get(GlobalConfig.DATA_PATH, HISTORY_DIR_NAME).normalize();
    }

    static String dateFolder(long timestamp) {
        return Instant.ofEpochMilli(timestamp).atZone(ZoneOffset.UTC).toLocalDate().toString();
    }

    static void setNowSupplierForTest(LongSupplier testNowSupplier) {
        nowSupplier = testNowSupplier == null ? System::currentTimeMillis : testNowSupplier;
    }

    static void setRetentionDaysForTest(int days) {
        retentionDays = Math.max(1, days);
    }

    static void setCleanupIntervalMillisForTest(long intervalMillis) {
        cleanupIntervalMillis = Math.max(0L, intervalMillis);
    }

    static void setEnabledForTest(boolean testEnabled) {
        enabled = testEnabled;
    }

    static void resetForTest() {
        nowSupplier = System::currentTimeMillis;
        retentionDays = DEFAULT_RETENTION_DAYS;
        cleanupIntervalMillis = TimeUnit.HOURS.toMillis(1);
        lastCleanupAt = 0L;
        enabled = true;
    }

    private static void cleanupIfNeeded(long now) throws IOException {
        if (lastCleanupAt > 0 && now - lastCleanupAt < cleanupIntervalMillis) {
            return;
        }
        lastCleanupAt = now;
        Path root = historyRoot();
        if (!Files.isDirectory(root)) {
            return;
        }

        LocalDate cutoffDate = Instant.ofEpochMilli(now)
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
                .minusDays(retentionDays);
        try (Stream<Path> stream = Files.list(root)) {
            stream.filter(Files::isDirectory)
                    .filter(path -> isExpiredDayDirectory(path, cutoffDate))
                    .forEach(GobangHistoryService::deleteDirectoryQuietly);
        }
    }

    private static boolean isExpiredDayDirectory(Path path, LocalDate cutoffDate) {
        try {
            return LocalDate.parse(path.getFileName().toString()).isBefore(cutoffDate);
        } catch (Exception e) {
            return false;
        }
    }

    private static void deleteDirectoryQuietly(Path dir) {
        Path root = historyRoot();
        Path normalized = dir.normalize();
        if (!normalized.startsWith(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(normalized)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.warn("五子棋过期留痕清理失败 path={}: {}", path, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.warn("五子棋过期留痕扫描失败 path={}: {}", normalized, e.getMessage());
        }
    }

    private static String safeRoomId(String roomId) {
        String safe = roomId.replaceAll("[^A-Za-z0-9._-]", "_");
        return StrUtil.isBlank(safe) ? "unknown-room" : safe;
    }
}
