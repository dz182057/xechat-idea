package cn.xeblog.commons.entity.duo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 双人小屋当天状态。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DuoTodayDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String date;
    private DuoInteractionDTO myInteraction;
    private DuoInteractionDTO partnerInteraction;
    private DuoDailyQuizDTO quiz;
    private boolean interactionWarmthAwarded;
    private boolean playWarmthAwarded;
}
