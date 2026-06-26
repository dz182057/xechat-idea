package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 管理员重新分配狗狗每日问候。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminReassignPetDailySayingDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String assignmentId;

}
