package cn.xeblog.commons.entity.game.drawguess;

import cn.xeblog.commons.entity.game.GameDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 你画我猜联机数据。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DrawGuessDTO extends GameDTO {

    private Event event;
    private String drawerId;
    private String drawerName;
    private String guesserId;
    private String guesserName;
    private String maskedWord;
    private Integer wordLength;
    private String hint;
    private String word;
    private String text;
    private Line line;

    public enum Event {
        START_ROUND,
        DRAW,
        CLEAR,
        GUESS,
        CORRECT
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Line {
        private double x1;
        private double y1;
        private double x2;
        private double y2;
        private String color;
        private int size;
    }

    public void setEvent(String event) {
        this.event = Event.valueOf(event);
    }

    public void setEvent(Event event) {
        this.event = event;
    }

}
