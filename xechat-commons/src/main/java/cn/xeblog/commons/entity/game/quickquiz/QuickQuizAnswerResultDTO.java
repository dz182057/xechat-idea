package cn.xeblog.commons.entity.game.quickquiz;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 快问快答单题/整局结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuickQuizAnswerResultDTO implements Serializable {

    private String roomId;

    private QuickQuizQuestionDTO question;

    private List<QuickQuizAnswerViewDTO> answers;

    private List<QuickQuizPlayerScoreDTO> rankings;

    private int roundNo;

    private int totalRounds;

    private boolean finished;

    private int prizePool;

    private int rewardPerWinner;

    private boolean economyApplied;

    private List<String> petItemNotices;

    public QuickQuizAnswerResultDTO(String roomId, QuickQuizQuestionDTO question, List<QuickQuizAnswerViewDTO> answers,
                                    List<QuickQuizPlayerScoreDTO> rankings, int roundNo, int totalRounds,
                                    boolean finished, int prizePool, int rewardPerWinner, boolean economyApplied) {
        this.roomId = roomId;
        this.question = question;
        this.answers = answers;
        this.rankings = rankings;
        this.roundNo = roundNo;
        this.totalRounds = totalRounds;
        this.finished = finished;
        this.prizePool = prizePool;
        this.rewardPerWinner = rewardPerWinner;
        this.economyApplied = economyApplied;
    }

}
