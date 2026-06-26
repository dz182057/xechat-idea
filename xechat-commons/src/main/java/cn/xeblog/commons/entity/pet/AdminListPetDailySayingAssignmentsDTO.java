package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 管理员查询狗狗每日问候分配记录。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminListPetDailySayingAssignmentsDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String assignedServerDate;
    private String keyword;
    private String status;
    private Integer page;
    private Integer pageSize;

}
