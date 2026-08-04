package cn.xeblog.commons.entity.duo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 双人小屋每日默契题状态。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DuoDailyQuizDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private boolean unavailable;
    private String message;
    private DuoQuestionDTO question;
    private Integer myChoiceIndex;
    private boolean partnerAnswered;
    private Integer partnerChoiceIndex;
    private Boolean matched;
    private Long completedAt;
}
