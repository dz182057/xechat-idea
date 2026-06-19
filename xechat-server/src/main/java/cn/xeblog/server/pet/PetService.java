package cn.xeblog.server.pet;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.pet.PetAdoptDTO;
import cn.xeblog.commons.entity.pet.PetDogDTO;
import cn.xeblog.commons.entity.pet.PetProfileDTO;
import cn.xeblog.commons.entity.pet.PetRaceResultDTO;
import cn.xeblog.commons.entity.pet.PetWalkDogDTO;
import cn.xeblog.commons.enums.Game;

/**
 * 兼容旧调用点的宠物服务门面。
 */
public final class PetService {

    private PetService() {
    }

    public static PetProfileDTO profile(User user) {
        ensureAccountUser(user);
        return PetProfileService.profile(user.getAccountId());
    }

    public static PetProfileDTO adopt(User user, PetAdoptDTO adopt) {
        ensureAccountUser(user);
        return PetProfileService.adopt(user.getAccountId(), adopt);
    }

    public static PetProfileDTO applyRaceResult(User user, PetRaceResultDTO result) {
        ensureAccountUser(user);
        return PetProfileService.recordRaceResult(user.getAccountId(), result);
    }

    public static PetProfileDTO walkDog(User user, PetWalkDogDTO request) {
        ensureAccountUser(user);
        return PetProfileService.walkDog(user.getAccountId(), request);
    }

    public static PetProfileDTO applyRaceResult(long accountId, PetRaceResultDTO result) {
        ensureAccountId(accountId);
        return PetProfileService.recordRaceResult(accountId, result);
    }

    public static PetProfileDTO changeBones(long accountId, int delta) {
        ensureAccountId(accountId);
        return PetProfileService.changeBones(accountId, delta);
    }

    public static PetProfileDTO applyGameTraining(long accountId, Game game, boolean win) {
        ensureAccountId(accountId);
        return PetProfileService.applyGameTraining(accountId, game, win);
    }

    public static PetProfileDTO applyMiniGameResult(long accountId, Game game, boolean win, long durationSeconds) {
        ensureAccountId(accountId);
        return PetProfileService.applyMiniGameResult(accountId, game, win, durationSeconds);
    }

    public static PetProfileDTO applyInteractionItemReward(long accountId, String itemId, int requestedBones) {
        ensureAccountId(accountId);
        return PetProfileService.applyInteractionItemReward(accountId, itemId, requestedBones);
    }

    public static PetProfileDTO spendRaceSignup(long accountId, String dogId, int energyCost, int bonesCost) {
        ensureAccountId(accountId);
        return PetProfileService.spendRaceSignup(accountId, dogId, energyCost, bonesCost);
    }

    public static PetDogDTO findRaceDog(long accountId) {
        ensureAccountId(accountId);
        return PetProfileService.findRaceDog(accountId);
    }

    private static void ensureAccountUser(User user) {
        if (user == null || user.isGuest() || user.getAccountId() <= 0L) {
            throw new IllegalArgumentException("请先登录账号");
        }
    }

    private static void ensureAccountId(long accountId) {
        if (accountId <= 0L) {
            throw new IllegalArgumentException("请先登录账号");
        }
    }
}
