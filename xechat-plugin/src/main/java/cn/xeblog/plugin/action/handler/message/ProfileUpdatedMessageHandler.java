package cn.xeblog.plugin.action.handler.message;

import cn.hutool.core.util.StrUtil;
import cn.xeblog.commons.entity.ProfileUpdatedDTO;
import cn.xeblog.commons.entity.Response;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.plugin.action.ConsoleAction;
import cn.xeblog.plugin.annotation.DoMessage;
import cn.xeblog.plugin.cache.DataCache;

import java.util.Map;

/**
 * 个人资料变更广播。
 *
 * @author dz
 * @date 2026/5/29
 */
@DoMessage(MessageType.PROFILE_UPDATED)
public class ProfileUpdatedMessageHandler extends AbstractMessageHandler<ProfileUpdatedDTO> {

    @Override
    protected void process(Response<ProfileUpdatedDTO> response) {
        ProfileUpdatedDTO dto = response.getBody();
        if (dto == null || DataCache.userMap == null) {
            return;
        }

        for (Map.Entry<String, User> entry : DataCache.userMap.entrySet()) {
            User user = entry.getValue();
            if (user == null || user.getAccountId() != dto.getAccountId()) {
                continue;
            }

            String oldUsername = entry.getKey();
            String newNickname = dto.getNickname();
            if (StrUtil.isNotBlank(newNickname)) {
                user.setNickname(newNickname);
                user.setUsername(newNickname);
            }
            user.setAvatarVersion(dto.getAvatarVersion());

            if (StrUtil.isNotBlank(newNickname) && !oldUsername.equals(newNickname)) {
                DataCache.userMap.remove(oldUsername);
                DataCache.userMap.put(newNickname, user);
                String account = DataCache.peerAccountByUsername.remove(oldUsername);
                if (account != null) {
                    DataCache.peerAccountByUsername.put(newNickname, account);
                    DataCache.peerNicknameByAccount.put(account, newNickname);
                }
                if (oldUsername.equals(DataCache.username)) {
                    DataCache.username = newNickname;
                }
                if (oldUsername.equals(DataCache.stickyPrivateTarget)) {
                    DataCache.stickyPrivateTarget = newNickname;
                }
            }
        }

        ConsoleAction.setConsoleTitle("Debug(" + DataCache.getOnlineUserTotal() + ")");
    }

}
