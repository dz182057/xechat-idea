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
 * 桌面端增量更新产物管理。
 */
public final class DesktopUpdateService {

    private static final String INDEX_FILE = "versions.json";
    private static final String ELECTRON_LATEST_FILE = "latest.yml";

    private DesktopUpdateService() {
    }

    public static synchronized DesktopUpdateInfoDTO publish(
            String version,
            String title,
            String notes,
            boolean mandatory,
            String latestFileName,
            byte[] latestBytes,
            String installerFileName,
            byte[] installerBytes,
            String blockMapFileName,
            byte[] blockMapBytes
    ) throws IOException {
        String normalizedVersion = normalizeVersion(version);
        String installerName = safeName(installerFileName);
        String blockMapName = safeName(blockMapFileName);
        String latestName = safeName(latestFileName);
        String latestText = latestBytes == null ? "" : new String(latestBytes, StandardCharsets.UTF_8);

        if (!isSemver(normalizedVersion)) {
            throw new IllegalArgumentException("版本号格式应为 x.y.z，例如 0.2.0");
        }
        if (!"latest.yml".equalsIgnoreCase(latestName)) {
            throw new IllegalArgumentException("请选择 electron-builder 生成的 latest.yml");
        }
        if (latestBytes == null || latestBytes.length == 0) {
            throw new IllegalArgumentException("请选择 latest.yml");
        }
        if (installerBytes == null || installerBytes.length == 0 || !installerName.toLowerCase().endsWith(".exe")) {
            throw new IllegalArgumentException("请选择 .exe 安装包");
        }
        if (blockMapBytes == null || blockMapBytes.length == 0 || !blockMapName.equals(installerName + ".blockmap")) {
            throw new IllegalArgumentException("请选择与安装包同名的 .blockmap 文件");
        }
        if (!latestText.contains("version: " + normalizedVersion) && !latestText.contains("version: \"" + normalizedVersion + "\"")) {
            throw new IllegalArgumentException("latest.yml 中的版本号与填写版本不一致");
        }
        if (!latestText.contains(installerName)) {
            throw new IllegalArgumentException("latest.yml 未引用当前安装包文件名");
        }

        List<DesktopUpdateInfoDTO> list = readAll();
        for (DesktopUpdateInfoDTO item : list) {
            if (normalizedVersion.equals(item.getVersion())) {
                throw new IllegalArgumentException("该版本已存在，不允许覆盖");
            }
        }

        ensureDir();
        File versionDir = versionDir(normalizedVersion);
        Files.createDirectories(versionDir.toPath());
        Files.write(new File(versionDir, ELECTRON_LATEST_FILE).toPath(), latestBytes);
        Files.write(new File(versionDir, installerName).toPath(), installerBytes);
        Files.write(new File(versionDir, blockMapName).toPath(), blockMapBytes);

        DesktopUpdateInfoDTO info = new DesktopUpdateInfoDTO();
        info.setVersion(normalizedVersion);
        info.setTitle(emptyToDefault(title, "XeChat " + normalizedVersion));
        info.setNotes(notes == null ? "" : notes.trim());
        info.setMandatory(mandatory);
        info.setEnabled(true);
        info.setFileName(versionFileName(normalizedVersion, installerName));
        info.setLatestFileName(versionFileName(normalizedVersion, ELECTRON_LATEST_FILE));
        info.setBlockMapFileName(versionFileName(normalizedVersion, blockMapName));
        info.setSize(installerBytes.length);
        info.setSha256(sha256(installerBytes));
        info.setPublishedAt(System.currentTimeMillis());
        info.setDownloadUrl("/updates/desktop/" + info.getFileName());

        list.add(info);
        writeAll(list);
        writeElectronLatest(list);
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
        return latest == null ? null : withBaseUrl(latest, baseUrl);
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
            writeElectronLatest(list);
        }
        return changed;
    }

    public static synchronized boolean enable(String version) throws IOException {
        String normalizedVersion = normalizeVersion(version);
        List<DesktopUpdateInfoDTO> list = readAll();
        boolean changed = false;
        for (DesktopUpdateInfoDTO item : list) {
            if (normalizedVersion.equals(item.getVersion()) && !item.isEnabled()) {
                item.setEnabled(true);
                changed = true;
            }
        }
        if (changed) {
            writeAll(list);
            writeElectronLatest(list);
        }
        return changed;
    }

    public static synchronized boolean delete(String version) throws IOException {
        String normalizedVersion = normalizeVersion(version);
        List<DesktopUpdateInfoDTO> list = readAll();
        boolean removed = false;
        List<DesktopUpdateInfoDTO> kept = new ArrayList<>();
        for (DesktopUpdateInfoDTO item : list) {
            if (normalizedVersion.equals(item.getVersion())) {
                removed = true;
            } else {
                kept.add(item);
            }
        }
        if (!removed) {
            return false;
        }
        deleteDirectory(versionDir(normalizedVersion));
        writeAll(kept);
        writeElectronLatest(kept);
        return true;
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

    public static byte[] readFile(String fileName) throws IOException {
        File file = storageFile(fileName);
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

    private static void writeElectronLatest(List<DesktopUpdateInfoDTO> list) throws IOException {
        ensureDir();
        DesktopUpdateInfoDTO latest = latestEnabled(list);
        File target = new File(storageDir(), ELECTRON_LATEST_FILE);
        if (latest == null) {
            Files.deleteIfExists(target.toPath());
            return;
        }
        byte[] bytes = readFile(latest.getLatestFileName());
        if (bytes != null) {
            String installerName = safeName(latest.getFileName());
            String latestText = new String(bytes, StandardCharsets.UTF_8);
            String rootLatestText = latestText.replace(installerName, latest.getFileName());
            Files.write(target.toPath(), rootLatestText.getBytes(StandardCharsets.UTF_8));
        }
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
        DesktopUpdateInfoDTO copy = new DesktopUpdateInfoDTO();
        copy.setVersion(item.getVersion());
        copy.setTitle(item.getTitle());
        copy.setNotes(item.getNotes());
        copy.setMandatory(item.isMandatory());
        copy.setEnabled(item.isEnabled());
        copy.setFileName(item.getFileName());
        copy.setLatestFileName(item.getLatestFileName());
        copy.setBlockMapFileName(item.getBlockMapFileName());
        copy.setSize(item.getSize());
        copy.setSha256(item.getSha256());
        copy.setPublishedAt(item.getPublishedAt());
        copy.setDownloadUrl(item.getDownloadUrl());
        if (baseUrl != null && !baseUrl.isEmpty()) {
            copy.setDownloadUrl(baseUrl + "/updates/desktop/" + copy.getFileName());
        }
        return copy;
    }

    private static File versionDir(String version) {
        return new File(storageDir(), version);
    }

    private static void deleteDirectory(File dir) throws IOException {
        if (!dir.exists()) {
            return;
        }
        String root = storageDir().getCanonicalPath();
        String target = dir.getCanonicalPath();
        if (!target.equals(root) && !target.startsWith(root + File.separator)) {
            return;
        }
        List<java.nio.file.Path> paths = new ArrayList<>();
        try (java.util.stream.Stream<java.nio.file.Path> stream = Files.walk(dir.toPath())) {
            stream.sorted(new Comparator<java.nio.file.Path>() {
                    @Override
                    public int compare(java.nio.file.Path a, java.nio.file.Path b) {
                        return b.compareTo(a);
                    }
                })
                    .forEach(paths::add);
        }
        for (java.nio.file.Path path : paths) {
            Files.deleteIfExists(path);
        }
    }

    private static String versionFileName(String version, String fileName) {
        return version + "/" + safeName(fileName);
    }

    private static File storageFile(String fileName) throws IOException {
        String name = fileName == null ? "" : fileName.replace('\\', '/').trim();
        if (name.contains("../") || name.startsWith("/") || name.startsWith(".")) {
            return new File(storageDir(), "not-found");
        }
        String[] parts = name.split("/");
        File file = storageDir();
        for (String part : parts) {
            file = new File(file, safeName(part));
        }
        String root = storageDir().getCanonicalPath();
        String target = file.getCanonicalPath();
        if (!target.equals(root) && !target.startsWith(root + File.separator)) {
            return new File(storageDir(), "not-found");
        }
        return file;
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
        return name.isEmpty() ? "file" : name;
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
