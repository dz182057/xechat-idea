package cn.xeblog.server.action.handler.account;

import cn.hutool.core.util.StrUtil;
import cn.xeblog.commons.entity.ProfileUpdatedDTO;
import cn.xeblog.commons.entity.UpdateProfileDTO;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.account.AccountException;
import cn.xeblog.server.account.AccountService;
import cn.xeblog.server.account.AvatarService;
import cn.xeblog.server.account.entity.Account;
import cn.xeblog.server.action.ChannelAction;
import cn.xeblog.server.action.handler.AbstractActionHandler;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.cache.UserCache;
import lombok.extern.slf4j.Slf4j;

/**
 * 个人资料更新(UPDATE_PROFILE)。
 *
 * <p>支持三类变更(同次请求填写其一或多个,各自独立处理):
 * <ul>
 *     <li>nickname → 改昵称,广播 PROFILE_UPDATED</li>
 *     <li>avatarBase64 → 换头像,广播 PROFILE_UPDATED</li>
 *     <li>oldPassword + newPassword + 新 E2EE envelope → 改密码,**不广播**,改密后该账号全部 token 被吊销</li>
 *     <li>newE2eeSalt + newIdentityPrivKeyEnvelope → 原设备自动恢复后重包身份私钥</li>
 * </ul>
 * 三类都失败则回 SYSTEM 中文错误。任一成功就把对应 user 字段更新并把广播的 dto 发出。</p>
 *
 * @author dz
 * @date 2026/5/22
 */
@Slf4j
@DoAction(Action.UPDATE_PROFILE)
public class UpdateProfileActionHandler extends AbstractActionHandler<UpdateProfileDTO> {

    @Override
    protected void process(User user, UpdateProfileDTO body) {
        if (body == null) {
            user.send(ResponseBuilder.system("请求 body 为空"));
            return;
        }

        boolean nicknameChanged = false;
        boolean avatarChanged = false;
        String newNickname = user.getNickname();
        int newAvatarVer = user.getAvatarVersion();

        try {
            if (StrUtil.isNotBlank(body.getNickname())) {
                Account a = AccountService.changeNickname(user.getAccountId(), body.getNickname());
                newNickname = a.getNickname();
                user.setNickname(newNickname);
                // 同步更新该账号所有连接的 User 实例的 nickname
                for (User other : UserCache.getByAccount(user.getAccountId())) {
                    other.setNickname(newNickname);
                }
                nicknameChanged = true;
            }

            if (StrUtil.isNotBlank(body.getAvatarBase64())) {
                newAvatarVer = AvatarService.saveAvatar(user.getAccountId(), body.getAvatarBase64());
                user.setAvatarVersion(newAvatarVer);
                for (User other : UserCache.getByAccount(user.getAccountId())) {
                    other.setAvatarVersion(newAvatarVer);
                }
                avatarChanged = true;
            }

            if (StrUtil.isNotBlank(body.getOldPassword()) || StrUtil.isNotBlank(body.getNewPassword())) {
                AccountService.changePassword(user.getAccountId(),
                        body.getOldPassword(), body.getNewPassword(),
                        body.getNewE2eeSalt(), body.getNewIdentityPrivKeyEnvelope());
                user.send(ResponseBuilder.system("密码已修改,请重新登录"));
                // 改密后该账号所有 token 已被吊销,关闭当前 channel,客户端清 token 后回登录页
                if (user.getChannel() != null) {
                    user.getChannel().close();
                }
                return;
            }

            if (StrUtil.isNotBlank(body.getNewE2eeSalt())
                    || StrUtil.isNotBlank(body.getNewIdentityPrivKeyEnvelope())) {
                AccountService.updateIdentityEnvelope(user.getAccountId(),
                        body.getNewE2eeSalt(), body.getNewIdentityPrivKeyEnvelope());
                user.send(ResponseBuilder.system("E2EE 私钥已在原设备恢复"));
                return;
            }
        } catch (AccountException e) {
            user.send(ResponseBuilder.system(e.getMessage()));
            return;
        } catch (Exception e) {
            log.error("更新资料异常 accountId={}", user.getAccountId(), e);
            user.send(ResponseBuilder.system("更新失败,请稍后重试"));
            return;
        }

        if (nicknameChanged || avatarChanged) {
            ProfileUpdatedDTO dto = new ProfileUpdatedDTO(user.getAccountId(), newNickname, newAvatarVer);
            ChannelAction.send(ResponseBuilder.build(null, dto, MessageType.PROFILE_UPDATED));
            log.info("账号 {} 资料更新 nickname={} avatarVersion={}",
                    user.getAccountId(), newNickname, newAvatarVer);
            // 资料变更后通常需要重发在线列表(让其他端刷新 platform 集合无需,但 platform 不变此处可跳过)
            // 这里不重发在线列表,客户端按 PROFILE_UPDATED 本地合并
        } else {
            user.send(ResponseBuilder.system("没有可更新的字段"));
        }
    }

}
