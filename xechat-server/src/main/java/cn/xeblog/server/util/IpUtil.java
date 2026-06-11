package cn.xeblog.server.util;


import cn.hutool.core.util.ObjectUtil;
import cn.xeblog.commons.constants.IpConstants;
import cn.xeblog.commons.entity.IpRegion;
import cn.xeblog.server.service.IpRegionService;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.util.AttributeKey;

import java.net.InetSocketAddress;

/**
 * ip地址工具类
 *
 * @author nn200433
 * @date 2022-01-10 11:32:24
 */
public class IpUtil {

    public static final AttributeKey<String> CLIENT_IP_ATTRIBUTE =
            AttributeKey.valueOf("xechat.clientIp");

    private static IpRegionService ipRegionService;

    public IpUtil(IpRegionService param) {
        ipRegionService = param;
    }

    /**
     * 获取IP地区实体
     *
     * @param ip IP地址
     * @return {@link IpRegion }
     * @author nn200433
     */
    public static IpRegion getRegionByIp(String ip) {
        if (ObjectUtil.isNull(ipRegionService)) {
            return IpRegion.builder().ip(ip).country(IpConstants.IP_UN_KNOW_DEFAULT_REGION).build();
        }
        return ipRegionService.getRegion(ip);
    }

    /**
     * 获取IP地区名字
     *
     * @param ip IP地址
     * @return {@link String }
     * @author nn200433
     */
    public static String getRegionStrByIp(String ip) {
        return getRegionByIp(ip).toString();
    }

    /**
     * 通过ctx获取ip地址
     *
     * @param ctx ctx
     * @return {@link String }
     * @author nn200433
     */
    public static String getIpByCtx(ChannelHandlerContext ctx) {
        String clientIp = ctx.channel().attr(CLIENT_IP_ATTRIBUTE).get();
        if (isNotBlank(clientIp)) {
            return clientIp;
        }

        InetSocketAddress ipSocket = (InetSocketAddress) ctx.channel().remoteAddress();
        String hostAddress = ipSocket.getAddress().getHostAddress();
        return hostAddress;
    }

    public static void rememberForwardedIp(Channel channel, HttpHeaders headers) {
        String forwardedFor = firstForwardedIp(headers.get("X-Forwarded-For"));
        if (isNotBlank(forwardedFor)) {
            channel.attr(CLIENT_IP_ATTRIBUTE).set(forwardedFor);
            return;
        }

        String realIp = headers.get("X-Real-IP");
        if (isNotBlank(realIp)) {
            channel.attr(CLIENT_IP_ATTRIBUTE).set(realIp.trim());
        }
    }

    private static String firstForwardedIp(String header) {
        if (!isNotBlank(header)) {
            return null;
        }
        String[] parts = header.split(",");
        for (String part : parts) {
            if (isNotBlank(part)) {
                return part.trim();
            }
        }
        return null;
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * 通过上下文通道获取地址
     *
     * @param ctx ctx
     * @return {@link String }
     * @author nn200433
     */
    public static IpRegion getRegionByCtx(ChannelHandlerContext ctx) {
        return getRegionByIp(getIpByCtx(ctx));
    }

    /**
     * 通过上下文通道获取省份
     *
     * @param ctx ctx
     * @return {@link String }
     * @author nn200433
     */
    public static String getProvinceByCtx(ChannelHandlerContext ctx) {
        return getRegionByCtx(ctx).getProvince();
    }

    /**
     * 通过上下文通道获取省份简称
     *
     * @param ctx ctx
     * @return {@link String }
     * @author nn200433
     */
    public static String getShortProvinceByCtx(ChannelHandlerContext ctx) {
        return IpConstants.SHORT_PROVINCE.get(getProvinceByCtx(ctx));
    }

}
