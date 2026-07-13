package cn.xeblog.server.pet;

import cn.xeblog.commons.entity.pet.AdminPetSkinDTO;
import cn.xeblog.commons.entity.pet.AdminPetSkinListDTO;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class AdminPetSkinCatalogServiceTest {

    @Test
    public void listsAllServerDefinedSkinsInDefinitionOrder() {
        AdminPetSkinListDTO result = AdminPetSkinCatalogService.list();

        List<String> itemIds = result.getSkins().stream()
                .map(AdminPetSkinDTO::getItemId)
                .collect(Collectors.toList());
        Assert.assertEquals(Arrays.asList(
                "item_minesweeper_skin_ink_wash",
                "item_minesweeper_skin_toy",
                "item_minesweeper_skin_fleet",
                "item_gomoku_skin_magic",
                "item_gomoku_skin_starry",
                "item_gomoku_skin_fairy",
                "item_gomoku_skin_ink",
                "item_gomoku_skin_toy",
                "item_gomoku_skin_deepsea",
                "item_gomoku_skin_lotus_ink"
        ), itemIds);
        Assert.assertEquals("扫雷星空舰队皮肤", result.getSkins().get(2).getName());
        Assert.assertEquals("EPIC", result.getSkins().get(2).getRarity());
        Assert.assertEquals(Arrays.asList("扫雷"), result.getSkins().get(2).getRelatedGames());
        Assert.assertEquals("LEGENDARY", result.getSkins().get(8).getRarity());
        Assert.assertEquals("五子棋水墨荷塘皮肤", result.getSkins().get(9).getName());
    }

}
