package cn.xeblog.plugin.game.turtlesoup;

import cn.xeblog.commons.entity.game.turtlesoup.TurtleSoupDTO;
import org.junit.Assert;
import org.junit.Test;

public class TurtleSoupTest {

    @Test
    public void previewKeyClueTextShouldHideClueFromHost() {
        TurtleSoupDTO dto = new TurtleSoupDTO("room-1");
        dto.setKeyClue("提前不应看到的关键线索");

        Assert.assertEquals("正式开始后按规则申请查看", TurtleSoup.previewKeyClueText(dto, true));
    }

    @Test
    public void previewKeyClueTextShouldRemainEmptyForGuesser() {
        TurtleSoupDTO dto = new TurtleSoupDTO("room-1");
        dto.setKeyClue("提前不应看到的关键线索");

        Assert.assertEquals("", TurtleSoup.previewKeyClueText(dto, false));
    }
}
