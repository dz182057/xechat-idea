package cn.xeblog.commons.entity.duo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 双人小屋回忆分页。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DuoMemoryPageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<DuoMemoryDTO> items;
    private boolean hasMore;
    private String nextBeforeDate;
}
