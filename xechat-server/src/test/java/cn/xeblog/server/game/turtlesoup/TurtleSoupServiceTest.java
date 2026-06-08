package cn.xeblog.server.game.turtlesoup;

import cn.xeblog.commons.entity.game.turtlesoup.TurtleSoupDTO;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TurtleSoupServiceTest {

    @Test
    public void previewDtoForHostShouldHideKeyClue() {
        TurtleSoupDTO dto = previewDto();

        TurtleSoupService.sanitizePreview(dto, true);

        assertNull("主持人选题预览时不应提前拿到关键线索", dto.getKeyClue());
        assertEquals("主持人预览仍应看到汤面", "汤面", dto.getSurface());
    }

    @Test
    public void previewDtoForGuesserShouldHideStoryDetails() {
        TurtleSoupDTO dto = previewDto();

        TurtleSoupService.sanitizePreview(dto, false);

        assertNull("猜题人预览等待时不应拿到标题", dto.getTitle());
        assertNull("猜题人预览等待时不应拿到汤面", dto.getSurface());
        assertNull("猜题人预览等待时不应拿到关键线索", dto.getKeyClue());
    }

    private TurtleSoupDTO previewDto() {
        TurtleSoupDTO dto = new TurtleSoupDTO("room-1");
        dto.setEvent(TurtleSoupDTO.Event.PREVIEW_STORY);
        dto.setTitle("标题");
        dto.setSurface("汤面");
        dto.setBottom("汤底");
        dto.setKeyClue("关键线索");
        dto.setDifficulty("简单");
        dto.setTags("测试");
        return dto;
    }
}
