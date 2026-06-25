package cn.xeblog.server.pet;

import cn.hutool.core.util.StrUtil;
import cn.xeblog.commons.entity.pet.AdminListPetDailySayingsDTO;
import cn.xeblog.commons.entity.pet.AdminPetDailySayingContentListDTO;
import cn.xeblog.commons.entity.pet.PetDailySayingContentDTO;
import cn.xeblog.commons.entity.pet.PetDailySayingDTO;
import cn.xeblog.commons.entity.pet.PetDailySayingReadDTO;
import cn.xeblog.commons.entity.pet.PetProfileDTO;
import cn.xeblog.commons.entity.pet.PetRecentSayingDTO;
import cn.xeblog.server.account.DbInitializer;
import org.apache.ibatis.session.SqlSession;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * 狗狗每日问候内容投放与已读服务。
 */
public final class PetDailySayingService {

    private static final String REVIEW_STATUS_PUBLISHABLE = "可发布";
    private static final String REVIEW_STATUS_REMOVED = "下架";
    private static final String DEFAULT_REVIEW_STATUS = "待编辑终审";
    private static final String ASSIGNMENT_UNREAD = "UNREAD";
    private static final String ASSIGNMENT_READ = "READ";
    private static final int RECENT_EXCLUDE_LIMIT = 180;
    private static final int RECENT_ACTIVITY_LIMIT = 3;
    private static final int DEFAULT_PAGE_SIZE = 30;
    private static final int MAX_PAGE_SIZE = 100;

    private PetDailySayingService() {
    }

    public static PetProfileDTO get(long accountId) {
        synchronized (PetProfileService.accountLock(accountId)) {
            ensureDailySayingAssignmentLocked(accountId);
            return PetProfileService.profileLocked(accountId);
        }
    }

    public static PetProfileDTO read(long accountId, PetDailySayingReadDTO request) {
        String assignmentId = request == null ? null : StrUtil.trim(request.getAssignmentId());
        if (StrUtil.isBlank(assignmentId)) {
            throw new IllegalArgumentException("请选择要确认的狗狗问候");
        }
        synchronized (PetProfileService.accountLock(accountId)) {
            readLocked(accountId, assignmentId);
            return PetProfileService.profileLocked(accountId);
        }
    }

    public static void attachDailySaying(SqlSession session, PetProfileDTO profile) {
        if (session == null || profile == null || profile.getAccountId() <= 0L) {
            return;
        }
        PetDailySayingAssignmentMapper assignmentMapper = session.getMapper(PetDailySayingAssignmentMapper.class);
        PetDailySayingContentMapper contentMapper = session.getMapper(PetDailySayingContentMapper.class);
        PetDailySayingAssignmentRecord unread = assignmentMapper.findUnread(profile.getAccountId());
        if (unread != null) {
            PetDailySayingContentRecord content = contentMapper.findById(unread.getContentId());
            if (content != null) {
                profile.setDailySaying(toDTO(unread, content));
            }
        }
        List<PetRecentSayingDTO> recentSayings = new ArrayList<>();
        for (PetDailySayingAssignmentRecord row : assignmentMapper.listRecentRead(
                profile.getAccountId(), RECENT_ACTIVITY_LIMIT)) {
            PetDailySayingContentRecord content = contentMapper.findById(row.getContentId());
            if (content != null) {
                recentSayings.add(new PetRecentSayingDTO(row.getAssignmentId(),
                        row.getPetNameSnapshot(), content.getCategory(), content.getPrimaryText(),
                        row.getReadAt() == null ? 0L : row.getReadAt()));
            }
        }
        profile.setRecentSayings(recentSayings);
    }

    public static AdminPetDailySayingContentListDTO list(AdminListPetDailySayingsDTO query) {
        AdminListPetDailySayingsDTO normalized = normalizeQuery(query);
        int page = normalized.getPage();
        int pageSize = normalized.getPageSize();
        int offset = (page - 1) * pageSize;
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            PetDailySayingContentMapper mapper = session.getMapper(PetDailySayingContentMapper.class);
            return new AdminPetDailySayingContentListDTO(
                    toContentDTOs(mapper.list(normalized, offset, pageSize)),
                    mapper.countList(normalized),
                    page,
                    pageSize,
                    mapper.listCategories(),
                    mapper.latestContentVersion());
        }
    }

    public static AdminPetDailySayingContentListDTO save(PetDailySayingContentDTO content) {
        PetDailySayingContentRecord normalized = normalizeContent(content);
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            PetDailySayingContentMapper mapper = session.getMapper(PetDailySayingContentMapper.class);
            PetDailySayingContentRecord existing = mapper.findById(normalized.getContentId());
            long now = System.currentTimeMillis();
            normalized.setCreatedAt(existing == null ? now : existing.getCreatedAt());
            normalized.setUpdatedAt(now);
            mapper.upsert(normalized);
            session.commit();
        }
        AdminListPetDailySayingsDTO query = new AdminListPetDailySayingsDTO();
        query.setKeyword(normalized.getContentId());
        query.setPage(1);
        query.setPageSize(DEFAULT_PAGE_SIZE);
        return list(query);
    }

    public static AdminPetDailySayingContentListDTO delete(String contentId) {
        String normalizedId = StrUtil.trim(contentId);
        if (StrUtil.isBlank(normalizedId)) {
            throw new IllegalArgumentException("请选择要下架的问候内容");
        }
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            PetDailySayingContentMapper mapper = session.getMapper(PetDailySayingContentMapper.class);
            if (mapper.softDelete(normalizedId, System.currentTimeMillis()) <= 0) {
                throw new IllegalArgumentException("问候内容不存在");
            }
            session.commit();
        }
        AdminListPetDailySayingsDTO query = new AdminListPetDailySayingsDTO();
        query.setKeyword(normalizedId);
        query.setPage(1);
        query.setPageSize(DEFAULT_PAGE_SIZE);
        return list(query);
    }

    private static void ensureDailySayingAssignmentLocked(long accountId) {
        String today = LocalDate.now().toString();
        long now = System.currentTimeMillis();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            PetDailySayingAssignmentMapper assignmentMapper =
                    session.getMapper(PetDailySayingAssignmentMapper.class);
            if (assignmentMapper.findUnread(accountId) != null
                    || assignmentMapper.findReadOnDate(accountId, today) != null) {
                return;
            }

            PetDogMapper dogMapper = session.getMapper(PetDogMapper.class);
            List<PetDogRecord> dogs = dogMapper.listByOwner(accountId);
            if (dogs.isEmpty()) {
                return;
            }

            PetDailySayingContentMapper contentMapper = session.getMapper(PetDailySayingContentMapper.class);
            PetDailySayingContentRecord content = chooseContent(accountId, today, assignmentMapper,
                    contentMapper.listPublishable(), contentMapper.latestContentVersion());
            if (content == null) {
                return;
            }

            PetDogRecord dog = chooseDog(session, accountId, dogs);
            assignmentMapper.insert(PetDailySayingAssignmentRecord.builder()
                    .assignmentId("PDS-" + UUID.randomUUID())
                    .accountId(accountId)
                    .petId(dog.getId())
                    .petNameSnapshot(dog.getName())
                    .petBreedSnapshot(dog.getBreed())
                    .petStageSnapshot(dog.getStage())
                    .contentId(content.getContentId())
                    .assignedServerDate(today)
                    .status(ASSIGNMENT_UNREAD)
                    .assignedAt(now)
                    .contentVersion(content.getContentVersion())
                    .build());
            session.commit();
        }
    }

    private static void readLocked(long accountId, String assignmentId) {
        String today = LocalDate.now().toString();
        long now = System.currentTimeMillis();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            PetDailySayingAssignmentMapper assignmentMapper =
                    session.getMapper(PetDailySayingAssignmentMapper.class);
            PetDailySayingAssignmentRecord assignment =
                    assignmentMapper.findByIdAndAccount(assignmentId, accountId);
            if (assignment == null) {
                throw new IllegalArgumentException("狗狗问候不存在");
            }
            if (ASSIGNMENT_READ.equals(assignment.getStatus())) {
                return;
            }

            PetDogMapper dogMapper = session.getMapper(PetDogMapper.class);
            PetDogRecord dog = dogMapper.findByIdAndOwner(assignment.getPetId(), accountId);
            int intimacyDelta = 0;
            boolean rewardApplied = false;
            if (dog != null) {
                int nextBond = grantGreetingBond(session.getMapper(PetDailyCounterMapper.class),
                        accountId, today, dog, now);
                intimacyDelta = Math.max(0, nextBond - dog.getBond());
                rewardApplied = intimacyDelta > 0;
                if (rewardApplied && dogMapper.updateCareStats(dog.getId(), accountId, nextBond, now) <= 0) {
                    rewardApplied = false;
                    intimacyDelta = 0;
                }
            }

            assignmentMapper.markRead(assignmentId, accountId, now, today, rewardApplied, intimacyDelta);
            session.commit();
        }
    }

    private static int grantGreetingBond(PetDailyCounterMapper counterMapper,
                                         long accountId,
                                         String today,
                                         PetDogRecord dog,
                                         long now) {
        String totalCounter = PetProfileService.DAILY_COUNTER_DOG_BOND_TOTAL_PREFIX + dog.getId();
        if (PetProfileService.findDailyCounterValue(counterMapper, accountId, today, totalCounter)
                >= PetProfileService.DAILY_DOG_BOND_LIMIT) {
            return dog.getBond();
        }
        if (counterMapper.incrementIfUnderLimit(accountId, today,
                PetProfileService.DAILY_COUNTER_GREET_BOND_PREFIX + dog.getId(), 1, now) <= 0) {
            return dog.getBond();
        }
        if (counterMapper.incrementIfUnderLimit(accountId, today,
                totalCounter, PetProfileService.DAILY_DOG_BOND_LIMIT, now) <= 0) {
            return dog.getBond();
        }
        return Math.max(0, Math.min(100, dog.getBond() + 1));
    }

    private static PetDailySayingContentRecord chooseContent(long accountId,
                                                            String today,
                                                            PetDailySayingAssignmentMapper assignmentMapper,
                                                            List<PetDailySayingContentRecord> publishable,
                                                            String contentVersion) {
        if (publishable == null || publishable.isEmpty()) {
            return null;
        }
        Set<String> recentContentIds = new HashSet<>(assignmentMapper.listRecentContentIds(
                accountId, RECENT_EXCLUDE_LIMIT));
        List<String> recentCategories = assignmentMapper.listRecentCategories(accountId, 2);
        String blockedCategory = recentCategories.size() >= 2
                && recentCategories.get(0).equals(recentCategories.get(1))
                ? recentCategories.get(0)
                : null;

        List<PetDailySayingContentRecord> eligible = new ArrayList<>();
        for (PetDailySayingContentRecord content : publishable) {
            if (!recentContentIds.contains(content.getContentId())
                    && (blockedCategory == null || !blockedCategory.equals(content.getCategory()))) {
                eligible.add(content);
            }
        }
        if (eligible.isEmpty()) {
            for (PetDailySayingContentRecord content : publishable) {
                if (!recentContentIds.contains(content.getContentId())) {
                    eligible.add(content);
                }
            }
        }
        if (eligible.isEmpty()) {
            eligible.addAll(publishable);
        }

        Random random = new Random(stableSeed(accountId + ":" + today + ":" +
                (contentVersion == null ? "" : contentVersion)));
        Map<String, List<PetDailySayingContentRecord>> groups = new LinkedHashMap<>();
        for (PetDailySayingContentRecord content : eligible) {
            groups.computeIfAbsent(content.getCategory(), ignored -> new ArrayList<>()).add(content);
        }
        String category = chooseCategory(groups, random);
        List<PetDailySayingContentRecord> categoryItems = groups.get(category);
        return chooseWeighted(categoryItems == null ? eligible : categoryItems, random);
    }

    private static String chooseCategory(Map<String, List<PetDailySayingContentRecord>> groups, Random random) {
        double total = 0D;
        for (List<PetDailySayingContentRecord> items : groups.values()) {
            total += totalWeight(items);
        }
        double roll = random.nextDouble() * Math.max(1D, total);
        double cursor = 0D;
        for (Map.Entry<String, List<PetDailySayingContentRecord>> entry : groups.entrySet()) {
            cursor += totalWeight(entry.getValue());
            if (roll <= cursor) {
                return entry.getKey();
            }
        }
        return groups.keySet().iterator().next();
    }

    private static PetDailySayingContentRecord chooseWeighted(List<PetDailySayingContentRecord> items,
                                                             Random random) {
        double total = totalWeight(items);
        double roll = random.nextDouble() * Math.max(1D, total);
        double cursor = 0D;
        for (PetDailySayingContentRecord item : items) {
            cursor += weight(item);
            if (roll <= cursor) {
                return item;
            }
        }
        return items.get(items.size() - 1);
    }

    private static double totalWeight(List<PetDailySayingContentRecord> items) {
        double total = 0D;
        for (PetDailySayingContentRecord item : items) {
            total += weight(item);
        }
        return total;
    }

    private static double weight(PetDailySayingContentRecord item) {
        return item == null || item.getRecommendedWeight() <= 0D ? 1D : item.getRecommendedWeight();
    }

    private static long stableSeed(String value) {
        long hash = 1469598103934665603L;
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= 1099511628211L;
        }
        return hash;
    }

    private static PetDogRecord chooseDog(SqlSession session, long accountId, List<PetDogRecord> dogs) {
        PetAssetsRecord assets = session.getMapper(PetAssetsMapper.class).findByAccountId(accountId);
        String companionDogId = assets == null ? null : assets.getCompanionDogId();
        if (StrUtil.isNotBlank(companionDogId)) {
            for (PetDogRecord dog : dogs) {
                if (companionDogId.equals(dog.getId())) {
                    return dog;
                }
            }
        }
        return dogs.get(0);
    }

    private static AdminListPetDailySayingsDTO normalizeQuery(AdminListPetDailySayingsDTO query) {
        AdminListPetDailySayingsDTO normalized = query == null ? new AdminListPetDailySayingsDTO() : query;
        normalized.setCategory(trimToNull(normalized.getCategory()));
        normalized.setReviewStatus(trimToNull(normalized.getReviewStatus()));
        normalized.setKeyword(trimToNull(normalized.getKeyword()));
        if (normalized.getPage() <= 0) {
            normalized.setPage(1);
        }
        if (normalized.getPageSize() <= 0) {
            normalized.setPageSize(DEFAULT_PAGE_SIZE);
        }
        normalized.setPageSize(Math.min(MAX_PAGE_SIZE, Math.max(1, normalized.getPageSize())));
        return normalized;
    }

    private static PetDailySayingContentRecord normalizeContent(PetDailySayingContentDTO content) {
        if (content == null) {
            throw new IllegalArgumentException("问候内容不能为空");
        }
        String contentId = StrUtil.trim(content.getContentId());
        if (StrUtil.isBlank(contentId)) {
            contentId = "CUS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
        String category = StrUtil.trim(content.getCategory());
        String primaryText = StrUtil.trim(content.getPrimaryText());
        if (StrUtil.isBlank(category)) {
            throw new IllegalArgumentException("问候分类不能为空");
        }
        if (StrUtil.isBlank(primaryText)) {
            throw new IllegalArgumentException("问候正文不能为空");
        }
        String reviewStatus = StrUtil.blankToDefault(StrUtil.trim(content.getReviewStatus()), DEFAULT_REVIEW_STATUS);
        if (content.isActive() && !REVIEW_STATUS_PUBLISHABLE.equals(reviewStatus)) {
            throw new IllegalArgumentException("只有审核状态为“可发布”的内容才能启用");
        }
        int charCount = content.getCharCount() > 0 ? content.getCharCount() : primaryText.length();
        double recommendedWeight = content.getRecommendedWeight() > 0D ? content.getRecommendedWeight() : 1D;
        return PetDailySayingContentRecord.builder()
                .contentId(contentId)
                .category(category)
                .subtype(trimToNull(content.getSubtype()))
                .title(trimToNull(content.getTitle()))
                .primaryText(primaryText)
                .secondaryText(trimToNull(content.getSecondaryText()))
                .author(trimToNull(content.getAuthor()))
                .work(trimToNull(content.getWork()))
                .sourceType(trimToNull(content.getSourceType()))
                .sourceUrl(trimToNull(content.getSourceUrl()))
                .sourceLocator(trimToNull(content.getSourceLocator()))
                .sourceOriginal(trimToNull(content.getSourceOriginal()))
                .language(trimToNull(content.getLanguage()))
                .translatorEditor(trimToNull(content.getTranslatorEditor()))
                .tags(trimToNull(content.getTags()))
                .tone(trimToNull(content.getTone()))
                .charCount(charCount)
                .recommendedWeight(recommendedWeight)
                .copyrightStatus(trimToNull(content.getCopyrightStatus()))
                .reviewStatus(reviewStatus)
                .riskNotes(trimToNull(content.getRiskNotes()))
                .active(content.isActive())
                .contentVersion(trimToNull(content.getContentVersion()))
                .build();
    }

    private static List<PetDailySayingContentDTO> toContentDTOs(List<PetDailySayingContentRecord> rows) {
        List<PetDailySayingContentDTO> records = new ArrayList<>();
        if (rows == null) {
            return records;
        }
        for (PetDailySayingContentRecord row : rows) {
            records.add(toDTO(row));
        }
        return records;
    }

    private static PetDailySayingDTO toDTO(PetDailySayingAssignmentRecord assignment,
                                           PetDailySayingContentRecord content) {
        return new PetDailySayingDTO(assignment.getStatus(), assignment.getAssignmentId(),
                assignment.getAssignedServerDate(), assignment.getPetId(), assignment.getPetNameSnapshot(),
                assignment.getPetBreedSnapshot(), assignment.getPetStageSnapshot(), toDTO(content),
                assignment.getReadAt(), assignment.getGreetingRewardApplied(),
                assignment.getGreetingIntimacyDelta());
    }

    private static PetDailySayingContentDTO toDTO(PetDailySayingContentRecord record) {
        return new PetDailySayingContentDTO(record.getContentId(), record.getCategory(), record.getSubtype(),
                record.getTitle(), record.getPrimaryText(), record.getSecondaryText(), record.getAuthor(),
                record.getWork(), record.getSourceType(), record.getSourceUrl(), record.getSourceLocator(),
                record.getSourceOriginal(), record.getLanguage(), record.getTranslatorEditor(), record.getTags(),
                record.getTone(), record.getCharCount(), record.getRecommendedWeight(),
                record.getCopyrightStatus(), record.getReviewStatus(), record.getRiskNotes(), record.isActive(),
                record.getContentVersion(), record.getCreatedAt(), record.getUpdatedAt());
    }

    private static String trimToNull(String value) {
        String trimmed = StrUtil.trim(value);
        return StrUtil.isBlank(trimmed) ? null : trimmed;
    }

}
