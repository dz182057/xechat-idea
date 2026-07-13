package cn.xeblog.server.pet;

import cn.xeblog.commons.entity.pet.AdminPetSkinDTO;
import cn.xeblog.commons.entity.pet.AdminPetSkinListDTO;
import cn.xeblog.commons.enums.Game;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理员狗狗之家皮肤目录服务。
 */
public final class AdminPetSkinCatalogService {

    private AdminPetSkinCatalogService() {
    }

    public static AdminPetSkinListDTO list() {
        List<AdminPetSkinDTO> skins = new ArrayList<>();
        for (String itemId : PetItemDefinitions.skinItemIds()) {
            PetItemDefinition definition = PetItemDefinitions.byId(itemId);
            if (definition == null) {
                continue;
            }
            List<String> relatedGames = new ArrayList<>();
            for (Game game : definition.getRelatedGames()) {
                relatedGames.add(game.getName());
            }
            skins.add(new AdminPetSkinDTO(
                    itemId,
                    PetProfileService.getPetItemLabel(itemId),
                    definition.getRarity().name(),
                    relatedGames));
        }
        return new AdminPetSkinListDTO(skins);
    }

}
