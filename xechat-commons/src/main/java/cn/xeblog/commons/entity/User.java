package cn.xeblog.commons.entity;

import cn.hutool.core.map.MapUtil;
import cn.xeblog.commons.constants.IpConstants;
import cn.xeblog.commons.enums.Permissions;
import cn.xeblog.commons.enums.Platform;
import cn.xeblog.commons.enums.UserStatus;
import io.netty.channel.Channel;
import lombok.*;

import java.io.Serializable;
import java.util.Objects;
import java.util.Set;

/**
 * @author anlingyi
 * @date 2020/5/29
 */
@ToString
@NoArgsConstructor
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * uuid
     */
    @Getter
    @Setter
    private String uuid;

    /**
     * 连接级 ID(channel 维度,断线即换),与持久化账号无关
     */
    @Getter
    @Setter
    private String id;

    /**
     * 持久化账号 ID(雪花,永久不变,跨连接稳定)
     */
    @Getter
    @Setter
    private long accountId;

    /**
     * 登录账号(只读,注册后不可改,正则 [a-zA-Z0-9_]{4,20})
     */
    @Getter
    @Setter
    private String account;

    /**
     * 用户昵称(显示名,唯一,可改)。setter 见下方 {@link #setNickname(String)}。
     */
    @Getter
    private String nickname;

    /**
     * 头像版本,换头像 +1,客户端用 ?v= 破缓存
     */
    @Getter
    @Setter
    private int avatarVersion;

    /**
     * 用户昵称(过渡兼容字段)
     * <p>历史代码读 username;新代码应改用 nickname。下一版本删除。</p>
     *
     * @deprecated 使用 {@link #nickname} 代替
     */
    @Deprecated
    @Getter
    @Setter
    private String username;

    /**
     * 用户状态
     */
    @Getter
    @Setter
    private UserStatus status;

    /**
     * 地区简称
     */
    @Getter
    @Setter
    private String shortRegion;

    /**
     * 用户IP
     */
    @Getter
    @Setter
    private transient String ip;

    /**
     * 用户所在区域
     */
    @Getter
    @Setter
    private transient IpRegion region;

    /**
     * 用户角色
     */
    @Getter
    @Setter
    private Role role;

    /**
     * 用户权限
     */
    @Getter
    @Setter
    private int permit;

    @Getter
    @Setter
    private Platform platform;

    /**
     * 同账号当前所有在线端的 platform 集合(仅在线列表响应中由服务端填充)。
     * <p>普通消息中为 null,只在 ONLINE_USERS 响应里有值。</p>
     */
    @Getter
    @Setter
    private Set<Platform> platforms;

    /**
     * 通道
     */
    @Getter
    private transient Channel channel;

    public enum Role {
        /**
         * 管理员
         */
        ADMIN,
        /**
         * 用户
         */
        USER
    }

    public User(String id, String username, UserStatus status, String ip, IpRegion region, Channel channel) {
        this.id = id;
        this.username = username;
        this.nickname = username;
        this.status = status;
        this.ip = ip;
        this.region = region;
        this.channel = channel;
        this.platform = Platform.IDEA;
        this.shortRegion = MapUtil.getStr(IpConstants.SHORT_PROVINCE, region.getProvince(), region.getCountry());
    }

    /**
     * 账号体系登录用构造器
     */
    public User(String id, long accountId, String account, String nickname, int avatarVersion,
                UserStatus status, String ip, IpRegion region, Channel channel) {
        this.id = id;
        this.accountId = accountId;
        this.account = account;
        this.nickname = nickname;
        this.username = nickname;
        this.avatarVersion = avatarVersion;
        this.status = status;
        this.ip = ip;
        this.region = region;
        this.channel = channel;
        this.platform = Platform.IDEA;
        this.shortRegion = region == null ? null
                : MapUtil.getStr(IpConstants.SHORT_PROVINCE, region.getProvince(), region.getCountry());
    }

    /**
     * 同步 nickname → username(过渡期保持下行兼容)
     */
    public void setNickname(String nickname) {
        this.nickname = nickname;
        this.username = nickname;
    }

    public void send(Response response) {
        if (channel == null) {
            return;
        }

        channel.writeAndFlush(response);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /**
     * 添加权限
     *
     * @param permissions
     */
    public void addPermit(Permissions permissions) {
        this.permit |= permissions.getValue();
    }

    /**
     * 移除权限
     *
     * @param permissions
     */
    public void removePermit(Permissions permissions) {
        if (hasPermit(permissions)) {
            this.permit ^= permissions.getValue();
        }
    }

    /**
     * 是否存在权限
     *
     * @param permissions
     * @return
     */
    public boolean hasPermit(Permissions permissions) {
        int value = permissions.getValue();
        return (this.permit & value) == value;
    }

    /**
     * 是否是管理员
     *
     * @return
     */
    public boolean isAdmin() {
        return this.role == Role.ADMIN;
    }

}
