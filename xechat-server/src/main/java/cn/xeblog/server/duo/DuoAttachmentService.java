package cn.xeblog.server.duo;

import cn.xeblog.server.config.GlobalConfig;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import cn.xeblog.server.account.DbInitializer;
import org.apache.ibatis.session.SqlSession;

/**
 * 双人小屋密文附件服务。
 */
@Slf4j
public final class DuoAttachmentService {

    /** 明文图片最多 3 MiB，AES-GCM 密文额外包含 16 字节认证标签。 */
    public static final int MAX_BYTES = 3 * 1024 * 1024 + 16;
    private static final long CLEANUP_INTERVAL_MS = 60L * 60L * 1000L;
    private static volatile long lastCleanupAt;

    private DuoAttachmentService() {
    }

    public static void ensureDirectory() throws IOException {
        Files.createDirectories(directory());
    }

    public static void upload(long accountId, String spaceId, String attachmentId, InputStream input,
                              long declaredLength) throws IOException {
        maybeCleanupOrphans();
        validateId(spaceId);
        validateId(attachmentId);
        if (declaredLength > MAX_BYTES) throw new PayloadTooLargeException();
        if (!DuoSpaceService.isActiveMember(accountId, spaceId)) throw new ForbiddenException();
        ensureDirectory();
        String storageName = UUID.randomUUID().toString() + ".bin";
        Path tmp = directory().resolve(storageName + ".tmp");
        Path target = directory().resolve(storageName);
        long size = 0L;
        try {
            try (java.io.OutputStream out = Files.newOutputStream(tmp, StandardOpenOption.CREATE_NEW)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read == 0) continue;
                    size += read;
                    if (size > MAX_BYTES) throw new PayloadTooLargeException();
                    out.write(buffer, 0, read);
                }
            }
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
            try (SqlSession session = DbInitializer.factory().openSession(false)) {
                Connection conn = session.getConnection();
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO duo_attachments(id, space_id, uploader_account_id, storage_name, size_bytes, " +
                                "interaction_id, created_at) VALUES(?,?,?,?,?,NULL,?)")) {
                    ps.setString(1, attachmentId);
                    ps.setString(2, spaceId);
                    ps.setLong(3, accountId);
                    ps.setString(4, storageName);
                    ps.setLong(5, size);
                    ps.setLong(6, System.currentTimeMillis());
                    ps.executeUpdate();
                }
                session.commit();
            }
        } catch (PayloadTooLargeException e) {
            deleteQuietly(tmp);
            deleteQuietly(target);
            throw e;
        } catch (Exception e) {
            deleteQuietly(tmp);
            deleteQuietly(target);
            if (String.valueOf(e.getMessage()).toUpperCase().contains("CONSTRAINT")) {
                throw new ConflictException();
            }
            throw new IOException("保存小屋附件失败", e);
        }
    }

    public static byte[] read(long accountId, String spaceId, String attachmentId) throws IOException {
        maybeCleanupOrphans();
        validateId(spaceId);
        validateId(attachmentId);
        if (!DuoSpaceService.isActiveMember(accountId, spaceId)) throw new ForbiddenException();
        String storageName = null;
        try (SqlSession session = DbInitializer.factory().openSession(true);
             PreparedStatement ps = session.getConnection().prepareStatement(
                     "SELECT storage_name FROM duo_attachments WHERE id=? AND space_id=?")) {
            ps.setString(1, attachmentId);
            ps.setString(2, spaceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) storageName = rs.getString(1);
            }
        } catch (Exception e) {
            throw new IOException("读取小屋附件失败", e);
        }
        if (storageName == null) throw new NotFoundException();
        Path path = directory().resolve(storageName).normalize();
        if (!path.getParent().equals(directory().toAbsolutePath().normalize())) throw new NotFoundException();
        if (!Files.exists(path)) throw new NotFoundException();
        return Files.readAllBytes(path);
    }

    public static void cleanupOrphans() {
        long cutoff = System.currentTimeMillis() - 24L * 60L * 60L * 1000L;
        List<String> files = new ArrayList<>();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            Connection conn = session.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT storage_name FROM duo_attachments WHERE interaction_id IS NULL AND created_at<?")) {
                ps.setLong(1, cutoff);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) files.add(rs.getString(1));
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM duo_attachments WHERE interaction_id IS NULL AND created_at<?")) {
                ps.setLong(1, cutoff);
                ps.executeUpdate();
            }
            session.commit();
        } catch (Exception e) {
            log.warn("清理双人小屋孤儿附件记录失败", e);
        }
        for (String file : files) deleteQuietly(directory().resolve(file));
        lastCleanupAt = System.currentTimeMillis();
    }

    private static void maybeCleanupOrphans() {
        long now = System.currentTimeMillis();
        if (now - lastCleanupAt < CLEANUP_INTERVAL_MS) return;
        synchronized (DuoAttachmentService.class) {
            if (now - lastCleanupAt < CLEANUP_INTERVAL_MS) return;
            cleanupOrphans();
        }
    }

    static void deleteFilesQuietly(List<String> storageNames) {
        if (storageNames == null) return;
        for (String name : storageNames) {
            if (name == null || name.contains("/") || name.contains("\\") || name.contains("..")) continue;
            deleteQuietly(directory().resolve(name));
        }
    }

    private static Path directory() {
        return Paths.get(GlobalConfig.DUO_ATTACHMENT_DIR).toAbsolutePath().normalize();
    }

    private static void validateId(String value) {
        try {
            UUID.fromString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("附件标识格式不正确");
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception e) {
            log.warn("删除双人小屋附件文件失败 path={}", path, e);
        }
    }

    public static class ForbiddenException extends IOException {
    }

    public static class NotFoundException extends IOException {
    }

    public static class ConflictException extends IOException {
    }

    public static class PayloadTooLargeException extends IOException {
    }
}
