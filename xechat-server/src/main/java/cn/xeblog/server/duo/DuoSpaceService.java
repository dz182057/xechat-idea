package cn.xeblog.server.duo;

import cn.hutool.json.JSONUtil;
import cn.xeblog.commons.entity.duo.DuoDailyQuizDTO;
import cn.xeblog.commons.entity.duo.DuoDogSnapshotDTO;
import cn.xeblog.commons.entity.duo.DuoInviteDTO;
import cn.xeblog.commons.entity.duo.DuoInteractionDTO;
import cn.xeblog.commons.entity.duo.DuoMemoryDTO;
import cn.xeblog.commons.entity.duo.DuoMemoryPageDTO;
import cn.xeblog.commons.entity.duo.DuoPartnerDTO;
import cn.xeblog.commons.entity.duo.DuoQuestionDTO;
import cn.xeblog.commons.entity.duo.DuoSpaceProfileDTO;
import cn.xeblog.commons.entity.duo.DuoSpaceResponseDTO;
import cn.xeblog.commons.entity.duo.DuoTodayDTO;
import cn.xeblog.commons.entity.duo.EncryptedPayloadDTO;
import cn.xeblog.commons.enums.DuoDecoration;
import cn.xeblog.commons.enums.DuoInteractionType;
import cn.xeblog.commons.enums.DuoSpaceEvent;
import cn.xeblog.commons.enums.DuoSpaceStatus;
import cn.xeblog.commons.enums.Game;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.commons.entity.User;
import cn.xeblog.server.account.DbInitializer;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.cache.UserCache;
import cn.xeblog.server.push.WebPushService;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSession;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * 双人小屋领域服务。
 *
 * <p>所有涉及唯一约束、每日进度和温暖值的写操作都在同一个 SQLite 事务中完成，客户端只负责
 * 发送意图和渲染服务端返回的资料。</p>
 */
@Slf4j
public final class DuoSpaceService {

    public static final String ERROR_INVITE_FRIEND = "只能邀请好友建立双人小屋";
    public static final String ERROR_ONE_SPACE = "每个账号只能拥有一个双人小屋";
    public static final String ERROR_EXPIRED = "这份小屋邀请已经过期";
    public static final String ERROR_OWN_DOG = "只能选择自己的狗狗";
    public static final String ERROR_INTERACTION_DUPLICATE = "今天已经在小屋留下过互动了";
    public static final String ERROR_QUIZ_DUPLICATE = "今天的默契题已经回答过了";
    public static final String ERROR_INVALID_CHOICE = "请选择有效答案";
    public static final String ERROR_INTERACTION_UNAVAILABLE = "这个互动动作暂不可用";
    public static final String ERROR_NO_SPACE = "还没有可以进入的双人小屋";
    public static final String ERROR_PENDING_INVITE = "已有待处理的小屋邀请，请先处理";
    public static final String NO_QUIZ_MESSAGE = "今天暂时没有可用的默契题";
    private static final long INVITE_TTL_MS = 7L * 24L * 60L * 60L * 1000L;
    private static final int RECENT_MEMORY_LIMIT = 10;
    private static final int MEMORY_PAGE_LIMIT = 30;
    private static final LongSupplier SYSTEM_NOW = System::currentTimeMillis;
    private static volatile LongSupplier nowSupplier = SYSTEM_NOW;

    private DuoSpaceService() {
    }

    public static DuoSpaceProfileDTO profile(long accountId) {
        requireAccount(accountId);
        long now = now();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            Connection conn = session.getConnection();
            cleanupExpiredPending(conn, now);
            SpaceRow space = findSpaceByAccount(conn, accountId);
            String date = serverDate(now);
            DuoSpaceProfileDTO result;
            if (space == null) {
                result = emptyProfile(date);
            } else if ("PENDING".equals(space.status)) {
                result = buildPendingProfile(conn, space, accountId, date);
            } else {
                ensureDailyQuiz(conn, space.id, date, now);
                result = buildActiveProfile(conn, space, accountId, date);
            }
            session.commit();
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("读取双人小屋失败", e);
        }
    }

    public static void invite(long accountId, long partnerAccountId) {
        requireAccount(accountId);
        long now = now();
        if (partnerAccountId <= 0L || partnerAccountId == accountId) {
            throw new IllegalArgumentException(ERROR_INVITE_FRIEND);
        }
        List<Long> notifyAccounts = new ArrayList<>();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            Connection conn = session.getConnection();
            cleanupExpiredPending(conn, now);
            if (!accountExists(conn, partnerAccountId) || !areFriends(conn, accountId, partnerAccountId)) {
                throw new IllegalArgumentException(ERROR_INVITE_FRIEND);
            }
            if (findSpaceByAccount(conn, accountId) != null || findSpaceByAccount(conn, partnerAccountId) != null) {
                throw new IllegalArgumentException(resolveOccupiedError(conn, accountId, partnerAccountId));
            }
            String spaceId = UUID.randomUUID().toString();
            long low = Math.min(accountId, partnerAccountId);
            long high = Math.max(accountId, partnerAccountId);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO duo_spaces(id, account_low_id, account_high_id, invited_by_account_id, status, " +
                            "warmth, created_at, activated_at, expires_at, updated_at) VALUES(?,?,?,?,?,?,?,?,?,?)")) {
                ps.setString(1, spaceId);
                ps.setLong(2, low);
                ps.setLong(3, high);
                ps.setLong(4, accountId);
                ps.setString(5, "PENDING");
                ps.setInt(6, 0);
                ps.setLong(7, now);
                ps.setObject(8, null);
                ps.setLong(9, now + INVITE_TTL_MS);
                ps.setLong(10, now);
                ps.executeUpdate();
            } catch (SQLException e) {
                if (isConstraint(e)) {
                    throw new IllegalArgumentException(ERROR_PENDING_INVITE);
                }
                throw e;
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO duo_members(space_id, account_id, selected_dog_id, joined_at) VALUES(?,?,?,?)")) {
                insertMember(ps, spaceId, accountId, null, null);
                insertMember(ps, spaceId, partnerAccountId, null, null);
            }
            session.commit();
            notifyAccounts.add(accountId);
            notifyAccounts.add(partnerAccountId);
        } catch (SQLException e) {
            throw new IllegalStateException("创建双人小屋邀请失败", e);
        }
        pushProfiles(notifyAccounts);
        AccountView inviter = accountView(accountId);
        if (inviter != null) {
            WebPushService.pushDuoInvite(partnerAccountId, inviter.nickname, findSpaceId(accountId));
        }
    }

    public static void respondInvite(long accountId, boolean accept) {
        requireAccount(accountId);
        long now = now();
        List<Long> notifyAccounts;
        List<String> attachmentFiles = new ArrayList<>();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            Connection conn = session.getConnection();
            SpaceRow space = findSpaceByAccount(conn, accountId);
            if (space == null || !"PENDING".equals(space.status)) {
                throw new IllegalArgumentException(ERROR_NO_SPACE);
            }
            if (space.expiresAt != null && space.expiresAt <= now) {
                attachmentFiles.addAll(deleteSpaceRows(conn, space.id));
                session.commit();
                DuoAttachmentService.deleteFilesQuietly(attachmentFiles);
                throw new IllegalArgumentException(ERROR_EXPIRED);
            }
            if (space.invitedByAccountId == accountId) {
                throw new IllegalArgumentException(ERROR_PENDING_INVITE);
            }
            if (!accept) {
                attachmentFiles.addAll(deleteSpaceRows(conn, space.id));
                notifyAccounts = Arrays.asList(space.accountLowId, space.accountHighId);
            } else {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE duo_spaces SET status='ACTIVE', activated_at=?, expires_at=NULL, updated_at=? WHERE id=?")) {
                    ps.setLong(1, now);
                    ps.setLong(2, now);
                    ps.setString(3, space.id);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE duo_members SET joined_at=?, selected_dog_id=? WHERE space_id=? AND account_id=?")) {
                    updateMemberAfterActivation(ps, space.id, space.accountLowId, defaultDogId(conn, space.accountLowId), now);
                    updateMemberAfterActivation(ps, space.id, space.accountHighId, defaultDogId(conn, space.accountHighId), now);
                }
                notifyAccounts = Arrays.asList(space.accountLowId, space.accountHighId);
            }
            session.commit();
        } catch (SQLException e) {
            throw new IllegalStateException("处理双人小屋邀请失败", e);
        }
        DuoAttachmentService.deleteFilesQuietly(attachmentFiles);
        pushProfiles(notifyAccounts);
    }

    public static void cancelInvite(long accountId) {
        requireAccount(accountId);
        List<Long> notifyAccounts;
        List<String> attachmentFiles = new ArrayList<>();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            Connection conn = session.getConnection();
            SpaceRow space = findSpaceByAccount(conn, accountId);
            if (space == null || !"PENDING".equals(space.status) || space.invitedByAccountId != accountId) {
                throw new IllegalArgumentException(ERROR_NO_SPACE);
            }
            attachmentFiles.addAll(deleteSpaceRows(conn, space.id));
            notifyAccounts = Arrays.asList(space.accountLowId, space.accountHighId);
            session.commit();
        } catch (SQLException e) {
            throw new IllegalStateException("取消双人小屋邀请失败", e);
        }
        DuoAttachmentService.deleteFilesQuietly(attachmentFiles);
        pushProfiles(notifyAccounts);
    }

    public static void setDog(long accountId, String dogId) {
        requireAccount(accountId);
        List<Long> notifyAccounts;
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            Connection conn = session.getConnection();
            SpaceRow space = requireActiveSpace(conn, accountId);
            if (dogId != null && !dogOwnedBy(conn, dogId, accountId)) {
                throw new IllegalArgumentException(ERROR_OWN_DOG);
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE duo_members SET selected_dog_id=? WHERE space_id=? AND account_id=?")) {
                ps.setString(1, dogId);
                ps.setString(2, space.id);
                ps.setLong(3, accountId);
                ps.executeUpdate();
            }
            session.commit();
            notifyAccounts = Arrays.asList(space.accountLowId, space.accountHighId);
        } catch (SQLException e) {
            throw new IllegalStateException("设置小屋狗狗失败", e);
        }
        pushProfiles(notifyAccounts);
    }

    public static void submitInteraction(long accountId, DuoInteractionType gesture,
                                         EncryptedPayloadDTO payload, String attachmentId) {
        requireAccount(accountId);
        if (gesture == null) {
            throw new IllegalArgumentException(ERROR_INTERACTION_UNAVAILABLE);
        }
        validatePayload(payload);
        if (attachmentId != null && !isUuid(attachmentId)) {
            throw new IllegalArgumentException(ERROR_INTERACTION_UNAVAILABLE);
        }
        long now = now();
        String date = serverDate(now);
        String interactionId = UUID.randomUUID().toString();
        List<Long> notifyAccounts;
        long partnerId;
        String spaceId;
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            Connection conn = session.getConnection();
            SpaceRow space = requireActiveSpace(conn, accountId);
            partnerId = space.other(accountId);
            DuoDogSnapshotDTO partnerDog = findDogSnapshot(conn, partnerId, selectedDogId(conn, space.id, partnerId));
            DuoDogSnapshotDTO myDog = findDogSnapshot(conn, accountId, selectedDogId(conn, space.id, accountId));
            if (!interactionAvailable(gesture, myDog, partnerDog)) {
                throw new IllegalArgumentException(ERROR_INTERACTION_UNAVAILABLE);
            }
            if (interactionExists(conn, space.id, date, accountId)) {
                throw new IllegalArgumentException(ERROR_INTERACTION_DUPLICATE);
            }
            if (attachmentId != null && !attachmentReady(conn, attachmentId, space.id, accountId)) {
                throw new IllegalArgumentException(ERROR_INTERACTION_UNAVAILABLE);
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO duo_interactions(id, space_id, server_date, actor_account_id, gesture, " +
                            "payload_version, payload_iv, payload_ciphertext, attachment_id, created_at, viewed_at) " +
                            "VALUES(?,?,?,?,?,?,?,?,?,?,NULL)")) {
                ps.setString(1, interactionId);
                ps.setString(2, space.id);
                ps.setString(3, date);
                ps.setLong(4, accountId);
                ps.setString(5, gesture.name());
                ps.setString(6, payload == null ? null : payload.getVersion());
                ps.setString(7, payload == null ? null : payload.getIv());
                ps.setString(8, payload == null ? null : payload.getCiphertext());
                ps.setString(9, attachmentId);
                ps.setLong(10, now);
                ps.executeUpdate();
            } catch (SQLException e) {
                if (isConstraint(e)) {
                    throw new IllegalArgumentException(ERROR_INTERACTION_DUPLICATE);
                }
                throw e;
            }
            if (attachmentId != null) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE duo_attachments SET interaction_id=? WHERE id=? AND interaction_id IS NULL")) {
                    ps.setString(1, interactionId);
                    ps.setString(2, attachmentId);
                    if (ps.executeUpdate() != 1) {
                        throw new IllegalArgumentException(ERROR_INTERACTION_UNAVAILABLE);
                    }
                }
            }
            ensureProgress(conn, space.id, date, now);
            if (countInteractions(conn, space.id, date) >= 2
                    && markProgressAwarded(conn, space.id, date, "interaction_awarded")) {
                incrementWarmth(conn, space.id, now);
            }
            session.commit();
            notifyAccounts = Arrays.asList(space.accountLowId, space.accountHighId);
            spaceId = space.id;
        } catch (SQLException e) {
            throw new IllegalStateException("留下小屋互动失败", e);
        }
        pushProfiles(notifyAccounts);
        AccountView actor = accountView(accountId);
        if (actor != null) {
            WebPushService.pushDuoInteraction(partnerId, actor.nickname, spaceId);
        }
    }

    public static void ackInteraction(long accountId, String interactionId) {
        requireAccount(accountId);
        if (interactionId == null || interactionId.trim().isEmpty()) {
            throw new IllegalArgumentException(ERROR_INTERACTION_UNAVAILABLE);
        }
        List<Long> notifyAccounts;
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            Connection conn = session.getConnection();
            SpaceRow space = requireActiveSpace(conn, accountId);
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE duo_interactions SET viewed_at=? WHERE id=? AND space_id=? AND actor_account_id<>?")) {
                ps.setLong(1, now());
                ps.setString(2, interactionId);
                ps.setString(3, space.id);
                ps.setLong(4, accountId);
                if (ps.executeUpdate() <= 0) {
                    throw new IllegalArgumentException(ERROR_INTERACTION_UNAVAILABLE);
                }
            }
            session.commit();
            notifyAccounts = Arrays.asList(space.accountLowId, space.accountHighId);
        } catch (SQLException e) {
            throw new IllegalStateException("确认小屋互动失败", e);
        }
        pushProfiles(notifyAccounts);
    }

    public static void submitDailyQuiz(long accountId, Integer choiceIndex) {
        requireAccount(accountId);
        if (choiceIndex == null) {
            throw new IllegalArgumentException(ERROR_INVALID_CHOICE);
        }
        long now = now();
        String date = serverDate(now);
        List<Long> notifyAccounts;
        long partnerId;
        String spaceId;
        boolean completed;
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            Connection conn = session.getConnection();
            SpaceRow space = requireActiveSpace(conn, accountId);
            ensureDailyQuiz(conn, space.id, date, now);
            QuizRow quiz = findQuiz(conn, space.id, date);
            if (quiz == null) {
                throw new IllegalArgumentException(NO_QUIZ_MESSAGE);
            }
            List<String> options = questionOptions(conn, quiz.questionId);
            if (choiceIndex < 0 || choiceIndex >= options.size()) {
                throw new IllegalArgumentException(ERROR_INVALID_CHOICE);
            }
            if (answerExists(conn, quiz.id, accountId)) {
                throw new IllegalArgumentException(ERROR_QUIZ_DUPLICATE);
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO duo_daily_quiz_answers(quiz_id, account_id, choice_index, answered_at) VALUES(?,?,?,?)")) {
                ps.setString(1, quiz.id);
                ps.setLong(2, accountId);
                ps.setInt(3, choiceIndex);
                ps.setLong(4, now);
                ps.executeUpdate();
            } catch (SQLException e) {
                if (isConstraint(e)) {
                    throw new IllegalArgumentException(ERROR_QUIZ_DUPLICATE);
                }
                throw e;
            }
            partnerId = space.other(accountId);
            completed = answerExists(conn, quiz.id, partnerId);
            if (completed) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE duo_daily_quizzes SET completed_at=? WHERE id=? AND completed_at IS NULL")) {
                    ps.setLong(1, now);
                    ps.setString(2, quiz.id);
                    ps.executeUpdate();
                }
                ensureProgress(conn, space.id, date, now);
                if (markProgressAwarded(conn, space.id, date, "play_awarded")) {
                    incrementWarmth(conn, space.id, now);
                }
            }
            session.commit();
            notifyAccounts = Arrays.asList(space.accountLowId, space.accountHighId);
            spaceId = space.id;
        } catch (SQLException e) {
            throw new IllegalStateException("提交小屋每日默契题失败", e);
        }
        pushProfiles(notifyAccounts);
        AccountView actor = accountView(accountId);
        if (actor != null) {
            if (completed) {
                WebPushService.pushDuoQuizRevealed(partnerId, actor.nickname);
            } else {
                WebPushService.pushDuoQuizAnswered(partnerId, actor.nickname, spaceId);
            }
        }
    }

    public static DuoMemoryPageDTO listMemories(long accountId, String beforeDate) {
        requireAccount(accountId);
        if (beforeDate != null && !beforeDate.trim().isEmpty()) {
            parseDate(beforeDate);
        }
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            Connection conn = session.getConnection();
            SpaceRow space = requireActiveSpace(conn, accountId);
            List<String> dates = memoryDates(conn, space.id, beforeDate, MEMORY_PAGE_LIMIT + 1);
            boolean hasMore = dates.size() > MEMORY_PAGE_LIMIT;
            if (hasMore) {
                dates = new ArrayList<>(dates.subList(0, MEMORY_PAGE_LIMIT));
            }
            List<DuoMemoryDTO> items = new ArrayList<>();
            for (String date : dates) {
                items.add(buildMemory(conn, space, date));
            }
            String next = items.isEmpty() ? null : items.get(items.size() - 1).getDate();
            return new DuoMemoryPageDTO(items, hasMore, next);
        } catch (SQLException e) {
            throw new IllegalStateException("读取小屋回忆失败", e);
        }
    }

    public static void closeSpace(long accountId) {
        requireAccount(accountId);
        List<Long> notifyAccounts;
        List<String> attachmentFiles = new ArrayList<>();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            Connection conn = session.getConnection();
            SpaceRow space = findSpaceByAccount(conn, accountId);
            if (space == null) {
                throw new IllegalArgumentException(ERROR_NO_SPACE);
            }
            attachmentFiles.addAll(deleteSpaceRows(conn, space.id));
            notifyAccounts = Arrays.asList(space.accountLowId, space.accountHighId);
            session.commit();
        } catch (SQLException e) {
            throw new IllegalStateException("关闭双人小屋失败", e);
        }
        DuoAttachmentService.deleteFilesQuietly(attachmentFiles);
        pushProfiles(notifyAccounts);
    }

    /**
     * 账号删除前调用，释放小屋账号唯一占用和密文附件。
     */
    public static void closeByAccount(long accountId) {
        if (accountId <= 0L) {
            return;
        }
        try {
            closeSpace(accountId);
        } catch (IllegalArgumentException e) {
            if (!ERROR_NO_SPACE.equals(e.getMessage())) {
                throw e;
            }
        }
    }

    /**
     * 现有默契问答完成后调用，共享每日玩法奖励与实时游戏复用同一标记。
     */
    public static void recordSharedPlay(List<Long> accountIds, Game game, long timestamp) {
        if (game != Game.TACIT_QUIZ || accountIds == null || accountIds.size() != 2
                || accountIds.get(0) == null || accountIds.get(1) == null
                || accountIds.get(0) <= 0L || accountIds.get(1) <= 0L
                || accountIds.get(0).equals(accountIds.get(1))) {
            return;
        }
        long a = accountIds.get(0);
        long b = accountIds.get(1);
        String date = serverDate(timestamp);
        List<Long> notifyAccounts = new ArrayList<>();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            Connection conn = session.getConnection();
            SpaceRow space = findSpaceByAccount(conn, a);
            if (space == null || !"ACTIVE".equals(space.status) || space.other(a) != b) {
                return;
            }
            ensureProgress(conn, space.id, date, timestamp);
            if (markProgressAwarded(conn, space.id, date, "play_awarded")) {
                incrementWarmth(conn, space.id, timestamp);
                session.commit();
                notifyAccounts.add(space.accountLowId);
                notifyAccounts.add(space.accountHighId);
            } else {
                session.rollback();
                return;
            }
        } catch (Exception e) {
            log.warn("记录双人小屋共同玩法温暖值失败 accountIds={}", accountIds, e);
            return;
        }
        pushProfiles(notifyAccounts);
    }

    public static boolean isActiveMember(long accountId, String spaceId) {
        if (accountId <= 0L || !isUuid(spaceId)) {
            return false;
        }
        try (SqlSession session = DbInitializer.factory().openSession(true);
             PreparedStatement ps = session.getConnection().prepareStatement(
                     "SELECT 1 FROM duo_spaces s JOIN duo_members m ON m.space_id=s.id " +
                             "WHERE s.id=? AND s.status='ACTIVE' AND m.account_id=?")) {
            ps.setString(1, spaceId);
            ps.setLong(2, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            return false;
        }
    }

    static void setNowSupplierForTest(LongSupplier supplier) {
        nowSupplier = supplier == null ? SYSTEM_NOW : supplier;
    }

    static void resetNowSupplier() {
        nowSupplier = SYSTEM_NOW;
    }

    private static void requireAccount(long accountId) {
        if (accountId <= 0L) {
            throw new IllegalArgumentException("请先登录账号");
        }
    }

    private static long now() {
        return nowSupplier.getAsLong();
    }

    private static String serverDate(long timestamp) {
        return Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate().toString();
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("回忆日期游标格式不正确");
        }
    }

    private static DuoSpaceProfileDTO emptyProfile(String date) {
        return new DuoSpaceProfileDTO(DuoSpaceStatus.NONE, date, null, null, null, null, null,
                0, Collections.emptyList(), null, Collections.emptyList(), false);
    }

    private static DuoSpaceProfileDTO buildPendingProfile(Connection conn, SpaceRow space,
                                                           long accountId, String date) throws SQLException {
        long partnerId = space.other(accountId);
        AccountView partner = accountView(conn, partnerId);
        DuoSpaceStatus status = space.invitedByAccountId == accountId
                ? DuoSpaceStatus.OUTGOING_INVITE : DuoSpaceStatus.INCOMING_INVITE;
        DuoInviteDTO invite = new DuoInviteDTO(space.invitedByAccountId, space.expiresAt == null ? 0L : space.expiresAt);
        return new DuoSpaceProfileDTO(status, date, space.id, invite, partner == null ? null : partner.toDTO(),
                null, null, 0, Collections.emptyList(), null, Collections.emptyList(), false);
    }

    private static DuoSpaceProfileDTO buildActiveProfile(Connection conn, SpaceRow space,
                                                         long accountId, String date) throws SQLException {
        long partnerId = space.other(accountId);
        String myDogId = selectedDogId(conn, space.id, accountId);
        String partnerDogId = selectedDogId(conn, space.id, partnerId);
        DuoDogSnapshotDTO myDog = findDogSnapshot(conn, accountId, myDogId);
        DuoDogSnapshotDTO partnerDog = findDogSnapshot(conn, partnerId, partnerDogId);
        DuoDailyQuizDTO quiz = buildDailyQuiz(conn, space.id, date, accountId);
        DuoInteractionDTO myInteraction = findInteraction(conn, space.id, date, accountId);
        DuoInteractionDTO partnerInteraction = findInteraction(conn, space.id, date, partnerId);
        ProgressRow progress = findProgress(conn, space.id, date);
        DuoTodayDTO today = new DuoTodayDTO(date, myInteraction, partnerInteraction, quiz,
                progress != null && progress.interactionAwarded,
                progress != null && progress.playAwarded);
        List<String> dates = memoryDates(conn, space.id, null, RECENT_MEMORY_LIMIT + 1);
        boolean hasMore = dates.size() > RECENT_MEMORY_LIMIT;
        if (hasMore) dates = new ArrayList<>(dates.subList(0, RECENT_MEMORY_LIMIT));
        List<DuoMemoryDTO> recent = new ArrayList<>();
        for (String item : dates) recent.add(buildMemory(conn, space, item));
        AccountView partner = accountView(conn, partnerId);
        return new DuoSpaceProfileDTO(DuoSpaceStatus.ACTIVE, date, space.id, null,
                partner == null ? null : partner.toDTO(), myDog, partnerDog, space.warmth,
                unlockedDecorations(space.warmth), today, recent, hasMore);
    }

    private static List<DuoDecoration> unlockedDecorations(int warmth) {
        List<DuoDecoration> result = new ArrayList<>();
        if (warmth >= 3) result.add(DuoDecoration.WARM_LAMP);
        if (warmth >= 10) result.add(DuoDecoration.DUAL_DOG_PHOTO_WALL);
        if (warmth >= 30) result.add(DuoDecoration.TOY_BASKET);
        if (warmth >= 60) result.add(DuoDecoration.WINDOW_STAR_LIGHT);
        return result;
    }

    private static DuoMemoryDTO buildMemory(Connection conn, SpaceRow space, String date) throws SQLException {
        List<DuoInteractionDTO> interactions = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, server_date, actor_account_id, gesture, payload_version, payload_iv, " +
                        "payload_ciphertext, attachment_id, created_at, viewed_at FROM duo_interactions " +
                        "WHERE space_id=? AND server_date=? ORDER BY created_at ASC")) {
            ps.setString(1, space.id);
            ps.setString(2, date);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) interactions.add(interactionFrom(rs));
            }
        }
        QuizRow quiz = findQuiz(conn, space.id, date);
        DuoDailyQuizDTO quizResult = null;
        if (quiz != null && bothAnswers(conn, quiz.id, space.accountLowId, space.accountHighId)) {
            quizResult = buildDailyQuiz(conn, space.id, date, space.accountLowId);
        }
        ProgressRow progress = findProgress(conn, space.id, date);
        return new DuoMemoryDTO(date, interactions, quizResult,
                progress != null && progress.interactionAwarded,
                progress != null && progress.playAwarded);
    }

    private static DuoDailyQuizDTO buildDailyQuiz(Connection conn, String spaceId, String date,
                                                   long accountId) throws SQLException {
        QuizRow quiz = findQuiz(conn, spaceId, date);
        if (quiz == null) {
            return new DuoDailyQuizDTO(null, true, NO_QUIZ_MESSAGE, null, null, false, null, null, null);
        }
        DuoQuestionDTO question = question(conn, quiz.questionId);
        if (question == null) {
            return new DuoDailyQuizDTO(quiz.id, true, NO_QUIZ_MESSAGE, null, null, false, null, null, null);
        }
        long partnerId = otherMemberId(conn, spaceId, accountId);
        Integer myChoice = answerChoice(conn, quiz.id, accountId);
        Integer partnerChoice = answerChoice(conn, quiz.id, partnerId);
        boolean partnerAnswered = partnerChoice != null;
        boolean both = myChoice != null && partnerChoice != null;
        Boolean matched = both ? myChoice.equals(partnerChoice) : null;
        return new DuoDailyQuizDTO(quiz.id, false, null, question, myChoice, partnerAnswered,
                both ? partnerChoice : null, matched, quiz.completedAt);
    }

    private static DuoInteractionDTO findInteraction(Connection conn, String spaceId, String date,
                                                     long accountId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, server_date, actor_account_id, gesture, payload_version, payload_iv, " +
                        "payload_ciphertext, attachment_id, created_at, viewed_at FROM duo_interactions " +
                        "WHERE space_id=? AND server_date=? AND actor_account_id=?")) {
            ps.setString(1, spaceId);
            ps.setString(2, date);
            ps.setLong(3, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? interactionFrom(rs) : null;
            }
        }
    }

    private static DuoInteractionDTO interactionFrom(ResultSet rs) throws SQLException {
        String version = rs.getString("payload_version");
        EncryptedPayloadDTO payload = version == null ? null
                : new EncryptedPayloadDTO(version, rs.getString("payload_iv"), rs.getString("payload_ciphertext"));
        long viewedAt = rs.getLong("viewed_at");
        Long viewed = rs.wasNull() ? null : viewedAt;
        return new DuoInteractionDTO(rs.getString("id"), rs.getString("server_date"),
                rs.getLong("actor_account_id"), DuoInteractionType.valueOf(rs.getString("gesture")),
                payload, rs.getString("attachment_id"), rs.getLong("created_at"), viewed);
    }

    private static List<String> memoryDates(Connection conn, String spaceId, String beforeDate, int limit)
            throws SQLException {
        String comparator = beforeDate == null || beforeDate.trim().isEmpty() ? "" : " AND server_date < ?";
        String sql = "SELECT server_date FROM (" +
                "SELECT server_date FROM duo_interactions WHERE space_id=?" + comparator +
                " UNION SELECT server_date FROM duo_daily_quizzes WHERE space_id=?" + comparator.replace("server_date", "server_date") +
                ") dates ORDER BY server_date DESC LIMIT ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int index = 1;
            ps.setString(index++, spaceId);
            if (!comparator.isEmpty()) ps.setString(index++, beforeDate);
            ps.setString(index++, spaceId);
            if (!comparator.isEmpty()) ps.setString(index++, beforeDate);
            ps.setInt(index, limit);
            List<String> dates = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) dates.add(rs.getString(1));
            }
            return dates;
        }
    }

    private static void ensureDailyQuiz(Connection conn, String spaceId, String date, long now) throws SQLException {
        if (findQuiz(conn, spaceId, date) != null) return;
        Long questionId = pickQuestionId(conn, spaceId);
        if (questionId == null) return;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO duo_daily_quizzes(id, space_id, server_date, question_id, created_at, completed_at) " +
                        "VALUES(?,?,?,?,?,NULL)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, spaceId);
            ps.setString(3, date);
            ps.setLong(4, questionId);
            ps.setLong(5, now);
            ps.executeUpdate();
        }
    }

    private static Long pickQuestionId(Connection conn, String spaceId) throws SQLException {
        Long id = queryQuestionId(conn,
                "SELECT q.id FROM tacit_quiz_questions q WHERE q.active=1 AND NOT EXISTS (" +
                        "SELECT 1 FROM duo_daily_quizzes d WHERE d.space_id=? AND d.question_id=q.id) " +
                        "ORDER BY RANDOM() LIMIT 1", spaceId);
        if (id != null) return id;
        id = queryQuestionId(conn,
                "SELECT q.id FROM tacit_quiz_questions q WHERE q.active=1 AND q.id NOT IN (" +
                        "SELECT d.question_id FROM duo_daily_quizzes d WHERE d.space_id=? " +
                        "ORDER BY d.server_date DESC LIMIT 10) ORDER BY RANDOM() LIMIT 1", spaceId);
        if (id != null) return id;
        return queryQuestionId(conn, "SELECT id FROM tacit_quiz_questions WHERE active=1 ORDER BY RANDOM() LIMIT 1");
    }

    private static Long queryQuestionId(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    private static QuizRow findQuiz(Connection conn, String spaceId, String date) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, question_id, completed_at FROM duo_daily_quizzes WHERE space_id=? AND server_date=?")) {
            ps.setString(1, spaceId);
            ps.setString(2, date);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                long completed = rs.getLong("completed_at");
                return new QuizRow(rs.getString("id"), rs.getLong("question_id"), rs.wasNull() ? null : completed);
            }
        }
    }

    private static DuoQuestionDTO question(Connection conn, long questionId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, question, options_json FROM tacit_quiz_questions WHERE id=? AND active=1")) {
            ps.setLong(1, questionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new DuoQuestionDTO(rs.getLong("id"), rs.getString("question"),
                        JSONUtil.toList(rs.getString("options_json"), String.class));
            }
        }
    }

    private static List<String> questionOptions(Connection conn, long questionId) throws SQLException {
        DuoQuestionDTO question = question(conn, questionId);
        return question == null || question.getOptions() == null ? Collections.emptyList() : question.getOptions();
    }

    private static Integer answerChoice(Connection conn, String quizId, long accountId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT choice_index FROM duo_daily_quiz_answers WHERE quiz_id=? AND account_id=?")) {
            ps.setString(1, quizId);
            ps.setLong(2, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    private static boolean answerExists(Connection conn, String quizId, long accountId) throws SQLException {
        return answerChoice(conn, quizId, accountId) != null;
    }

    private static boolean bothAnswers(Connection conn, String quizId, long a, long b) throws SQLException {
        return answerExists(conn, quizId, a) && answerExists(conn, quizId, b);
    }

    private static ProgressRow findProgress(Connection conn, String spaceId, String date) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT interaction_awarded, play_awarded FROM duo_daily_progress WHERE space_id=? AND server_date=?")) {
            ps.setString(1, spaceId);
            ps.setString(2, date);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new ProgressRow(rs.getInt(1) != 0, rs.getInt(2) != 0) : null;
            }
        }
    }

    private static void ensureProgress(Connection conn, String spaceId, String date, long now) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO duo_daily_progress(space_id, server_date, interaction_awarded, play_awarded) VALUES(?,?,0,0)")) {
            ps.setString(1, spaceId);
            ps.setString(2, date);
            ps.executeUpdate();
        }
    }

    private static boolean markProgressAwarded(Connection conn, String spaceId, String date, String column)
            throws SQLException {
        if (!"interaction_awarded".equals(column) && !"play_awarded".equals(column)) {
            throw new IllegalArgumentException("双人小屋进度字段不合法");
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE duo_daily_progress SET " + column + "=1 WHERE space_id=? AND server_date=? AND " + column + "=0")) {
            ps.setString(1, spaceId);
            ps.setString(2, date);
            return ps.executeUpdate() == 1;
        }
    }

    private static int countInteractions(Connection conn, String spaceId, String date) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(1) FROM duo_interactions WHERE space_id=? AND server_date=?")) {
            ps.setString(1, spaceId);
            ps.setString(2, date);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static void incrementWarmth(Connection conn, String spaceId, long now) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE duo_spaces SET warmth=warmth+1, updated_at=? WHERE id=? AND status='ACTIVE'")) {
            ps.setLong(1, now);
            ps.setString(2, spaceId);
            ps.executeUpdate();
        }
    }

    private static boolean interactionExists(Connection conn, String spaceId, String date, long accountId)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM duo_interactions WHERE space_id=? AND server_date=? AND actor_account_id=?")) {
            ps.setString(1, spaceId);
            ps.setString(2, date);
            ps.setLong(3, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean attachmentReady(Connection conn, String attachmentId, String spaceId, long accountId)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM duo_attachments WHERE id=? AND space_id=? AND uploader_account_id=? " +
                        "AND interaction_id IS NULL")) {
            ps.setString(1, attachmentId);
            ps.setString(2, spaceId);
            ps.setLong(3, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean interactionAvailable(DuoInteractionType gesture, DuoDogSnapshotDTO myDog,
                                                 DuoDogSnapshotDTO partnerDog) {
        switch (gesture) {
            case WAVE_HELLO:
                return true;
            case PAT_DOG:
            case GIVE_TOY:
            case LEAVE_SNACK:
                return partnerDog != null;
            case WALK_TOGETHER:
                return myDog != null && partnerDog != null;
            default:
                return false;
        }
    }

    private static void validatePayload(EncryptedPayloadDTO payload) {
        if (payload == null) return;
        if (!"v1".equals(payload.getVersion()) || payload.getIv() == null || payload.getCiphertext() == null) {
            throw new IllegalArgumentException(ERROR_INTERACTION_UNAVAILABLE);
        }
        try {
            byte[] iv = decodeBase64Url(payload.getIv());
            byte[] ciphertext = decodeBase64Url(payload.getCiphertext());
            if (iv.length != 12 || ciphertext.length > 16 * 1024) {
                throw new IllegalArgumentException(ERROR_INTERACTION_UNAVAILABLE);
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(ERROR_INTERACTION_UNAVAILABLE);
        }
    }

    private static byte[] decodeBase64Url(String value) {
        return java.util.Base64.getUrlDecoder().decode(value);
    }

    private static boolean accountExists(Connection conn, long accountId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM accounts WHERE account_id=? AND status='ACTIVE'")) {
            ps.setLong(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean areFriends(Connection conn, long a, long b) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM friends WHERE owner_account_id=? AND friend_account_id=?")) {
            ps.setLong(1, a);
            ps.setLong(2, b);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static String resolveOccupiedError(Connection conn, long a, long b) throws SQLException {
        SpaceRow first = findSpaceByAccount(conn, a);
        SpaceRow second = findSpaceByAccount(conn, b);
        if ((first != null && "PENDING".equals(first.status)) || (second != null && "PENDING".equals(second.status))) {
            return ERROR_PENDING_INVITE;
        }
        return ERROR_ONE_SPACE;
    }

    private static SpaceRow findSpaceByAccount(Connection conn, long accountId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT s.id, s.account_low_id, s.account_high_id, s.invited_by_account_id, s.status, s.warmth, " +
                        "s.created_at, s.activated_at, s.expires_at, s.updated_at FROM duo_spaces s " +
                        "JOIN duo_members m ON m.space_id=s.id WHERE m.account_id=? LIMIT 1")) {
            ps.setLong(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                long activated = rs.getLong("activated_at");
                boolean activatedNull = rs.wasNull();
                long expires = rs.getLong("expires_at");
                boolean expiresNull = rs.wasNull();
                return new SpaceRow(rs.getString("id"), rs.getLong("account_low_id"), rs.getLong("account_high_id"),
                        rs.getLong("invited_by_account_id"), rs.getString("status"), rs.getInt("warmth"),
                        rs.getLong("created_at"), activatedNull ? null : activated,
                        expiresNull ? null : expires, rs.getLong("updated_at"));
            }
        }
    }

    private static SpaceRow requireActiveSpace(Connection conn, long accountId) throws SQLException {
        SpaceRow space = findSpaceByAccount(conn, accountId);
        if (space == null || !"ACTIVE".equals(space.status)) throw new IllegalArgumentException(ERROR_NO_SPACE);
        return space;
    }

    private static String findSpaceId(long accountId) {
        try (SqlSession session = DbInitializer.factory().openSession(true);
             PreparedStatement ps = session.getConnection().prepareStatement(
                     "SELECT s.id FROM duo_spaces s JOIN duo_members m ON m.space_id=s.id WHERE m.account_id=?")) {
            ps.setLong(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : "";
            }
        } catch (Exception e) {
            return "";
        }
    }

    private static String selectedDogId(Connection conn, String spaceId, long accountId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT selected_dog_id FROM duo_members WHERE space_id=? AND account_id=?")) {
            ps.setString(1, spaceId);
            ps.setLong(2, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private static boolean dogOwnedBy(Connection conn, String dogId, long accountId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM dogs WHERE id=? AND owner_id=?")) {
            ps.setString(1, dogId);
            ps.setLong(2, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static String defaultDogId(Connection conn, long accountId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT companion_dog_id FROM pet_assets WHERE account_id=?")) {
            ps.setLong(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String companion = rs.getString(1);
                    if (companion != null && dogOwnedBy(conn, companion, accountId)) return companion;
                }
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM dogs WHERE owner_id=? ORDER BY created_at ASC LIMIT 1")) {
            ps.setLong(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private static DuoDogSnapshotDTO findDogSnapshot(Connection conn, long ownerId, String dogId) throws SQLException {
        if (dogId == null) return null;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, name, breed, stage, bond FROM dogs WHERE id=? AND owner_id=?")) {
            ps.setString(1, dogId);
            ps.setLong(2, ownerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new DuoDogSnapshotDTO(ownerId, rs.getString("id"), rs.getString("name"),
                        rs.getString("breed"), rs.getString("stage"), rs.getInt("bond")) : null;
            }
        }
    }

    private static long otherMemberId(Connection conn, String spaceId, long accountId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT account_id FROM duo_members WHERE space_id=? AND account_id<>?")) {
            ps.setString(1, spaceId);
            ps.setLong(2, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    private static AccountView accountView(Connection conn, long accountId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT account_id, account, nickname, avatar_version FROM accounts WHERE account_id=?")) {
            ps.setLong(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new AccountView(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4)) : null;
            }
        }
    }

    private static AccountView accountView(long accountId) {
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            return accountView(session.getConnection(), accountId);
        } catch (Exception e) {
            return null;
        }
    }

    private static void pushProfiles(List<Long> accountIds) {
        Set<Long> unique = new HashSet<>(accountIds == null ? Collections.emptyList() : accountIds);
        for (Long accountId : unique) {
            if (accountId == null || accountId <= 0L) continue;
            try {
                DuoSpaceResponseDTO response = new DuoSpaceResponseDTO(
                        DuoSpaceEvent.PROFILE, null, profile(accountId), null);
                for (User user : UserCache.getByAccount(accountId)) {
                    user.send(ResponseBuilder.build(null, response, MessageType.DUO_SPACE));
                }
            } catch (Exception e) {
                log.warn("推送双人小屋资料失败 accountId={}", accountId, e);
            }
        }
    }

    private static void cleanupExpiredPending(Connection conn, long now) throws SQLException {
        List<String> expired = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM duo_spaces WHERE status='PENDING' AND expires_at IS NOT NULL AND expires_at<=?")) {
            ps.setLong(1, now);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) expired.add(rs.getString(1));
            }
        }
        for (String id : expired) deleteSpaceRows(conn, id);
    }

    private static List<String> deleteSpaceRows(Connection conn, String spaceId) throws SQLException {
        List<String> files = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT storage_name FROM duo_attachments WHERE space_id=?")) {
            ps.setString(1, spaceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) files.add(rs.getString(1));
            }
        }
        String[] deletes = {
                "DELETE FROM duo_daily_quiz_answers WHERE quiz_id IN (SELECT id FROM duo_daily_quizzes WHERE space_id=?)",
                "DELETE FROM duo_daily_quizzes WHERE space_id=?",
                "DELETE FROM duo_interactions WHERE space_id=?",
                "DELETE FROM duo_daily_progress WHERE space_id=?",
                "DELETE FROM duo_attachments WHERE space_id=?",
                "DELETE FROM duo_members WHERE space_id=?",
                "DELETE FROM duo_spaces WHERE id=?"
        };
        for (String sql : deletes) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, spaceId);
                ps.executeUpdate();
            }
        }
        return files;
    }

    private static void insertMember(PreparedStatement ps, String spaceId, long accountId,
                                     String dogId, Long joinedAt) throws SQLException {
        ps.setString(1, spaceId);
        ps.setLong(2, accountId);
        ps.setString(3, dogId);
        if (joinedAt == null) ps.setObject(4, null); else ps.setLong(4, joinedAt);
        ps.executeUpdate();
    }

    private static void updateMemberAfterActivation(PreparedStatement ps, String spaceId, long accountId,
                                                    String dogId, long joinedAt) throws SQLException {
        ps.setLong(1, joinedAt);
        ps.setString(2, dogId);
        ps.setString(3, spaceId);
        ps.setLong(4, accountId);
        ps.executeUpdate();
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isConstraint(SQLException e) {
        String message = e.getMessage();
        return message != null && message.toUpperCase().contains("CONSTRAINT");
    }

    private static final class SpaceRow {
        private final String id;
        private final long accountLowId;
        private final long accountHighId;
        private final long invitedByAccountId;
        private final String status;
        private final int warmth;
        private final long createdAt;
        private final Long activatedAt;
        private final Long expiresAt;
        private final long updatedAt;

        private SpaceRow(String id, long accountLowId, long accountHighId, long invitedByAccountId, String status,
                         int warmth, long createdAt, Long activatedAt, Long expiresAt, long updatedAt) {
            this.id = id;
            this.accountLowId = accountLowId;
            this.accountHighId = accountHighId;
            this.invitedByAccountId = invitedByAccountId;
            this.status = status;
            this.warmth = warmth;
            this.createdAt = createdAt;
            this.activatedAt = activatedAt;
            this.expiresAt = expiresAt;
            this.updatedAt = updatedAt;
        }

        private long other(long accountId) {
            return accountId == accountLowId ? accountHighId : accountLowId;
        }
    }

    private static final class QuizRow {
        private final String id;
        private final long questionId;
        private final Long completedAt;

        private QuizRow(String id, long questionId, Long completedAt) {
            this.id = id;
            this.questionId = questionId;
            this.completedAt = completedAt;
        }
    }

    private static final class ProgressRow {
        private final boolean interactionAwarded;
        private final boolean playAwarded;

        private ProgressRow(boolean interactionAwarded, boolean playAwarded) {
            this.interactionAwarded = interactionAwarded;
            this.playAwarded = playAwarded;
        }
    }

    private static final class AccountView {
        private final long accountId;
        private final String account;
        private final String nickname;
        private final long avatarVersion;

        private AccountView(long accountId, String account, String nickname, long avatarVersion) {
            this.accountId = accountId;
            this.account = account;
            this.nickname = nickname;
            this.avatarVersion = avatarVersion;
        }

        private DuoPartnerDTO toDTO() {
            return new DuoPartnerDTO(accountId, account, nickname, avatarVersion);
        }
    }
}
