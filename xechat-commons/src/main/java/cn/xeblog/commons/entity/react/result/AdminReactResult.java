package cn.xeblog.commons.entity.react.result;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @author anlingyi
 * @date 2023/2/18 8:15 PM
 */
@Data
@NoArgsConstructor
public class AdminReactResult {

    /**
     * 全局权限值
     */
    private int globalPermit;

    /**
     * 文件大小限制
     */
    private int maxFileSize;

    /**
     * 登录记录
     */
    private List<AdminLoginLogDTO> records;

    /**
     * 登录记录总数
     */
    private long total;

    /**
     * 当前页,从 1 开始
     */
    private int page;

    /**
     * 每页条数
     */
    private int pageSize;

    public AdminReactResult(int globalPermit, int maxFileSize) {
        this.globalPermit = globalPermit;
        this.maxFileSize = maxFileSize;
    }

    public AdminReactResult(List<AdminLoginLogDTO> records, long total, int page, int pageSize) {
        this.records = records;
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
    }

}
