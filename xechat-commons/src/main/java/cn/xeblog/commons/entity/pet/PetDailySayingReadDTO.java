package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 狗狗每日问候阅读确认请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetDailySayingReadDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String assignmentId;

}
