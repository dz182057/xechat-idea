package cn.xeblog.commons.entity.duo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 双人小屋每日默契题。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DuoQuestionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private long id;
    private String content;
    private List<String> options;
}
