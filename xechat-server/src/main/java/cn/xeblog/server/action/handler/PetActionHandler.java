package cn.xeblog.server.action.handler;

import cn.hutool.json.JSONUtil;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.pet.PetAdoptDTO;
import cn.xeblog.commons.entity.pet.PetDailySayingReadDTO;
import cn.xeblog.commons.entity.pet.PetExploreOpenDTO;
import cn.xeblog.commons.entity.pet.PetExploreOpenResultDTO;
import cn.xeblog.commons.entity.pet.PetExploreStartDTO;
import cn.xeblog.commons.entity.pet.PetFeedDTO;
import cn.xeblog.commons.entity.pet.PetFlip7ActionResultDTO;
import cn.xeblog.commons.entity.pet.PetMakeupCheckinDTO;
import cn.xeblog.commons.entity.pet.PetProfileDTO;
import cn.xeblog.commons.entity.pet.PetRaceResultDTO;
import cn.xeblog.commons.entity.pet.PetRequestDTO;
import cn.xeblog.commons.entity.pet.PetResolveOldTennisBallDTO;
import cn.xeblog.commons.entity.pet.PetRenameDTO;
import cn.xeblog.commons.entity.pet.PetResponseDTO;
import cn.xeblog.commons.entity.pet.PetSellItemDTO;
import cn.xeblog.commons.entity.pet.PetSetCompanionDTO;
import cn.xeblog.commons.entity.pet.PetShopBuyDTO;
import cn.xeblog.commons.entity.pet.PetTrainingSkillActionDTO;
import cn.xeblog.commons.entity.pet.PetTreasureHuntRedeemSkinDTO;
import cn.xeblog.commons.entity.pet.PetTreasureHuntSpinResultDTO;
import cn.xeblog.commons.entity.pet.PetUseItemDTO;
import cn.xeblog.commons.entity.pet.PetWalkDogDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.commons.enums.PetAction;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.cache.UserCache;
import cn.xeblog.server.pet.PetDailySayingService;
import cn.xeblog.server.pet.PetProfileService;

import java.util.Objects;

/**
 * 狗狗宇宙个人数据入口。
 */
@DoAction(Action.PET)
public class PetActionHandler extends AbstractActionHandler<PetRequestDTO> {

    @Override
    protected void process(User user, PetRequestDTO body) {
        PetAction petAction = body == null ? null : body.getPetAction();
        Long requestId = body == null ? null : body.getRequestId();
        if (petAction == null) {
            send(user, PetResponseDTO.fail(null, requestId, "狗狗操作为空"));
            return;
        }

        if (user.isGuest() || user.getAccountId() <= 0L) {
            send(user, PetResponseDTO.fail(petAction, requestId, "游客不支持狗狗宇宙，请登录账号后再进入"));
            return;
        }

        switch (petAction) {
            case PET_PROFILE:
                PetProfileDTO profile = PetProfileService.profile(user.getAccountId());
                send(user, PetResponseDTO.ok(petAction, requestId, profile));
                break;
            case ADOPT:
                try {
                    PetProfileDTO adoptedProfile = PetProfileService.adopt(user.getAccountId(), toBean(body.getContent(), PetAdoptDTO.class));
                    sendProfileResult(user, petAction, requestId, adoptedProfile);
                } catch (IllegalArgumentException e) {
                    send(user, PetResponseDTO.fail(petAction, requestId, e.getMessage()));
                }
                break;
            case RENAME:
                try {
                    PetProfileDTO renamedProfile = PetProfileService.rename(user.getAccountId(), toBean(body.getContent(), PetRenameDTO.class));
                    sendProfileResult(user, petAction, requestId, renamedProfile);
                } catch (IllegalArgumentException e) {
                    send(user, PetResponseDTO.fail(petAction, requestId, e.getMessage()));
                }
                break;
            case FEED:
                try {
                    PetProfileDTO fedProfile = PetProfileService.feed(user.getAccountId(), toBean(body.getContent(), PetFeedDTO.class));
                    sendProfileResult(user, petAction, requestId, fedProfile);
                } catch (IllegalArgumentException e) {
                    send(user, PetResponseDTO.fail(petAction, requestId, e.getMessage()));
                }
                break;
            case GREET_ALL_DOGS:
                PetProfileDTO greetedProfile = PetProfileService.greetAllDogs(user.getAccountId());
                sendProfileResult(user, petAction, requestId, greetedProfile);
                break;
            case DAILY_SAYING_GET:
                PetProfileDTO dailySayingProfile = PetDailySayingService.dailySaying(user.getAccountId());
                sendProfileResult(user, petAction, requestId, dailySayingProfile);
                break;
            case DAILY_SAYING_VIEW:
                try {
                    PetProfileDTO viewedDailySayingProfile = PetDailySayingService.viewDailySaying(
                            user.getAccountId(), toBean(body.getContent(), PetDailySayingReadDTO.class));
                    sendProfileResult(user, petAction, requestId, viewedDailySayingProfile);
                } catch (IllegalArgumentException e) {
                    send(user, PetResponseDTO.fail(petAction, requestId, e.getMessage()));
                }
                break;
            case DAILY_SAYING_READ:
                try {
                    PetProfileDTO readDailySayingProfile = PetDailySayingService.readDailySaying(
                            user.getAccountId(), toBean(body.getContent(), PetDailySayingReadDTO.class));
                    sendProfileResult(user, petAction, requestId, readDailySayingProfile);
                } catch (IllegalArgumentException e) {
                    send(user, PetResponseDTO.fail(petAction, requestId, e.getMessage()));
                }
                break;
            case WALK_DOG:
                try {
                    PetProfileDTO walkedProfile = PetProfileService.walkDog(user.getAccountId(),
                            toBean(body.getContent(), PetWalkDogDTO.class));
                    sendProfileResult(user, petAction, requestId, walkedProfile);
                } catch (IllegalArgumentException e) {
                    send(user, PetResponseDTO.fail(petAction, requestId, e.getMessage()));
                }
                break;
            case CHECKIN:
                try {
                    PetProfileDTO checkedProfile = PetProfileService.checkin(user.getAccountId());
                    sendProfileResult(user, petAction, requestId, checkedProfile);
                } catch (IllegalArgumentException e) {
                    send(user, PetResponseDTO.fail(petAction, requestId, e.getMessage()));
                }
                break;
            case MAKEUP_CHECKIN:
                try {
                    PetProfileDTO makeupProfile = PetProfileService.makeupCheckin(user.getAccountId(),
                            toBean(body.getContent(), PetMakeupCheckinDTO.class));
                    sendProfileResult(user, petAction, requestId, makeupProfile);
                } catch (IllegalArgumentException e) {
                    send(user, PetResponseDTO.fail(petAction, requestId, e.getMessage()));
                }
                break;
            case BUY_SLOT:
                try {
                    PetProfileDTO slotProfile = PetProfileService.buySlot(user.getAccountId());
                    sendProfileResult(user, petAction, requestId, slotProfile);
                } catch (IllegalArgumentException e) {
                    send(user, PetResponseDTO.fail(petAction, requestId, e.getMessage()));
                }
                break;
            case SET_COMPANION:
                try {
                    PetProfileDTO companionProfile = PetProfileService.setCompanion(user.getAccountId(),
                            toBean(body.getContent(), PetSetCompanionDTO.class));
                    sendProfileResult(user, petAction, requestId, companionProfile);
                } catch (IllegalArgumentException e) {
                    send(user, PetResponseDTO.fail(petAction, requestId, e.getMessage()));
                }
                break;
            case SHOP_BUY:
                try {
                    PetProfileDTO shopProfile = PetProfileService.shopBuy(user.getAccountId(),
                            toBean(body.getContent(), PetShopBuyDTO.class));
                    sendProfileResult(user, petAction, requestId, shopProfile);
                } catch (IllegalArgumentException e) {
                    send(user, PetResponseDTO.fail(petAction, requestId, e.getMessage()));
                }
                break;
            case SHOP_REFRESH:
                try {
                    PetProfileDTO shopProfile = PetProfileService.shopRefresh(user.getAccountId());
                    sendProfileResult(user, petAction, requestId, shopProfile);
                } catch (IllegalArgumentException e) {
                    send(user, PetResponseDTO.fail(petAction, requestId, e.getMessage()));
                }
                break;
            case SELL_ITEM:
                try {
                    PetProfileDTO sellProfile = PetProfileService.sellItem(user.getAccountId(),
                            toBean(body.getContent(), PetSellItemDTO.class));
                    sendProfileResult(user, petAction, requestId, sellProfile);
                } catch (IllegalArgumentException e) {
                    send(user, PetResponseDTO.fail(petAction, requestId, e.getMessage()));
                }
                break;
            case SELL_COLLECTION:
                try {
                    PetProfileDTO sellCollectionProfile = PetProfileService.sellCollection(user.getAccountId(),
                            toBean(body.getContent(), PetSellItemDTO.class));
                    sendProfileResult(user, petAction, requestId, sellCollectionProfile);
                } catch (IllegalArgumentException e) {
                    send(user, PetResponseDTO.fail(petAction, requestId, e.getMessage()));
                }
                break;
            case EXPLORE_START:
                try {
                    PetProfileDTO exploreProfile = PetProfileService.exploreStart(user.getAccountId(),
                            toBean(body.getContent(), PetExploreStartDTO.class));
                    sendProfileResult(user, petAction, requestId, exploreProfile);
                } catch (IllegalArgumentException e) {
                    send(user, PetResponseDTO.fail(petAction, requestId, e.getMessage()));
                }
                break;
            case EXPLORE_OPEN:
                try {
                    PetExploreOpenResultDTO openResult = PetProfileService.exploreOpen(user.getAccountId(),
                            toBean(body.getContent(), PetExploreOpenDTO.class));
                    send(user, PetResponseDTO.ok(petAction, requestId, openResult));
                    pushProfileUpdateToOtherConnections(user, openResult.getProfile());
                } catch (IllegalArgumentException e) {
                    send(user, PetResponseDTO.fail(petAction, requestId, e.getMessage()));
                }
                break;
            case EXPLORE_CANCEL:
                try {
                    PetProfileDTO cancelProfile = PetProfileService.exploreCancel(user.getAccountId(),
                            toBean(body.getContent(), PetExploreOpenDTO.class));
                    sendProfileResult(user, petAction, requestId, cancelProfile);
                } catch (IllegalArgumentException e) {
                    send(user, PetResponseDTO.fail(petAction, requestId, e.getMessage()));
                }
                break;
            case RESOLVE_OLD_TENNIS_BALL:
                try {
                    PetProfileDTO oldTennisBallProfile = PetProfileService.resolveOldTennisBall(user.getAccountId(),
                            toBean(body.getContent(), PetResolveOldTennisBallDTO.class));
                    sendProfileResult(user, petAction, requestId, oldTennisBallProfile);
                } catch (IllegalArgumentException e) {
                    send(user, PetResponseDTO.fail(petAction, requestId, e.getMessage()));
                }
                break;
            case TRAINING_LEARN:
                try {
                    PetProfileDTO trainingLearnProfile = PetProfileService.trainingLearn(user.getAccountId(),
                            toBean(body.getContent(), PetTrainingSkillActionDTO.class));
                    sendProfileResult(user, petAction, requestId, trainingLearnProfile);
                } catch (IllegalArgumentException e) {
                    send(user, PetResponseDTO.fail(petAction, requestId, e.getMessage()));
                }
                break;
            case TRAINING_UPGRADE:
                try {
                    PetProfileDTO trainingUpgradeProfile = PetProfileService.trainingUpgrade(user.getAccountId(),
                            toBean(body.getContent(), PetTrainingSkillActionDTO.class));
                    sendProfileResult(user, petAction, requestId, trainingUpgradeProfile);
                } catch (IllegalArgumentException e) {
                    send(user, PetResponseDTO.fail(petAction, requestId, e.getMessage()));
                }
                break;
            case TRAINING_EQUIP:
                try {
                    PetProfileDTO trainingEquipProfile = PetProfileService.trainingEquip(user.getAccountId(),
                            toBean(body.getContent(), PetTrainingSkillActionDTO.class));
                    sendProfileResult(user, petAction, requestId, trainingEquipProfile);
                } catch (IllegalArgumentException e) {
                    send(user, PetResponseDTO.fail(petAction, requestId, e.getMessage()));
                }
                break;
            case USE_ITEM:
                try {
                    PetUseItemDTO useItemRequest = toBean(body.getContent(), PetUseItemDTO.class);
                    if (isExploreChest(useItemRequest)) {
                        PetExploreOpenResultDTO chestResult = PetProfileService.openBackHillChest(user.getAccountId(),
                                useItemRequest);
                        send(user, PetResponseDTO.ok(petAction, requestId, chestResult));
                        pushProfileUpdateToOtherConnections(user, chestResult.getProfile());
                    } else {
                        PetProfileDTO useItemProfile = PetProfileService.useItem(user.getAccountId(), useItemRequest);
                        sendProfileResult(user, petAction, requestId, useItemProfile);
                    }
                } catch (IllegalArgumentException e) {
                    send(user, PetResponseDTO.fail(petAction, requestId, e.getMessage()));
                }
                break;
            case TREASURE_HUNT_SPIN:
                try {
                    PetTreasureHuntSpinResultDTO spinResult = PetProfileService.treasureHuntSpin(user.getAccountId());
                    send(user, PetResponseDTO.ok(petAction, requestId, spinResult));
                    pushProfileUpdateToOtherConnections(user, spinResult.getProfile());
                } catch (IllegalArgumentException e) {
                    send(user, PetResponseDTO.fail(petAction, requestId, e.getMessage()));
                }
                break;
            case TREASURE_HUNT_REDEEM_SKIN:
                try {
                    PetProfileDTO redeemedProfile = PetProfileService.treasureHuntRedeemSkin(user.getAccountId(),
                            toBean(body.getContent(), PetTreasureHuntRedeemSkinDTO.class));
                    sendProfileResult(user, petAction, requestId, redeemedProfile);
                } catch (IllegalArgumentException e) {
                    send(user, PetResponseDTO.fail(petAction, requestId, e.getMessage()));
                }
                break;
            case FLIP7_PLAY:
                try {
                    PetFlip7ActionResultDTO playResult = PetProfileService.flip7Play(user.getAccountId());
                    send(user, PetResponseDTO.ok(petAction, requestId, playResult));
                    pushProfileUpdateToOtherConnections(user, playResult.getProfile());
                } catch (IllegalArgumentException e) {
                    send(user, PetResponseDTO.fail(petAction, requestId, e.getMessage()));
                }
                break;
            case FLIP7_DRAW:
                try {
                    PetFlip7ActionResultDTO drawResult = PetProfileService.flip7Draw(user.getAccountId());
                    send(user, PetResponseDTO.ok(petAction, requestId, drawResult));
                    pushProfileUpdateToOtherConnections(user, drawResult.getProfile());
                } catch (IllegalArgumentException e) {
                    send(user, PetResponseDTO.fail(petAction, requestId, e.getMessage()));
                }
                break;
            case FLIP7_STAND:
                try {
                    PetFlip7ActionResultDTO standResult = PetProfileService.flip7Stand(user.getAccountId());
                    send(user, PetResponseDTO.ok(petAction, requestId, standResult));
                    pushProfileUpdateToOtherConnections(user, standResult.getProfile());
                } catch (IllegalArgumentException e) {
                    send(user, PetResponseDTO.fail(petAction, requestId, e.getMessage()));
                }
                break;
            case RACE_RESULT:
                try {
                    PetProfileDTO raceProfile = PetProfileService.recordRaceResult(user.getAccountId(),
                            toBean(body.getContent(), PetRaceResultDTO.class));
                    sendProfileResult(user, petAction, requestId, raceProfile);
                } catch (IllegalArgumentException e) {
                    send(user, PetResponseDTO.fail(petAction, requestId, e.getMessage()));
                }
                break;
            default:
                send(user, PetResponseDTO.fail(petAction, requestId, "暂不支持该狗狗操作"));
                break;
        }
    }

    private <T> T toBean(Object content, Class<T> clazz) {
        if (content == null) {
            return null;
        }
        if (clazz.isInstance(content)) {
            return clazz.cast(content);
        }
        try {
            return JSONUtil.toBean(JSONUtil.toJsonStr(content), clazz);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("狗狗请求内容无效", e);
        }
    }

    private boolean isExploreChest(PetUseItemDTO request) {
        String itemId = request == null || request.getItemId() == null ? null : request.getItemId().trim();
        return "chest_back_hill".equals(itemId)
                || "chest_creek".equals(itemId)
                || "chest_construction_site".equals(itemId)
                || "chest_old_library".equals(itemId);
    }

    private void send(User user, PetResponseDTO body) {
        user.send(ResponseBuilder.build(null, body, MessageType.PET));
    }

    private void sendProfileResult(User user, PetAction petAction, Long requestId, PetProfileDTO profile) {
        send(user, PetResponseDTO.ok(petAction, requestId, profile));
        pushProfileUpdateToOtherConnections(user, profile);
    }

    private void pushProfileUpdateToOtherConnections(User source, PetProfileDTO profile) {
        if (source == null || source.getAccountId() <= 0L || profile == null) {
            return;
        }
        PetResponseDTO pushBody = PetResponseDTO.ok(PetAction.PET_PROFILE, null, profile);
        for (User target : UserCache.getByAccount(source.getAccountId())) {
            if (target == null || Objects.equals(target.getId(), source.getId())) {
                continue;
            }
            target.send(ResponseBuilder.build(null, pushBody, MessageType.PET));
        }
    }

}
