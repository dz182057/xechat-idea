package cn.xeblog.commons.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 桌面端更新发布信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DesktopUpdateInfoDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String version;
    private String title;
    private String notes;
    private boolean mandatory;
    private boolean enabled;
    private String fileName;
    private String latestFileName;
    private String blockMapFileName;
    private long size;
    private String sha256;
    private long publishedAt;
    private String downloadUrl;

}
