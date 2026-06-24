package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 单只狗狗今日陪伴完成状态。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetDailyCompanionDogStatusDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean greetCompleted;

    private boolean feedCompleted;

    private boolean playCompleted;

    private boolean outingCompleted;

    private int completedCount;

    private int totalCount;
}
