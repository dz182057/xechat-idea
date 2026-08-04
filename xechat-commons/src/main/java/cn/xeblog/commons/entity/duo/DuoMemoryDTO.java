package cn.xeblog.commons.entity.duo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 双人小屋按日期归档的回忆。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DuoMemoryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String date;
    private List<DuoInteractionDTO> interactions;
    private DuoDailyQuizDTO quizResult;
    private boolean interactionWarmthAwarded;
    private boolean playWarmthAwarded;
}
