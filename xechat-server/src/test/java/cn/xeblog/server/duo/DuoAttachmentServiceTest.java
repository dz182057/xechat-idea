package cn.xeblog.server.duo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.UUID;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** 双人小屋附件鉴权、大小限制和孤儿清理回归测试。 */
public class DuoAttachmentServiceTest {

    @Before
    public void setUp() throws Exception {
        DuoTestSupport.setUpDatabase();
    }

    @After
    public void tearDown() throws Exception {
        DuoTestSupport.tearDownDatabase();
    }

    @Test
    public void activeMembersCanReadAttachmentButOtherAccountsCannot() throws Exception {
        String spaceId = DuoTestSupport.activateSpace();
        String attachmentId = UUID.randomUUID().toString();
        byte[] bytes = "photo-ciphertext".getBytes(StandardCharsets.UTF_8);
        DuoAttachmentService.upload(DuoTestSupport.ACCOUNT_A, spaceId, attachmentId,
                new ByteArrayInputStream(bytes), bytes.length);

        assertArrayEquals(bytes, DuoAttachmentService.read(DuoTestSupport.ACCOUNT_B, spaceId, attachmentId));
        try {
            DuoAttachmentService.read(9999L, spaceId, attachmentId);
            fail("非小屋成员不能读取附件");
        } catch (DuoAttachmentService.ForbiddenException expected) {
            // 预期拒绝越权读取。
        }
        try {
            DuoAttachmentService.upload(9999L, spaceId, UUID.randomUUID().toString(),
                    new ByteArrayInputStream(bytes), bytes.length);
            fail("非小屋成员不能上传附件");
        } catch (DuoAttachmentService.ForbiddenException expected) {
            // 预期拒绝越权上传。
        }
    }

    @Test
    public void declaredOrStreamedPayloadOverLimitIsRejected() throws Exception {
        String spaceId = DuoTestSupport.activateSpace();
        byte[] tooLarge = new byte[DuoAttachmentService.MAX_BYTES + 1];
        try {
            DuoAttachmentService.upload(DuoTestSupport.ACCOUNT_A, spaceId, UUID.randomUUID().toString(),
                    new ByteArrayInputStream(tooLarge), tooLarge.length);
            fail("超出附件限制应被拒绝");
        } catch (DuoAttachmentService.PayloadTooLargeException expected) {
            // 预期拒绝超大附件。
        }
        try {
            DuoAttachmentService.upload(DuoTestSupport.ACCOUNT_A, spaceId, UUID.randomUUID().toString(),
                    new ByteArrayInputStream(new byte[1]), DuoAttachmentService.MAX_BYTES + 1L);
            fail("声明长度超限应被拒绝");
        } catch (DuoAttachmentService.PayloadTooLargeException expected) {
            // 预期拒绝超大声明长度。
        }
    }

    @Test
    public void acceptsThreeMiBImageCiphertextAtExactBoundary() throws Exception {
        String spaceId = DuoTestSupport.activateSpace();
        String attachmentId = UUID.randomUUID().toString();
        byte[] bytes = new byte[DuoAttachmentService.MAX_BYTES];

        DuoAttachmentService.upload(DuoTestSupport.ACCOUNT_A, spaceId, attachmentId,
                new ByteArrayInputStream(bytes), bytes.length);

        assertArrayEquals(bytes, DuoAttachmentService.read(DuoTestSupport.ACCOUNT_B, spaceId, attachmentId));
    }

    @Test
    public void cleanupRemovesUnattachedOldFileAndDatabaseRow() throws Exception {
        String spaceId = DuoTestSupport.activateSpace();
        String attachmentId = UUID.randomUUID().toString();
        byte[] bytes = "orphan-ciphertext".getBytes(StandardCharsets.UTF_8);
        DuoAttachmentService.upload(DuoTestSupport.ACCOUNT_A, spaceId, attachmentId,
                new ByteArrayInputStream(bytes), bytes.length);
        String storageName = DuoTestSupport.attachmentStorageName(attachmentId);
        Path file = Path.of(cn.xeblog.server.config.GlobalConfig.DUO_ATTACHMENT_DIR, storageName);
        assertTrue(Files.exists(file));

        DuoTestSupport.markAttachmentOld(attachmentId);
        DuoAttachmentService.cleanupOrphans();

        assertFalse(Files.exists(file));
        assertTrue(DuoTestSupport.attachmentStorageName(attachmentId) == null);
    }

    @Test
    public void cleanupRemovesExpiredInterruptedUploadTempFile() throws Exception {
        Path temp = Files.createTempFile(Path.of(cn.xeblog.server.config.GlobalConfig.DUO_ATTACHMENT_DIR),
                "upload-", ".tmp");
        Files.setLastModifiedTime(temp, FileTime.fromMillis(
                System.currentTimeMillis() - 2L * 24L * 60L * 60L * 1000L));

        DuoAttachmentService.cleanupOrphans();

        assertFalse(Files.exists(temp));
    }
}
