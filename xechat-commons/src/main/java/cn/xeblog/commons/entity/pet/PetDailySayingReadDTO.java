package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 确认已读狗狗每日问候。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetDailySayingReadDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String assignmentId;

}
