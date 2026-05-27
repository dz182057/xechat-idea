package cn.xeblog.commons.entity.react.request;

import lombok.Data;

/**
 * @author anlingyi
 * @date 2022/9/19 8:54 AM
 */
@Data
public class UploadReact {

    private String fileType;

    private byte[] bytes;

    /**
     * false 表示只上传文件,不让服务端自动广播图片消息。
     */
    private Boolean broadcast;

}
