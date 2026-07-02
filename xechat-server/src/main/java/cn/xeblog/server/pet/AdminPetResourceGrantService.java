package cn.xeblog.server.pet;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.xeblog.commons.entity.pet.AdminGrantPetResourceDTO;
import cn.xeblog.commons.entity.pet.AdminPetResourceGrantResultDTO;
import cn.xeblog.server.account.AccountException;
import cn.xeblog.server.account.DbInitializer;
import cn.xeblog.server.account.entity.Account;
import cn.xeblog.server.account.mapper.AccountMapper;
import org.apache.ibatis.session.SqlSession;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 管理员手动发放狗狗之家资源。
 */
public final class AdminPetResourceGrantService {

    public static final String TYPE_BONES = "BONES";
    public static final String TYPE_FOOD = "FOOD";
    public static final String TYPE_MAKEUP_CARD = "MAKEUP_CARD";
    public static final String TYPE_ENERGY = "ENERGY";
    public static final String TYPE_ITEM = "ITEM";
    public static final String TYPE_SKIN = "SKIN";
    public static final String TYPE_COLLECTION = "COLLECTION";

    private static final int MAX_GRANT_QUANTITY = 1_000_000;
    private static final int DEFAULT_BONES = 300;
    private static final int DEFAULT_FOOD = 6;
    private static final int DEFAULT_MAKEUP_CARDS = 0;
    private static final int DEFAULT_DOG_SLOTS = 1;
    private static final int DEFAULT_ENERGY_LIMIT = 10;
    private static final String LEDGER_DIRECTION_GAIN = "gain";
    private static final String LEDGER_SOURCE_ADMIN_MANUAL_GRANT = "admin_manual_grant";
    private static final Set<String> RESOURCE_TYPES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            TYPE_BONES,
            TYPE_FOOD,
            TYPE_MAKEUP_CARD,
            TYPE_ENERGY,
            TYPE_ITEM,
            TYPE_SKIN,
            TYPE_COLLECTION
    )));
    private static final Set<String> COLLECTION_ITEM_IDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "back_hill_ball",
            "back_hill_branch",
            "back_hill_leaf",
            "back_hill_stone",
            "back_hill_mushroom",
            "back_hill_feather",
            "creek_shell",
            "creek_snail",
            "creek_lotus",
            "creek_duck",
            "creek_coral",
            "creek_drop",
            "construction_site_helmet",
            "construction_site_gear",
            "construction_site_nut",
            "construction_site_brick",
            "construction_site_driver",
            "construction_site_clip",
            "old_library_scroll",
            "old_library_pen",
            "old_library_key",
            "old_library_candle",
            "old_library_book",
            "old_library_bookmark",
            "snow_mountain_snowflake",
            "snow_mountain_ice",
            "snow_mountain_skate",
            "snow_mountain_cloud",
            "snow_mountain_board",
            "snow_mountain_deer",
            "treasure_map_fragment",
            "mystery_cave_completed",
            "easter_neighbor_slipper",
            "easter_snail",
            "easter_visit_dog_tag",
            "easter_old_tennis",
            "breed_shiba_unlocked"
    )));

    private AdminPetResourceGrantService() {
    }

    public static AdminPetResourceGrantResultDTO grant(long adminAccountId, AdminGrantPetResourceDTO request) {
        long targetAccountId = normalizeTargetAccountId(request);
        String resourceType = normalizeResourceType(request == null ? null : request.getResourceType());
        int quantity = normalizeQuantity(request == null ? null : request.getQuantity());
        String itemId = StrUtil.trim(request == null ? null : request.getItemId());
        String note = normalizeNote(request == null ? null : request.getNote());

        synchronized (PetProfileService.accountLock(targetAccountId)) {
            try (SqlSession session = DbInitializer.factory().openSession(false)) {
                Account target = validateTargetAccount(session, targetAccountId);
                PetAssetsRecord assets = ensureAssets(session, targetAccountId);
                long now = System.currentTimeMillis();
                String sourceRef = "admin:" + adminAccountId + ":" + now;
                GrantAmounts amounts;

                switch (resourceType) {
                    case TYPE_BONES:
                        amounts = grantAssets(session.getMapper(PetAssetsMapper.class),
                                targetAccountId, "bones", assets.getBones(), quantity, now);
                        break;
                    case TYPE_FOOD:
                        amounts = grantAssets(session.getMapper(PetAssetsMapper.class),
                                targetAccountId, "food", assets.getFood(), quantity, now);
                        break;
                    case TYPE_MAKEUP_CARD:
                        amounts = grantAssets(session.getMapper(PetAssetsMapper.class),
                                targetAccountId, "makeup_card", assets.getMakeupCards(), quantity, now);
                        break;
                    case TYPE_ENERGY:
                        amounts = grantAssets(session.getMapper(PetAssetsMapper.class),
                                targetAccountId, "energy", assets.getEnergy(), quantity, now);
                        break;
                    case TYPE_ITEM:
                        amounts = grantItem(session, targetAccountId, itemId, quantity, sourceRef,
                                metadata(adminAccountId, resourceType, itemId, quantity, note), now);
                        break;
                    case TYPE_SKIN:
                        amounts = grantSkinItem(session, targetAccountId, itemId, quantity, sourceRef,
                                metadata(adminAccountId, resourceType, itemId, quantity, note), now);
                        break;
                    case TYPE_COLLECTION:
                        amounts = grantCollection(session, targetAccountId, itemId, quantity, now);
                        break;
                    default:
                        throw new AccountException("资源类型不合法");
                }

                session.commit();
                return new AdminPetResourceGrantResultDTO(
                        targetAccountId,
                        target.getAccount(),
                        target.getNickname(),
                        resourceType,
                        itemId,
                        quantity,
                        amounts.beforeAmount,
                        amounts.afterAmount,
                        note);
            }
        }
    }

    private static long normalizeTargetAccountId(AdminGrantPetResourceDTO request) {
        if (request == null || request.getTargetAccountId() == null || request.getTargetAccountId() <= 0L) {
            throw new AccountException("目标账号不能为空");
        }
        return request.getTargetAccountId();
    }

    private static String normalizeResourceType(String value) {
        String resourceType = StrUtil.trim(value);
        if (StrUtil.isBlank(resourceType)) {
            throw new AccountException("资源类型不能为空");
        }
        resourceType = resourceType.toUpperCase(Locale.ROOT);
        if (!RESOURCE_TYPES.contains(resourceType)) {
            throw new AccountException("资源类型不合法");
        }
        return resourceType;
    }

    private static int normalizeQuantity(Integer value) {
        if (value == null || value <= 0) {
            throw new AccountException("发放数量必须大于 0");
        }
        if (value > MAX_GRANT_QUANTITY) {
            throw new AccountException("单次发放数量不能超过 " + MAX_GRANT_QUANTITY);
        }
        return value;
    }

    private static String normalizeNote(String value) {
        String note = StrUtil.trim(value);
        if (note != null && note.length() > 120) {
            throw new AccountException("备注最多 120 个字符");
        }
        return note;
    }

    private static Account validateTargetAccount(SqlSession session, long targetAccountId) {
        Account target = session.getMapper(AccountMapper.class).findById(targetAccountId);
        if (target == null) {
            throw new AccountException("目标账号不存在");
        }
        if (Account.STATUS_DELETED.equals(target.getStatus())) {
            throw new AccountException("已删除账号不能发放资源");
        }
        return target;
    }

    private static PetAssetsRecord ensureAssets(SqlSession session, long accountId) {
        PetAssetsMapper mapper = session.getMapper(PetAssetsMapper.class);
        PetAssetsRecord assets = mapper.findByAccountId(accountId);
        if (assets != null) {
            return assets;
        }
        long now = System.currentTimeMillis();
        assets = PetAssetsRecord.builder()
                .accountId(accountId)
                .bones(DEFAULT_BONES)
                .food(DEFAULT_FOOD)
                .makeupCards(DEFAULT_MAKEUP_CARDS)
                .dogSlots(DEFAULT_DOG_SLOTS)
                .energy(DEFAULT_ENERGY_LIMIT)
                .energyDate(LocalDate.now().toString())
                .energyLimit(DEFAULT_ENERGY_LIMIT)
                .createdAt(now)
                .updatedAt(now)
                .build();
        mapper.insert(assets);
        return assets;
    }

    private static GrantAmounts grantAssets(PetAssetsMapper mapper, long accountId, String kind,
                                            int beforeAmount, int quantity, long now) {
        int afterAmount = checkedAdd(beforeAmount, quantity);
        int updated;
        switch (kind) {
            case "bones":
                updated = mapper.addBones(accountId, quantity, now);
                break;
            case "food":
                updated = mapper.addFood(accountId, quantity, now);
                break;
            case "makeup_card":
                updated = mapper.addMakeupCards(accountId, quantity, now);
                break;
            case "energy":
                updated = mapper.addEnergy(accountId, quantity, now);
                break;
            default:
                throw new AccountException("资源类型不合法");
        }
        if (updated <= 0) {
            throw new AccountException("发放资源失败");
        }
        return new GrantAmounts(beforeAmount, afterAmount);
    }

    private static GrantAmounts grantItem(SqlSession session, long accountId, String itemId, int quantity,
                                          String sourceRef, String metadataJson, long now) {
        String normalizedItemId = validateItemId(itemId);
        PetItemMapper itemMapper = session.getMapper(PetItemMapper.class);
        int before = countItem(itemMapper, accountId, normalizedItemId);
        int after = checkedAdd(before, quantity);
        if (itemMapper.addItem(accountId, normalizedItemId, quantity, now) <= 0) {
            throw new AccountException("发放道具失败");
        }
        recordItemLedger(session.getMapper(PetItemLedgerMapper.class), accountId, normalizedItemId, quantity,
                LEDGER_DIRECTION_GAIN, LEDGER_SOURCE_ADMIN_MANUAL_GRANT, sourceRef, metadataJson, now);
        return new GrantAmounts(before, after);
    }

    private static GrantAmounts grantSkinItem(SqlSession session, long accountId, String itemId, int quantity,
                                              String sourceRef, String metadataJson, long now) {
        String normalizedItemId = validateSkinItemId(itemId);
        if (quantity > 1) {
            throw new AccountException("皮肤数量上限为 1");
        }
        PetItemMapper itemMapper = session.getMapper(PetItemMapper.class);
        int before = countItem(itemMapper, accountId, normalizedItemId);
        if (before >= 1) {
            throw new AccountException("该皮肤已拥有");
        }
        if (itemMapper.addItemIfUnderLimit(accountId, normalizedItemId, quantity, 1, now) <= 0) {
            throw new AccountException("该皮肤已拥有");
        }
        recordItemLedger(session.getMapper(PetItemLedgerMapper.class), accountId, normalizedItemId, quantity,
                LEDGER_DIRECTION_GAIN, LEDGER_SOURCE_ADMIN_MANUAL_GRANT, sourceRef, metadataJson, now);
        return new GrantAmounts(before, before + quantity);
    }

    private static GrantAmounts grantCollection(SqlSession session, long accountId, String itemId, int quantity,
                                                 long now) {
        String normalizedItemId = validateCollectionItemId(itemId);
        PetCollectionMapper collectionMapper = session.getMapper(PetCollectionMapper.class);
        int before = countCollection(collectionMapper, accountId, normalizedItemId);
        int after = checkedAdd(before, quantity);
        if (collectionMapper.addCollectionQuantity(accountId, normalizedItemId, quantity, now) <= 0) {
            throw new AccountException("发放收藏品失败");
        }
        return new GrantAmounts(before, after);
    }

    private static String validateItemId(String itemId) {
        String normalizedItemId = StrUtil.trim(itemId);
        if (StrUtil.isBlank(normalizedItemId)) {
            throw new AccountException("道具 ID 不能为空");
        }
        if (PetItemDefinitions.byId(normalizedItemId) == null) {
            throw new AccountException("道具 ID 不存在");
        }
        return normalizedItemId;
    }

    private static String validateSkinItemId(String itemId) {
        String normalizedItemId = StrUtil.trim(itemId);
        if (StrUtil.isBlank(normalizedItemId)) {
            throw new AccountException("皮肤 ID 不能为空");
        }
        if (!PetItemDefinitions.isDailySkinShopItem(normalizedItemId)) {
            throw new AccountException("皮肤 ID 不存在");
        }
        return normalizedItemId;
    }

    private static String validateCollectionItemId(String itemId) {
        String normalizedItemId = StrUtil.trim(itemId);
        if (StrUtil.isBlank(normalizedItemId)) {
            throw new AccountException("收藏品 ID 不能为空");
        }
        if (!COLLECTION_ITEM_IDS.contains(normalizedItemId)) {
            throw new AccountException("收藏品 ID 不存在");
        }
        return normalizedItemId;
    }

    private static int countItem(PetItemMapper mapper, long accountId, String itemId) {
        PetItemRecord item = mapper.findByAccountIdAndItemId(accountId, itemId);
        return item == null ? 0 : Math.max(0, item.getCount());
    }

    private static int countCollection(PetCollectionMapper mapper, long accountId, String itemId) {
        Integer count = mapper.findCount(accountId, itemId);
        return count == null ? 0 : Math.max(0, count);
    }

    private static int checkedAdd(int before, int quantity) {
        long after = (long) before + quantity;
        if (after > Integer.MAX_VALUE) {
            throw new AccountException("发放后数量过大");
        }
        return (int) after;
    }

    private static String metadata(long adminAccountId, String resourceType, String itemId, int quantity, String note) {
        JSONObject json = JSONUtil.createObj()
                .set("adminAccountId", adminAccountId)
                .set("resourceType", resourceType)
                .set("itemId", itemId)
                .set("quantity", quantity);
        if (StrUtil.isNotBlank(note)) {
            json.set("note", note);
        }
        return json.toString();
    }

    private static void recordItemLedger(PetItemLedgerMapper mapper, long accountId, String itemId, int quantity,
                                         String direction, String source, String sourceRef,
                                         String metadataJson, long now) {
        PetItemLedgerRecord record = new PetItemLedgerRecord();
        record.setId(UUID.randomUUID().toString());
        record.setAccountId(accountId);
        record.setItemId(itemId);
        record.setQuantity(quantity);
        record.setDirection(direction);
        record.setSource(source);
        record.setSourceRef(sourceRef);
        record.setMetadataJson(metadataJson);
        record.setCreatedAt(now);
        mapper.insert(record);
    }

    private static final class GrantAmounts {
        private final int beforeAmount;
        private final int afterAmount;

        private GrantAmounts(int beforeAmount, int afterAmount) {
            this.beforeAmount = beforeAmount;
            this.afterAmount = afterAmount;
        }
    }

}
