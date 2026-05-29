package cn.xeblog.server.account;

import cn.hutool.json.JSONUtil;
import cn.xeblog.commons.entity.DesktopUpdateInfoDTO;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.commons.enums.Platform;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.cache.UserCache;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 桌面端更新包与版本信息管理。
 */
public final class DesktopUpdateService {

    private static final String INDEX_FILE = "versions.json";
    private static final String LATEST_FILE = "latest.json";

    private DesktopUpdateService() {
    }

    public static synchronized DesktopUpdateInfoDTO publish(
            String version,
            String title,
            String notes,
            boolean mandatory,
            String originalFileName,
            byte[] bytes
    ) throws IOException {
        String normalizedVersion = normalizeVersion(version);
        if (!isSemver(normalizedVersion)) {
            throw new IllegalArgumentException("版本号格式应为 x.y.z，例如 0.2.0");
        }
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("请选择安装包文件");
        }
        if (!safeName(originalFileName).toLowerCase().endsWith(".exe")) {
            throw new IllegalArgumentException("安装包必须是 .exe 文件");
        }

        List<DesktopUpdateInfoDTO> list = readAll();
        for (DesktopUpdateInfoDTO item : list) {
            if (normalizedVersion.equals(item.getVersion())) {
                throw new IllegalArgumentException("该版本已存在，不允许覆盖");
            }
        }

        ensureDir();
        String fileName = normalizedVersion + "-" + safeName(originalFileName);
        File target = new File(storageDir(), fileName);
        Files.write(target.toPath(), bytes);

        DesktopUpdateInfoDTO info = new DesktopUpdateInfoDTO();
        info.setVersion(normalizedVersion);
        info.setTitle(emptyToDefault(title, "XeChat " + normalizedVersion));
        info.setNotes(notes == null ? "" : notes.trim());
        info.setMandatory(mandatory);
        info.setEnabled(true);
        info.setFileName(fileName);
        info.setSize(bytes.length);
        info.setSha256(sha256(bytes));
        info.setPublishedAt(System.currentTimeMillis());
        info.setDownloadUrl("/updates/desktop/" + fileName);

        list.add(info);
        writeAll(list);
        writeLatest(list);
        return info;
    }

    public static synchronized List<DesktopUpdateInfoDTO> list(String baseUrl) {
        List<DesktopUpdateInfoDTO> list = readAll();
        Collections.sort(list, new Comparator<DesktopUpdateInfoDTO>() {
            @Override
            public int compare(DesktopUpdateInfoDTO a, DesktopUpdateInfoDTO b) {
                return Long.compare(b.getPublishedAt(), a.getPublishedAt());
            }
        });
        return withBaseUrl(list, baseUrl);
    }

    public static synchronized DesktopUpdateInfoDTO latest(String baseUrl) {
        DesktopUpdateInfoDTO latest = latestEnabled(readAll());
        if (latest == null) {
            return null;
        }
        return withBaseUrl(latest, baseUrl);
    }

    public static synchronized boolean disable(String version) throws IOException {
        String normalizedVersion = normalizeVersion(version);
        List<DesktopUpdateInfoDTO> list = readAll();
        boolean changed = false;
        for (DesktopUpdateInfoDTO item : list) {
            if (normalizedVersion.equals(item.getVersion()) && item.isEnabled()) {
                item.setEnabled(false);
                changed = true;
            }
        }
        if (changed) {
            writeAll(list);
            writeLatest(list);
        }
        return changed;
    }

    public static int pushToDesktopClients() {
        DesktopUpdateInfoDTO latest = latest(null);
        if (latest == null) {
            return 0;
        }
        int count = 0;
        for (cn.xeblog.commons.entity.User user : UserCache.listUser()) {
            if (user.getPlatform() != Platform.DESKTOP) {
                continue;
            }
            user.send(ResponseBuilder.build(null, latest, MessageType.DESKTOP_UPDATE_AVAILABLE));
            count++;
        }
        return count;
    }

    public static byte[] readPackage(String fileName) throws IOException {
        String name = safeName(fileName);
        File file = new File(storageDir(), name);
        if (!file.isFile()) {
            return null;
        }
        return Files.readAllBytes(file.toPath());
    }

    private static File storageDir() {
        String configured = System.getProperty("xechat.desktopUpdate.dir");
        if (configured == null || configured.trim().isEmpty()) {
            configured = System.getenv("XECHAT_DESKTOP_UPDATE_DIR");
        }
        if (configured == null || configured.trim().isEmpty()) {
            configured = new File(System.getProperty("user.dir"), "data/desktop-updates").getAbsolutePath();
        }
        return new File(configured);
    }

    private static void ensureDir() throws IOException {
        Files.createDirectories(storageDir().toPath());
    }

    private static List<DesktopUpdateInfoDTO> readAll() {
        File index = new File(storageDir(), INDEX_FILE);
        if (!index.isFile()) {
            return new ArrayList<>();
        }
        try {
            String json = new String(Files.readAllBytes(index.toPath()), StandardCharsets.UTF_8);
            if (json.trim().isEmpty()) {
                return new ArrayList<>();
            }
            return JSONUtil.toList(JSONUtil.parseArray(json), DesktopUpdateInfoDTO.class);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static void writeAll(List<DesktopUpdateInfoDTO> list) throws IOException {
        ensureDir();
        File index = new File(storageDir(), INDEX_FILE);
        Files.write(index.toPath(), JSONUtil.toJsonPrettyStr(list).getBytes(StandardCharsets.UTF_8));
    }

    private static void writeLatest(List<DesktopUpdateInfoDTO> list) throws IOException {
        DesktopUpdateInfoDTO latest = latestEnabled(list);
        File latestFile = new File(storageDir(), LATEST_FILE);
        String json = latest == null ? "{}" : JSONUtil.toJsonPrettyStr(latest);
        Files.write(latestFile.toPath(), json.getBytes(StandardCharsets.UTF_8));
    }

    private static DesktopUpdateInfoDTO latestEnabled(List<DesktopUpdateInfoDTO> list) {
        DesktopUpdateInfoDTO latest = null;
        for (DesktopUpdateInfoDTO item : list) {
            if (!item.isEnabled()) {
                continue;
            }
            if (latest == null || compareSemver(item.getVersion(), latest.getVersion()) > 0) {
                latest = item;
            }
        }
        return latest;
    }

    private static List<DesktopUpdateInfoDTO> withBaseUrl(List<DesktopUpdateInfoDTO> list, String baseUrl) {
        List<DesktopUpdateInfoDTO> result = new ArrayList<>(list.size());
        for (DesktopUpdateInfoDTO item : list) {
            result.add(withBaseUrl(item, baseUrl));
        }
        return result;
    }

    private static DesktopUpdateInfoDTO withBaseUrl(DesktopUpdateInfoDTO item, String baseUrl) {
        DesktopUpdateInfoDTO copy = new DesktopUpdateInfoDTO(
                item.getVersion(),
                item.getTitle(),
                item.getNotes(),
                item.isMandatory(),
                item.isEnabled(),
                item.getFileName(),
                item.getSize(),
                item.getSha256(),
                item.getPublishedAt(),
                item.getDownloadUrl()
        );
        if (baseUrl != null && !baseUrl.isEmpty()) {
            copy.setDownloadUrl(baseUrl + "/updates/desktop/" + copy.getFileName());
        }
        return copy;
    }

    private static String normalizeVersion(String version) {
        return version == null ? "" : version.trim();
    }

    private static boolean isSemver(String version) {
        return version.matches("\\d+\\.\\d+\\.\\d+");
    }

    private static int compareSemver(String a, String b) {
        String[] left = a.split("\\.");
        String[] right = b.split("\\.");
        for (int i = 0; i < 3; i++) {
            int diff = Integer.parseInt(left[i]) - Integer.parseInt(right[i]);
            if (diff != 0) {
                return diff;
            }
        }
        return 0;
    }

    private static String safeName(String fileName) {
        String name = fileName == null ? "" : new File(fileName).getName();
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return name.isEmpty() ? "XeChat-Setup.exe" : name;
    }

    private static String emptyToDefault(String value, String defaultValue) {
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前环境不支持 SHA-256", e);
        }
    }

}
