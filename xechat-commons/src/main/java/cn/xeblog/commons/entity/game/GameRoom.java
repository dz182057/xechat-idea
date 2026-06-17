package cn.xeblog.commons.entity.game;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.enums.Game;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.*;

/**
 * @author anlingyi
 * @date 2022/5/25 10:30 上午
 */
@Data
public class GameRoom implements Serializable {

    /**
     * 房间号
     */
    private String id;

    /**
     * 游戏
     */
    private Game game;

    /**
     * 几人房
     */
    private int nums;

    /**
     * 游戏模式
     */
    private String gameMode;

    /**
     * 快问快答本局答题数
     */
    private int quickQuizQuestionCount;

    /**
     * 海龟汤猜底机会
     */
    private int turtleSoupGuessLimit;

    /**
     * 海龟汤首轮主持人：OWNER / GUEST / RANDOM
     */
    private String turtleSoupHostMode;

    /**
     * 狗狗赛跑模式：pure_betting / owned_dog
     */
    private String dogRaceMode;

    /**
     * 房主
     */
    private User homeowner;

    /**
     * 房间内玩家
     */
    private Map<String, Player> users = new LinkedHashMap<>();

    /**
     * 已邀请的玩家身份键列表
     */
    private transient Set<String> inviteUsers = new HashSet<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Player {
        /**
         * 玩家身份键。注册用户为 account:{accountId}，游客为 guest:{uuid}。
         */
        private String id;
        /**
         * 实际进入该游戏房间的连接 ID。游戏事件只发给这个端。
         */
        private String channelId;
        /**
         * 玩家昵称
         */
        private String username;
        /**
         * 持久化账号 ID；游客为 0。
         */
        private long accountId;
        /**
         * 登录账号；游客为空。
         */
        private String account;
        /**
         * 游客 / 客户端 uuid。
         */
        private String uuid;
        /**
         * 显示昵称。
         */
        private String nickname;
        /**
         * 是否已准备
         */
        private boolean readied;

        public Player(String id, String username) {
            this.id = id;
            this.username = username;
        }

        public Player(User user) {
            this.id = user.getIdentityKey();
            this.channelId = user.getId();
            this.username = user.getUsername();
            this.accountId = user.getAccountId();
            this.account = user.getAccount();
            this.uuid = user.getUuid();
            this.nickname = user.getNickname();
        }

        public boolean isConnection(User user) {
            return user != null && channelId != null && channelId.equals(user.getId());
        }

    }

    public boolean addUser(User user) {
        synchronized (users) {
            if (user.isGuest() || user.getAccountId() <= 0L) {
                return false;
            }
            if (getCurrentNums() > nums - 1) {
                return false;
            }

            if (existUser(user)) {
                return false;
            }

            users.put(user.getIdentityKey(), new Player(user));
            return true;
        }
    }

    public boolean existUser(User user) {
        return users.get(user.getIdentityKey()) != null;
    }

    public boolean removeUser(User user) {
        synchronized (users) {
            Player player = users.get(user.getIdentityKey());
            if (player == null || !player.isConnection(user)) {
                return false;
            }

            return users.remove(user.getIdentityKey()) != null;
        }
    }

    public int getCurrentNums() {
        return users.size();
    }

    public void addInviteUser(User user) {
        synchronized (inviteUsers) {
            inviteUsers.add(user.getIdentityKey());
        }
    }

    public void removeInviteUser(User user) {
        synchronized (inviteUsers) {
            inviteUsers.remove(user.getIdentityKey());
        }
    }

    public boolean readied(User user) {
        Player player = users.get(user.getIdentityKey());
        if (player == null || !player.isConnection(user)) {
            return false;
        }

        player.setReadied(true);
        return true;
    }

    public boolean readyCancelled(User user) {
        Player player = users.get(user.getIdentityKey());
        if (player == null || !player.isConnection(user)) {
            return false;
        }

        player.setReadied(false);
        return true;
    }

    public boolean isHomeowner(String username) {
        return homeowner.getUsername().equals(username);
    }

    public boolean isHomeowner(User user) {
        return homeowner != null
                && homeowner.getIdentityKey().equals(user.getIdentityKey())
                && homeowner.getId() != null
                && homeowner.getId().equals(user.getId());
    }

    public boolean isPlayerConnection(User user) {
        Player player = users.get(user.getIdentityKey());
        return player != null && player.isConnection(user);
    }

    public boolean isOvered() {
        return nums <= getCurrentNums();
    }

}
