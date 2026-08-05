package cn.xeblog.server.duo;

import cn.hutool.json.JSONUtil;
import cn.xeblog.commons.entity.duo.DuoDailyQuizDTO;
import cn.xeblog.commons.entity.duo.DuoMemoryPageDTO;
import cn.xeblog.commons.entity.duo.DuoSpaceProfileDTO;
import cn.xeblog.commons.entity.duo.EncryptedPayloadDTO;
import cn.xeblog.commons.enums.DuoSpaceStatus;
import cn.xeblog.commons.enums.DuoInteractionType;
import cn.xeblog.commons.enums.Game;
import cn.xeblog.server.account.DbInitializer;
import cn.xeblog.server.config.GlobalConfig;
import org.apache.ibatis.session.SqlSession;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** 双人小屋核心事务、隐私和清理回归测试。 */
public class DuoSpaceServiceTest {

    @Before
    public void setUp() throws Exception {
        DuoTestSupport.setUpDatabase();
    }

    @After
    public void tearDown() throws Exception {
        DuoTestSupport.tearDownDatabase();
    }

    @Test
    public void inviteRequestsRacingForTheSamePairKeepOnePendingSpace() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<Throwable> attempt = () -> {
            ready.countDown();
            start.await();
            try {
                DuoSpaceService.invite(DuoTestSupport.ACCOUNT_A, DuoTestSupport.ACCOUNT_B);
                return null;
            } catch (Throwable e) {
                return e;
            }
        };
        try {
            Future<Throwable> first = executor.submit(attempt);
            Future<Throwable> second = executor.submit(attempt);
            assertTrue("两个邀请线程都应进入临界区", ready.await(5, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();
            Throwable firstFailure = first.get(10, java.util.concurrent.TimeUnit.SECONDS);
            Throwable secondFailure = second.get(10, java.util.concurrent.TimeUnit.SECONDS);
            int successCount = (firstFailure == null ? 1 : 0) + (secondFailure == null ? 1 : 0);
            assertEquals(1, successCount);
            Throwable failure = firstFailure == null ? secondFailure : firstFailure;
            assertTrue(failure instanceof IllegalArgumentException);
            assertEquals(DuoSpaceService.ERROR_PENDING_INVITE, failure.getMessage());
        } finally {
            executor.shutdownNow();
        }
        assertEquals(1L, DuoTestSupport.count("duo_spaces"));
        assertEquals(DuoSpaceStatus.OUTGOING_INVITE, DuoSpaceService.profile(DuoTestSupport.ACCOUNT_A).getStatus());
    }

    @Test
    public void interactionAndSharedPlayWarmthAreAwardedOnlyOncePerDay() {
        long now = DuoTestSupport.atNoon(LocalDate.of(2026, 7, 1));
        DuoTestSupport.setNow(now);
        DuoTestSupport.activateSpace();

        DuoSpaceService.submitInteraction(DuoTestSupport.ACCOUNT_A, DuoInteractionType.WAVE_HELLO, null, null);
        DuoSpaceService.submitInteraction(DuoTestSupport.ACCOUNT_B, DuoInteractionType.WAVE_HELLO, null, null);
        assertEquals(1, DuoSpaceService.profile(DuoTestSupport.ACCOUNT_A).getWarmth());

        long nextDay = DuoTestSupport.atNoon(LocalDate.of(2026, 7, 2));
        DuoSpaceService.recordSharedPlay(Arrays.asList(DuoTestSupport.ACCOUNT_A, DuoTestSupport.ACCOUNT_B),
                Game.TACIT_QUIZ, nextDay);
        DuoSpaceService.recordSharedPlay(Arrays.asList(DuoTestSupport.ACCOUNT_A, DuoTestSupport.ACCOUNT_B),
                Game.TACIT_QUIZ, nextDay);
        assertEquals(2, DuoSpaceService.profile(DuoTestSupport.ACCOUNT_A).getWarmth());
    }

    @Test
    public void quizAnswersStayPrivateUntilBothPlayersAnswerAndRespectViewerPerspective() {
        DuoTestSupport.setNow(DuoTestSupport.atNoon(LocalDate.of(2026, 7, 3)));
        DuoTestSupport.activateSpace();
        DuoDailyQuizDTO initial = DuoSpaceService.profile(DuoTestSupport.ACCOUNT_A).getToday().getQuiz();
        assertNotNull(initial);
        assertFalse(initial.isUnavailable());

        DuoSpaceService.submitDailyQuiz(DuoTestSupport.ACCOUNT_A, 0);
        DuoDailyQuizDTO waiting = DuoSpaceService.profile(DuoTestSupport.ACCOUNT_A).getToday().getQuiz();
        assertEquals(Integer.valueOf(0), waiting.getMyChoiceIndex());
        assertFalse(waiting.isPartnerAnswered());
        assertNull(waiting.getPartnerChoiceIndex());
        assertNull(waiting.getMatched());

        DuoSpaceService.submitDailyQuiz(DuoTestSupport.ACCOUNT_B, 1);
        DuoDailyQuizDTO revealedForHighAccount = DuoSpaceService.profile(DuoTestSupport.ACCOUNT_B)
                .getToday().getQuiz();
        assertEquals(Integer.valueOf(1), revealedForHighAccount.getMyChoiceIndex());
        assertEquals(Integer.valueOf(0), revealedForHighAccount.getPartnerChoiceIndex());
        assertTrue(revealedForHighAccount.isPartnerAnswered());
        assertEquals(Boolean.FALSE, revealedForHighAccount.getMatched());
        assertNotNull(revealedForHighAccount.getCompletedAt());
    }

    @Test
    public void memoryListingIgnoresDailyQuizThatHasNotBeenCompleted() {
        DuoTestSupport.setNow(DuoTestSupport.atNoon(LocalDate.of(2026, 7, 5)));
        DuoTestSupport.activateSpace();

        DuoSpaceProfileDTO profile = DuoSpaceService.profile(DuoTestSupport.ACCOUNT_A);
        assertNotNull(profile.getToday().getQuiz());
        assertTrue(profile.getToday().getQuiz().getCompletedAt() == null);

        DuoMemoryPageDTO memories = DuoSpaceService.listMemories(DuoTestSupport.ACCOUNT_A, null);
        assertTrue(memories.getItems().isEmpty());
        assertFalse(memories.isHasMore());
    }

    @Test
    public void memoryListingUsesThirtyDayCursor() {
        DuoTestSupport.setNow(DuoTestSupport.atNoon(LocalDate.of(2026, 1, 1)));
        DuoTestSupport.activateSpace();
        for (int offset = 0; offset < 31; offset++) {
            DuoTestSupport.setNow(DuoTestSupport.atNoon(LocalDate.of(2026, 1, 1).plusDays(offset)));
            DuoSpaceService.submitInteraction(DuoTestSupport.ACCOUNT_A, DuoInteractionType.WAVE_HELLO, null, null);
        }

        DuoMemoryPageDTO firstPage = DuoSpaceService.listMemories(DuoTestSupport.ACCOUNT_A, null);
        assertEquals(30, firstPage.getItems().size());
        assertTrue(firstPage.isHasMore());
        assertEquals("2026-01-02", firstPage.getNextBeforeDate());

        DuoMemoryPageDTO secondPage = DuoSpaceService.listMemories(
                DuoTestSupport.ACCOUNT_A, firstPage.getNextBeforeDate());
        assertEquals(1, secondPage.getItems().size());
        assertFalse(secondPage.isHasMore());
        assertEquals("2026-01-01", secondPage.getItems().get(0).getDate());
    }

    @Test
    public void encryptedInteractionDoesNotExposePlaintextAndClosingSpaceCascadesAttachments() throws Exception {
        String spaceId = DuoTestSupport.activateSpace();
        DuoTestSupport.setNow(DuoTestSupport.atNoon(LocalDate.of(2026, 7, 4)));
        String attachmentId = java.util.UUID.randomUUID().toString();
        byte[] attachment = "encrypted-photo".getBytes(StandardCharsets.UTF_8);
        DuoAttachmentService.upload(DuoTestSupport.ACCOUNT_A, spaceId, attachmentId,
                new java.io.ByteArrayInputStream(attachment), attachment.length);
        String storageName = DuoTestSupport.attachmentStorageName(attachmentId);
        Path storagePath = Path.of(GlobalConfig.DUO_ATTACHMENT_DIR, storageName);
        assertTrue(Files.exists(storagePath));

        String iv = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[12]);
        String ciphertext = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "ciphertext-only".getBytes(StandardCharsets.UTF_8));
        DuoSpaceService.submitInteraction(DuoTestSupport.ACCOUNT_A, DuoInteractionType.WAVE_HELLO,
                new EncryptedPayloadDTO("v1", iv, ciphertext), attachmentId);

        DuoSpaceProfileDTO profile = DuoSpaceService.profile(DuoTestSupport.ACCOUNT_B);
        assertNotNull(profile.getToday().getPartnerInteraction().getEncryptedPayload());
        assertEquals(ciphertext, profile.getToday().getPartnerInteraction().getEncryptedPayload().getCiphertext());
        assertFalse(JSONUtil.toJsonStr(profile).contains("明文秘密"));

        DuoSpaceService.closeSpace(DuoTestSupport.ACCOUNT_B);
        assertFalse(Files.exists(storagePath));
        assertEquals(0L, DuoTestSupport.count("duo_spaces"));
        assertEquals(0L, DuoTestSupport.count("duo_members"));
        assertEquals(0L, DuoTestSupport.count("duo_interactions"));
        assertEquals(0L, DuoTestSupport.count("duo_attachments"));
        assertEquals(DuoSpaceStatus.NONE, DuoSpaceService.profile(DuoTestSupport.ACCOUNT_A).getStatus());
    }
}
