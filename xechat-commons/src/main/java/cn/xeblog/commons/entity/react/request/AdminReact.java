package cn.xeblog.commons.entity.react.request;

import cn.xeblog.commons.entity.react.BaseReact;
import cn.xeblog.commons.enums.Permissions;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author anlingyi
 * @date 2023/2/18 8:07 PM
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AdminReact extends BaseReact {

    /**
     * 操作
     */
    private Operate operate;

    /**
     * 权限
     */
    private Permissions permissions;

    /**
     * 用户id
     */
    private String uid;

    /**
     * 配置值
     */
    private String value;

    /**
     * 登录账号筛选
     */
    private String account;

    /**
     * 昵称筛选
     */
    private String nickname;

    /**
     * IP 筛选
     */
    private String ip;

    /**
     * 平台筛选
     */
    private String platform;

    /**
     * 行为主动作筛选
     */
    private String action;

    /**
     * 行为子动作筛选
     */
    private String subAction;

    /**
     * 行为处理结果筛选
     */
    private String resultStatus;

    /**
     * 登录结果筛选
     */
    private Boolean success;

    /**
     * 开始时间(epoch ms)
     */
    private Long startAt;

    /**
     * 结束时间(epoch ms)
     */
    private Long endAt;

    /**
     * 当前页,从 1 开始
     */
    private Integer page;

    /**
     * 每页条数
     */
    private Integer pageSize;

    public AdminReact(Operate operate, String value) {
        this.operate = operate;
        this.value = value;
    }

    public AdminReact(Operate operate, Permissions permissions) {
        this.operate = operate;
        this.permissions = permissions;
    }

    public AdminReact(Operate operate, Permissions permissions, String uid) {
        this.operate = operate;
        this.permissions = permissions;
        this.uid = uid;
    }

    public enum Operate {
        /**
         * 查询权限
         */
        QUERY_PERMIT,
        /**
         * 全局权限添加
         */
        GLOBAL_PERMIT_ADD,
        /**
         * 全局文件大小限制
         */
        GLOBAL_MAX_FILE_SIZE,
        /**
         * 全局权限移除
         */
        GLOBAL_PERMIT_REMOVE,
        /**
         * 用户权限添加
         */
        USER_PERMIT_ADD,
        /**
         * 用户权限移除
         */
        USER_PERMIT_REMOVE,
        /**
         * 查询用户登录记录
         */
        QUERY_LOGIN_LOGS,
        /**
         * 查询用户操作记录
         */
        QUERY_BEHAVIOR_LOGS
        ;
    }

}
