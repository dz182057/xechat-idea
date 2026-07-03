package cn.xeblog.server.pet;

import cn.hutool.core.util.StrUtil;
import cn.xeblog.commons.entity.pet.AdminListPetDailySayingAssignmentsDTO;
import cn.xeblog.commons.entity.pet.AdminListPetDailySayingsDTO;
import cn.xeblog.commons.entity.pet.AdminPetDailySayingAssignmentDTO;
import cn.xeblog.commons.entity.pet.AdminPetDailySayingAssignmentListDTO;
import cn.xeblog.commons.entity.pet.AdminPetDailySayingContentListDTO;
import cn.xeblog.commons.entity.pet.AdminReassignPetDailySayingDTO;
import cn.xeblog.commons.entity.pet.AdminSavePetDailySayingDTO;
import cn.xeblog.commons.entity.pet.AdminDeletePetDailySayingDTO;
import cn.xeblog.commons.entity.pet.PetDailySayingContentDTO;
import cn.xeblog.commons.entity.pet.PetDailySayingDTO;
import cn.xeblog.commons.entity.pet.PetDailySayingReadDTO;
import cn.xeblog.commons.entity.pet.PetProfileDTO;
import cn.xeblog.commons.entity.pet.PetRecentSayingDTO;
import cn.xeblog.server.account.DbInitializer;
import org.apache.ibatis.session.SqlSession;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 狗狗首页每日问候服务。
 */
public final class PetDailySayingService {

    private static final String STATE_NONE = "NONE";
    private static final String STATE_UNREAD = "UNREAD";
    private static final String STATE_READ_TODAY = "READ_TODAY";
    private static final String STATUS_UNREAD = "UNREAD";
    private static final String STATUS_READ = "READ";
    private static final String REVIEW_PUBLISHABLE = "可发布";
    private static final String REVIEW_PENDING = "待编辑终审";
    private static final String REVIEW_REMOVED = "下架";
    private static final String DEFAULT_CONTENT_VERSION = "pet-daily-saying-v1-2026-06-24";
    private static final int RECENT_EXCLUDE_LIMIT = 180;
    private static final String COUNTER_DATE_LIFETIME = "lifetime";
    private static final String DAILY_SAYING_VIEW_REWARD_COUNTER_PREFIX = "daily_saying_view_bones:";
    private static final int DAILY_SAYING_VIEW_REWARD_BONES = 50;
    private static final List<Integer> RELAXED_EXCLUDE_LIMITS = Collections.unmodifiableList(
            Arrays.asList(180, 120, 60, 30, 0));
    private static final int MAX_ADMIN_PAGE_SIZE = 100;
    private static final int DEFAULT_ADMIN_PAGE_SIZE = 30;

    private static final List<String> CATEGORIES = Collections.unmodifiableList(Arrays.asList(
            "古诗词及狗狗解读",
            "温柔短句",
            "狗狗笑话",
            "轻哲思",
            "废话文学/反鸡汤"
    ));
    private static final Map<String, Integer> CATEGORY_WEIGHTS = createCategoryWeights();

    private PetDailySayingService() {
    }

    public static PetProfileDTO dailySaying(long accountId) {
        synchronized (PetProfileService.accountLock(accountId)) {
            String today = LocalDate.now().toString();
            long now = System.currentTimeMillis();
            try (SqlSession session = DbInitializer.factory().openSession(false)) {
                ensureDailySayingAssignment(session, accountId, today, now);
                session.commit();
            }
            return PetProfileService.profileLocked(accountId);
        }
    }

    public static PetProfileDTO viewDailySaying(long accountId, PetDailySayingReadDTO request) {
        String assignmentId = request == null ? null : StrUtil.trim(request.getAssignmentId());
        if (StrUtil.isBlank(assignmentId)) {
            throw new IllegalArgumentException("缺少要查看的狗狗问候");
        }

        synchronized (PetProfileService.accountLock(accountId)) {
            long now = System.currentTimeMillis();
            try (SqlSession session = DbInitializer.factory().openSession(false)) {
                PetDailySayingAssignmentRecord assignment =
                        session.getMapper(PetDailySayingAssignmentMapper.class).findById(accountId, assignmentId);
                if (assignment == null) {
                    throw new IllegalArgumentException("这条狗狗问候不存在");
                }
                if (STATUS_UNREAD.equals(assignment.getStatus())) {
                    PetDailyCounterMapper counterMapper = session.getMapper(PetDailyCounterMapper.class);
                    String counter = DAILY_SAYING_VIEW_REWARD_COUNTER_PREFIX + assignmentId;
                    if (counterMapper.incrementIfUnderLimit(accountId, COUNTER_DATE_LIFETIME, counter, 1, now) > 0
                            && session.getMapper(PetAssetsMapper.class)
                            .addBones(accountId, DAILY_SAYING_VIEW_REWARD_BONES, now) <= 0) {
                        throw new IllegalArgumentException("狗狗资料更新失败");
                    }
                }
                session.commit();
            }
            return PetProfileService.profileLocked(accountId);
        }
    }

    public static PetProfileDTO readDailySaying(long accountId, PetDailySayingReadDTO request) {
        String assignmentId = request == null ? null : StrUtil.trim(request.getAssignmentId());
        if (StrUtil.isBlank(assignmentId)) {
            throw new IllegalArgumentException("缺少要阅读的狗狗问候");
        }

        synchronized (PetProfileService.accountLock(accountId)) {
            String today = LocalDate.now().toString();
            long now = System.currentTimeMillis();
            try (SqlSession session = DbInitializer.factory().openSession(false)) {
                PetDailySayingAssignmentMapper assignmentMapper =
                        session.getMapper(PetDailySayingAssignmentMapper.class);
                PetDailySayingAssignmentRecord assignment = assignmentMapper.findById(accountId, assignmentId);
                if (assignment == null) {
                    throw new IllegalArgumentException("这条狗狗问候不存在");
                }
                if (STATUS_READ.equals(assignment.getStatus())) {
                    session.commit();
                    return PetProfileService.profileLocked(accountId);
                }

                PetDogRecord dog = session.getMapper(PetDogMapper.class)
                        .findByIdAndOwner(assignment.getDogId(), accountId);
                int delta = dog == null ? 0 : PetProfileService.applyDailyGreetingBond(session, accountId, today, dog, now);
                boolean rewardApplied = delta > 0;
                if (assignmentMapper.markRead(accountId, assignmentId, now, today, rewardApplied, delta) <= 0) {
                    throw new IllegalArgumentException("这条狗狗问候已经读过了");
                }
                session.commit();
            }
            return PetProfileService.profileLocked(accountId);
        }
    }

    public static AdminPetDailySayingContentListDTO adminList(AdminListPetDailySayingsDTO request) {
        String keyword = normalizeFilter(request == null ? null : request.getKeyword());
        String category = normalizeFilter(request == null ? null : request.getCategory());
        String reviewStatus = normalizeFilter(request == null ? null : request.getReviewStatus());
        Boolean active = request == null ? null : request.getActive();
        int pageSize = normalizePageSize(request == null ? null : request.getPageSize());
        int page = normalizePage(request == null ? null : request.getPage());
        int offset = (page - 1) * pageSize;
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            PetDailySayingContentMapper mapper = session.getMapper(PetDailySayingContentMapper.class);
            int total = mapper.countByFilters(keyword, category, reviewStatus, active);
            List<PetDailySayingContentDTO> items = new ArrayList<>();
            for (PetDailySayingContentRecord record : mapper.listByFilters(keyword, category, reviewStatus,
                    active, offset, pageSize)) {
                items.add(toContentDTO(record));
            }
            return new AdminPetDailySayingContentListDTO(items, total, page, pageSize, latestVersion(mapper));
        }
    }

    public static AdminPetDailySayingContentListDTO adminSave(AdminSavePetDailySayingDTO request) {
        PetDailySayingContentDTO content = request == null ? null : request.getContent();
        PetDailySayingContentRecord record = toValidatedRecord(content);
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            session.getMapper(PetDailySayingContentMapper.class).upsert(record);
        }
        return adminList(new AdminListPetDailySayingsDTO(record.getContentId(), null, null, null, 1, 10));
    }

    public static AdminPetDailySayingContentListDTO adminDelete(AdminDeletePetDailySayingDTO request) {
        String contentId = request == null ? null : StrUtil.trim(request.getContentId());
        if (StrUtil.isBlank(contentId)) {
            throw new IllegalArgumentException("缺少要下架的内容 ID");
        }
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            if (session.getMapper(PetDailySayingContentMapper.class)
                    .softDelete(contentId, System.currentTimeMillis()) <= 0) {
                throw new IllegalArgumentException("内容不存在，无法下架");
            }
        }
        return adminList(new AdminListPetDailySayingsDTO(contentId, null, null, null, 1, 10));
    }

    public static AdminPetDailySayingAssignmentListDTO adminListAssignments(
            AdminListPetDailySayingAssignmentsDTO request) {
        String assignedDate = normalizeDate(request == null ? null : request.getAssignedServerDate());
        String keyword = normalizeFilter(request == null ? null : request.getKeyword());
        String status = normalizeAssignmentStatus(request == null ? null : request.getStatus());
        int pageSize = normalizePageSize(request == null ? null : request.getPageSize());
        int page = normalizePage(request == null ? null : request.getPage());
        int offset = (page - 1) * pageSize;
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            PetDailySayingAssignmentMapper mapper = session.getMapper(PetDailySayingAssignmentMapper.class);
            int total = mapper.countAdminByDate(assignedDate, keyword, status);
            return new AdminPetDailySayingAssignmentListDTO(
                    mapper.listAdminByDate(assignedDate, keyword, status, offset, pageSize),
                    total,
                    page,
                    pageSize,
                    assignedDate);
        }
    }

    public static AdminPetDailySayingAssignmentListDTO adminReassign(AdminReassignPetDailySayingDTO request) {
        String assignmentId = request == null ? null : StrUtil.trim(request.getAssignmentId());
        if (StrUtil.isBlank(assignmentId)) {
            throw new IllegalArgumentException("缺少要重新分配的狗狗问候");
        }

        PetDailySayingAssignmentRecord target;
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            target = session.getMapper(PetDailySayingAssignmentMapper.class).findByAssignmentId(assignmentId);
        }
        if (target == null) {
            throw new IllegalArgumentException("这条狗狗问候不存在");
        }

        synchronized (PetProfileService.accountLock(target.getAccountId())) {
            return adminReassignLocked(assignmentId);
        }
    }

    private static AdminPetDailySayingAssignmentListDTO adminReassignLocked(String assignmentId) {
        long now = System.currentTimeMillis();
        String today = LocalDate.now().toString();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            PetDailySayingAssignmentMapper assignmentMapper =
                    session.getMapper(PetDailySayingAssignmentMapper.class);
            PetDailySayingAssignmentRecord assignment = assignmentMapper.findByAssignmentId(assignmentId);
            if (assignment == null) {
                throw new IllegalArgumentException("这条狗狗问候不存在");
            }
            if (!today.equals(assignment.getAssignedServerDate())) {
                throw new IllegalArgumentException("只能重新分配今天的狗狗问候");
            }
            PetDogRecord dog = resolveActiveDog(session, assignment.getAccountId());
            if (dog == null) {
                throw new IllegalArgumentException("目标用户当前没有可用狗狗");
            }
            PetDailySayingContentRecord content =
                    pickReplacementContent(session, assignment.getAccountId(), assignment.getContentId());
            if (content == null) {
                throw new IllegalArgumentException("暂无可替换的可发布狗狗问候");
            }
            if (assignmentMapper.reassign(
                    assignmentId,
                    assignment.getAccountId(),
                    dog.getId(),
                    dog.getName(),
                    dog.getBreed(),
                    content.getContentId(),
                    now,
                    content.getContentVersion()) <= 0) {
                throw new IllegalArgumentException("重新分配狗狗问候失败");
            }
            AdminPetDailySayingAssignmentDTO updated = assignmentMapper.findAdminByAssignmentId(assignmentId);
            session.commit();
            return new AdminPetDailySayingAssignmentListDTO(
                    Collections.singletonList(updated), 1, 1, 1, today);
        }
    }

    static PetDailySayingDTO currentState(SqlSession session, long accountId, String today) {
        PetDailySayingAssignmentMapper assignmentMapper = session.getMapper(PetDailySayingAssignmentMapper.class);
        PetDailySayingContentMapper contentMapper = session.getMapper(PetDailySayingContentMapper.class);
        PetDailySayingAssignmentRecord unread = assignmentMapper.findUnread(accountId);
        if (unread != null) {
            return toDailySayingDTO(STATE_UNREAD, unread, contentMapper.findById(unread.getContentId()));
        }
        PetDailySayingAssignmentRecord readToday = assignmentMapper.findReadOnDate(accountId, today);
        if (readToday != null) {
            return toDailySayingDTO(STATE_READ_TODAY, readToday, null);
        }
        return PetDailySayingDTO.none();
    }

    static List<PetRecentSayingDTO> recentSayings(SqlSession session, long accountId) {
        PetDailySayingAssignmentMapper assignmentMapper = session.getMapper(PetDailySayingAssignmentMapper.class);
        PetDailySayingContentMapper contentMapper = session.getMapper(PetDailySayingContentMapper.class);
        List<PetRecentSayingDTO> result = new ArrayList<>();
        for (PetDailySayingAssignmentRecord assignment : assignmentMapper.listRecentRead(accountId, 3)) {
            PetDailySayingContentRecord content = contentMapper.findById(assignment.getContentId());
            PetDailySayingContentDTO contentDTO = contentOrFallback(content);
            result.add(new PetRecentSayingDTO(
                    assignment.getAssignmentId(),
                    assignment.getDogNameSnapshot(),
                    contentDTO.getCategory(),
                    replaceDogName(contentDTO.getPrimaryText(), assignment.getDogNameSnapshot()),
                    assignment.getReadAt()));
        }
        return result;
    }

    private static void ensureDailySayingAssignment(SqlSession session, long accountId, String today, long now) {
        PetDailySayingAssignmentMapper assignmentMapper = session.getMapper(PetDailySayingAssignmentMapper.class);
        if (assignmentMapper.findUnread(accountId) != null
                || assignmentMapper.findReadOnDate(accountId, today) != null) {
            return;
        }

        PetDogRecord dog = resolveActiveDog(session, accountId);
        if (dog == null) {
            return;
        }

        PetDailySayingContentRecord content = pickContent(session, accountId, today);
        if (content == null) {
            return;
        }

        assignmentMapper.insert(PetDailySayingAssignmentRecord.builder()
                .assignmentId(UUID.randomUUID().toString())
                .accountId(accountId)
                .dogId(dog.getId())
                .dogNameSnapshot(dog.getName())
                .dogAvatarSnapshot(dog.getBreed())
                .contentId(content.getContentId())
                .assignedServerDate(today)
                .status(STATUS_UNREAD)
                .assignedAt(now)
                .contentVersion(content.getContentVersion())
                .build());
    }

    private static PetDogRecord resolveActiveDog(SqlSession session, long accountId) {
        List<PetDogRecord> dogs = session.getMapper(PetDogMapper.class).listByOwner(accountId);
        if (dogs.isEmpty()) {
            return null;
        }
        PetAssetsRecord assets = session.getMapper(PetAssetsMapper.class).findByAccountId(accountId);
        String savedDogIds = assets == null ? null : assets.getCompanionDogId();
        if (StrUtil.isNotBlank(savedDogIds)) {
            for (String dogId : savedDogIds.split(",")) {
                String normalizedDogId = StrUtil.trim(dogId);
                for (PetDogRecord dog : dogs) {
                    if (dog.getId().equals(normalizedDogId)) {
                        return dog;
                    }
                }
            }
        }
        return dogs.get(0);
    }

    private static PetDailySayingContentRecord pickContent(SqlSession session, long accountId, String today) {
        PetDailySayingContentMapper contentMapper = session.getMapper(PetDailySayingContentMapper.class);
        PetDailySayingAssignmentMapper assignmentMapper = session.getMapper(PetDailySayingAssignmentMapper.class);
        List<String> recentIds = assignmentMapper.listRecentAssignedContentIds(accountId, RECENT_EXCLUDE_LIMIT);
        Set<String> assignedTexts = new HashSet<>(assignmentMapper.listAssignedPrimaryTextsByAccount(accountId));
        List<String> recentCategories = assignmentMapper.listRecentReadCategories(accountId, 2);
        String bannedCategory = recentCategories.size() >= 2 && recentCategories.get(0).equals(recentCategories.get(1))
                ? recentCategories.get(0)
                : null;
        String contentVersion = latestVersion(contentMapper);
        for (Integer excludeLimit : RELAXED_EXCLUDE_LIMITS) {
            List<String> excluded = excludeLimit <= 0 || recentIds.isEmpty()
                    ? Collections.emptyList()
                    : new ArrayList<>(recentIds.subList(0, Math.min(excludeLimit, recentIds.size())));
            Map<String, List<PetDailySayingContentRecord>> candidates = new LinkedHashMap<>();
            for (String category : CATEGORIES) {
                if (category.equals(bannedCategory)) {
                    continue;
                }
                List<PetDailySayingContentRecord> rows = contentMapper.listPublishableByCategory(category, excluded, 10000);
                rows.removeIf(row -> assignedTexts.contains(row.getPrimaryText()));
                if (!rows.isEmpty()) {
                    candidates.put(category, rows);
                }
            }
            if (candidates.isEmpty()) {
                continue;
            }
            String category = pickCategory(candidates, seed(accountId, today, contentVersion, "category-" + excludeLimit));
            return pickContentInCategory(candidates.get(category),
                    seed(accountId, today, contentVersion, "content-" + category + "-" + excludeLimit));
        }
        return null;
    }

    private static PetDailySayingContentRecord pickReplacementContent(SqlSession session,
                                                                      long accountId,
                                                                      String currentContentId) {
        Map<String, List<PetDailySayingContentRecord>> candidates =
                buildReplacementCandidates(session, accountId, currentContentId, true);
        if (candidates.isEmpty()) {
            candidates = buildReplacementCandidates(session, accountId, currentContentId, false);
        }
        if (candidates.isEmpty()) {
            return null;
        }
        String category = pickRandomCategory(candidates);
        return pickRandomContentInCategory(candidates.get(category));
    }

    private static Map<String, List<PetDailySayingContentRecord>> buildReplacementCandidates(SqlSession session,
                                                                                             long accountId,
                                                                                             String currentContentId,
                                                                                             boolean excludeAssignedTexts) {
        PetDailySayingContentMapper contentMapper = session.getMapper(PetDailySayingContentMapper.class);
        PetDailySayingAssignmentMapper assignmentMapper = session.getMapper(PetDailySayingAssignmentMapper.class);
        Set<String> assignedTexts = excludeAssignedTexts
                ? new HashSet<>(assignmentMapper.listAssignedPrimaryTextsByAccount(accountId))
                : Collections.emptySet();
        List<String> excludedIds = excludeAssignedTexts
                ? new ArrayList<>(assignmentMapper.listRecentAssignedContentIds(accountId, RECENT_EXCLUDE_LIMIT))
                : new ArrayList<>();
        if (StrUtil.isNotBlank(currentContentId) && !excludedIds.contains(currentContentId)) {
            excludedIds.add(currentContentId);
        }

        Map<String, List<PetDailySayingContentRecord>> candidates = new LinkedHashMap<>();
        for (String category : CATEGORIES) {
            List<PetDailySayingContentRecord> rows =
                    contentMapper.listPublishableByCategory(category, excludedIds, 10000);
            if (excludeAssignedTexts) {
                rows.removeIf(row -> assignedTexts.contains(row.getPrimaryText()));
            }
            if (!rows.isEmpty()) {
                candidates.put(category, rows);
            }
        }
        return candidates;
    }

    private static String pickRandomCategory(Map<String, List<PetDailySayingContentRecord>> candidates) {
        int total = 0;
        for (String category : candidates.keySet()) {
            total += Math.max(1, CATEGORY_WEIGHTS.getOrDefault(category, 1));
        }
        int point = ThreadLocalRandom.current().nextInt(Math.max(1, total));
        int cursor = 0;
        for (String category : candidates.keySet()) {
            cursor += Math.max(1, CATEGORY_WEIGHTS.getOrDefault(category, 1));
            if (point < cursor) {
                return category;
            }
        }
        return candidates.keySet().iterator().next();
    }

    private static PetDailySayingContentRecord pickRandomContentInCategory(List<PetDailySayingContentRecord> rows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        double total = 0D;
        for (PetDailySayingContentRecord row : rows) {
            total += Math.max(0.01D, row.getRecommendedWeight());
        }
        double point = ThreadLocalRandom.current().nextDouble(total);
        double cursor = 0D;
        for (PetDailySayingContentRecord row : rows) {
            cursor += Math.max(0.01D, row.getRecommendedWeight());
            if (point <= cursor) {
                return row;
            }
        }
        return rows.get(rows.size() - 1);
    }

    private static String pickCategory(Map<String, List<PetDailySayingContentRecord>> candidates, BigInteger seed) {
        int total = 0;
        for (String category : candidates.keySet()) {
            total += CATEGORY_WEIGHTS.getOrDefault(category, 1);
        }
        if (total <= 0) {
            return candidates.keySet().iterator().next();
        }
        int point = seed.mod(BigInteger.valueOf(total)).intValue();
        int cursor = 0;
        for (String category : candidates.keySet()) {
            cursor += CATEGORY_WEIGHTS.getOrDefault(category, 1);
            if (point < cursor) {
                return category;
            }
        }
        return candidates.keySet().iterator().next();
    }

    private static PetDailySayingContentRecord pickContentInCategory(List<PetDailySayingContentRecord> rows,
                                                                     BigInteger seed) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        double total = 0D;
        for (PetDailySayingContentRecord row : rows) {
            total += Math.max(0.01D, row.getRecommendedWeight());
        }
        double point = seed.mod(BigInteger.valueOf(1_000_000L)).doubleValue() / 1_000_000D * total;
        double cursor = 0D;
        for (PetDailySayingContentRecord row : rows) {
            cursor += Math.max(0.01D, row.getRecommendedWeight());
            if (point <= cursor) {
                return row;
            }
        }
        return rows.get(rows.size() - 1);
    }

    private static PetDailySayingDTO toDailySayingDTO(String state,
                                                     PetDailySayingAssignmentRecord assignment,
                                                     PetDailySayingContentRecord content) {
        PetDailySayingContentDTO contentDTO = STATE_UNREAD.equals(state) ? contentOrFallback(content) : null;
        if (contentDTO != null) {
            contentDTO.setPrimaryText(replaceDogName(contentDTO.getPrimaryText(), assignment.getDogNameSnapshot()));
            contentDTO.setSecondaryText(replaceDogName(contentDTO.getSecondaryText(), assignment.getDogNameSnapshot()));
        }
        return new PetDailySayingDTO(
                state,
                assignment.getAssignmentId(),
                assignment.getAssignedServerDate(),
                new PetDailySayingDTO.PetSnapshot(
                        assignment.getDogId(),
                        assignment.getDogNameSnapshot(),
                        assignment.getDogAvatarSnapshot()),
                contentDTO,
                assignment.getReadAt(),
                assignment.getGreetingRewardApplied(),
                assignment.getGreetingIntimacyDelta());
    }

    private static PetDailySayingContentDTO contentOrFallback(PetDailySayingContentRecord content) {
        if (content != null) {
            return toContentDTO(content);
        }
        PetDailySayingContentDTO fallback = new PetDailySayingContentDTO();
        fallback.setContentId("FALLBACK");
        fallback.setCategory("温柔短句");
        fallback.setSubtype("系统兜底");
        fallback.setTitle("狗狗的话还在路上");
        fallback.setPrimaryText("{dog_name}今天先把爪子搭在你手边，等一会儿再慢慢说。");
        fallback.setReviewStatus(REVIEW_PUBLISHABLE);
        fallback.setActive(false);
        fallback.setContentVersion(DEFAULT_CONTENT_VERSION);
        return fallback;
    }

    private static PetDailySayingContentDTO toContentDTO(PetDailySayingContentRecord record) {
        if (record == null) {
            return null;
        }
        return new PetDailySayingContentDTO(
                record.getContentId(),
                record.getCategory(),
                record.getSubtype(),
                record.getTitle(),
                record.getPrimaryText(),
                record.getSecondaryText(),
                record.getAuthor(),
                record.getWork(),
                record.getSourceType(),
                record.getSourceUrl(),
                record.getSourceLocator(),
                record.getSourceOriginal(),
                record.getLanguage(),
                record.getTranslatorEditor(),
                record.getTags(),
                record.getTone(),
                record.getRecommendedWeight(),
                record.getCopyrightStatus(),
                record.getReviewStatus(),
                record.getRiskNotes(),
                record.isActive(),
                record.getContentVersion(),
                record.getCreatedAt(),
                record.getUpdatedAt());
    }

    private static PetDailySayingContentRecord toValidatedRecord(PetDailySayingContentDTO content) {
        if (content == null) {
            throw new IllegalArgumentException("内容不能为空");
        }
        String contentId = StrUtil.trim(content.getContentId());
        String category = StrUtil.trim(content.getCategory());
        String primaryText = StrUtil.trim(content.getPrimaryText());
        String reviewStatus = StrUtil.blankToDefault(StrUtil.trim(content.getReviewStatus()), REVIEW_PENDING);
        if (StrUtil.isBlank(contentId)) {
            throw new IllegalArgumentException("内容 ID 不能为空");
        }
        if (!CATEGORIES.contains(category)) {
            throw new IllegalArgumentException("内容类别无效");
        }
        if (StrUtil.isBlank(primaryText)) {
            throw new IllegalArgumentException("主文本不能为空");
        }
        if (content.isActive() && !REVIEW_PUBLISHABLE.equals(reviewStatus)) {
            throw new IllegalArgumentException("只有 review_status=可发布 的内容才能启用");
        }
        long now = System.currentTimeMillis();
        long createdAt = content.getCreatedAt() > 0L ? content.getCreatedAt() : now;
        return PetDailySayingContentRecord.builder()
                .contentId(contentId)
                .category(category)
                .subtype(StrUtil.trim(content.getSubtype()))
                .title(StrUtil.trim(content.getTitle()))
                .primaryText(primaryText)
                .secondaryText(StrUtil.trim(content.getSecondaryText()))
                .author(StrUtil.trim(content.getAuthor()))
                .work(StrUtil.trim(content.getWork()))
                .sourceType(StrUtil.trim(content.getSourceType()))
                .sourceUrl(StrUtil.trim(content.getSourceUrl()))
                .sourceLocator(StrUtil.trim(content.getSourceLocator()))
                .sourceOriginal(StrUtil.trim(content.getSourceOriginal()))
                .language(StrUtil.trim(content.getLanguage()))
                .translatorEditor(StrUtil.trim(content.getTranslatorEditor()))
                .tags(StrUtil.trim(content.getTags()))
                .tone(StrUtil.trim(content.getTone()))
                .recommendedWeight(content.getRecommendedWeight() > 0D ? content.getRecommendedWeight() : 1D)
                .copyrightStatus(StrUtil.trim(content.getCopyrightStatus()))
                .reviewStatus(reviewStatus)
                .riskNotes(StrUtil.trim(content.getRiskNotes()))
                .active(content.isActive())
                .contentVersion(StrUtil.blankToDefault(StrUtil.trim(content.getContentVersion()),
                        DEFAULT_CONTENT_VERSION))
                .createdAt(createdAt)
                .updatedAt(now)
                .build();
    }

    private static String latestVersion(PetDailySayingContentMapper mapper) {
        return StrUtil.blankToDefault(mapper.latestContentVersion(), DEFAULT_CONTENT_VERSION);
    }

    private static String replaceDogName(String text, String dogName) {
        if (text == null) {
            return null;
        }
        return text.replace("{dog_name}", StrUtil.blankToDefault(dogName, "狗狗"));
    }

    private static String normalizeFilter(String value) {
        String normalized = StrUtil.trim(value);
        return StrUtil.isBlank(normalized) ? null : normalized;
    }

    private static int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private static int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_ADMIN_PAGE_SIZE;
        }
        return Math.min(MAX_ADMIN_PAGE_SIZE, pageSize);
    }

    private static String normalizeDate(String value) {
        String normalized = normalizeFilter(value);
        return normalized == null ? LocalDate.now().toString() : normalized;
    }

    private static String normalizeAssignmentStatus(String value) {
        String normalized = normalizeFilter(value);
        if (normalized == null || "ALL".equals(normalized)) {
            return null;
        }
        if (!STATUS_UNREAD.equals(normalized) && !STATUS_READ.equals(normalized)) {
            throw new IllegalArgumentException("问候状态无效");
        }
        return normalized;
    }

    private static Map<String, Integer> createCategoryWeights() {
        Map<String, Integer> weights = new LinkedHashMap<>();
        weights.put("古诗词及狗狗解读", 38);
        weights.put("温柔短句", 16);
        weights.put("狗狗笑话", 17);
        weights.put("轻哲思", 11);
        weights.put("废话文学/反鸡汤", 18);
        return Collections.unmodifiableMap(weights);
    }

    private static BigInteger seed(long accountId, String today, String contentVersion, String salt) {
        String input = accountId + "|" + today + "|" + contentVersion + "|" + salt;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return new BigInteger(1, digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            return BigInteger.valueOf(input.toLowerCase(Locale.ROOT).hashCode() & 0xffffffffL);
        }
    }

}
