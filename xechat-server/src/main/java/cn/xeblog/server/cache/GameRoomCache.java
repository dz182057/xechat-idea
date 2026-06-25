package cn.xeblog.server.cache;

import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.enums.Game;
import cn.xeblog.commons.enums.UserStatus;
import cn.xeblog.server.action.ChannelAction;
import cn.xeblog.server.game.gobang.GobangPetItemService;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author anlingyi
 * @date 2022/5/25 10:44 上午
 */
@Slf4j
public class GameRoomCache {

    /**
     * 游戏房间缓存，key -> roomId
     */
    private static final Map<String, GameRoom> GAME_ROOM_MAP = new ConcurrentHashMap<>(32);

    /**
     * 玩家当前所在游戏房间缓存，key -> user.identityKey
     */
    private static final Map<String, GameRoom> USER_ROOM_MAP = new ConcurrentHashMap<>(32);

    /**
     * 玩家实际进入游戏的连接缓存，key -> user.id(channelId)
     */
    private static final Map<String, GameRoom> CONNECTION_ROOM_MAP = new ConcurrentHashMap<>(32);

    private static final Map<String, ScheduledFuture<?>> EMPTY_ROOM_CLOSE_TASKS = new ConcurrentHashMap<>(32);
    private static final ScheduledExecutorService EMPTY_ROOM_CLOSE_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "gobang-empty-room-close");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile long gobangEmptyRoomTtlMillis = 90_000L;

    /**
     * 抢占房间
     *
     * @param roomId 房间ID
     * @return GameRoom 为null表示房间抢占失败
     */
    public static GameRoom seize(String roomId) {
        if (existRoom(roomId)) {
            return null;
        }

        GameRoom gameRoom = new GameRoom();
        gameRoom.setId(roomId);
        if (GAME_ROOM_MAP.put(roomId, gameRoom) == null) {
            return gameRoom;
        }

        return null;
    }

    /**
     * 移除房间
     *
     * @param roomId 房间ID
     */
    public static void removeRoom(String roomId) {
        cancelEmptyRoomClose(roomId);
        GameRoom gameRoom = getGameRoom(roomId);
        if (gameRoom == null) {
            return;
        }

        log.debug("游戏房间关闭 -> {}", gameRoom);

        GAME_ROOM_MAP.remove(roomId);
        if (gameRoom.getUsers().size() > 0) {
            gameRoom.getUsers().forEach((k, v) -> {
                USER_ROOM_MAP.remove(v.getId());
                if (v.getChannelId() != null) {
                    CONNECTION_ROOM_MAP.remove(v.getChannelId());
                }
            });
        }

        java.util.Set<User> userSet = ConcurrentHashMap.newKeySet();
        gameRoom.getUsers().forEach((k, v) -> {
            User player = UserCache.get(v.getChannelId());
            if (player != null) {
                userSet.add(player);
            }
        });
        if (userSet.size() > 0) {
            userSet.forEach(player -> {
                player.setStatus(UserStatus.FISHING);
                player.setCurrentGame(null);
                ChannelAction.updateUserStatus(player);
            });
        }
    }

    /**
     * 判断房间是否存在
     *
     * @param roomId 房间ID
     * @return
     */
    public static boolean existRoom(String roomId) {
        return GAME_ROOM_MAP.containsKey(roomId);
    }

    /**
     * 玩家加入房间
     *
     * @param roomId 房间ID
     * @param user   玩家
     * @return
     */
    public static boolean joinRoom(String roomId, User user) {
        GameRoom gameRoom = GAME_ROOM_MAP.get(roomId);
        if (gameRoom == null) {
            return false;
        }

        String identityKey = user.getIdentityKey();
        if (USER_ROOM_MAP.containsKey(identityKey)) {
            return false;
        }

        if (gameRoom.addUser(user)) {
            cancelEmptyRoomClose(gameRoom.getId());
            USER_ROOM_MAP.put(identityKey, gameRoom);
            if (user.getId() != null) {
                CONNECTION_ROOM_MAP.put(user.getId(), gameRoom);
            }
            user.setStatus(UserStatus.PLAYING);
            user.setCurrentGame(gameRoom.getGame());
            ChannelAction.updateUserStatus(user);
            return true;
        }

        return false;
    }

    /**
     * 玩家离开房间
     *
     * @param roomId 房间ID
     * @param user   玩家
     * @return
     */
    public static boolean leftRoom(String roomId, User user) {
        GameRoom gameRoom = GAME_ROOM_MAP.get(roomId);
        if (gameRoom == null) {
            return false;
        }

        if (gameRoom.removeUser(user)) {
            USER_ROOM_MAP.remove(user.getIdentityKey());
            if (user.getId() != null) {
                CONNECTION_ROOM_MAP.remove(user.getId());
            }
            user.setStatus(UserStatus.FISHING);
            user.setCurrentGame(null);
            ChannelAction.updateUserStatus(user);
            if (gameRoom.getCurrentNums() == 0 || gameRoom.getActiveNums() == 0 || gameRoom.isHomeowner(user)) {
                removeRoom(gameRoom.getId());
            }
            return true;
        }

        return false;
    }

    /**
     * 玩家连接断开时仅释放连接路由，不释放身份席位。
     *
     * @return true 表示房间已无在线玩家并被移除
     */
    public static boolean disconnectRoomConnection(String roomId, User user) {
        GameRoom gameRoom = GAME_ROOM_MAP.get(roomId);
        if (gameRoom == null) {
            return false;
        }

        if (!gameRoom.disconnectUser(user)) {
            return false;
        }

        if (user.getId() != null) {
            CONNECTION_ROOM_MAP.remove(user.getId());
        }
        if (gameRoom.getActiveNums() == 0) {
            if (gameRoom.getGame() == Game.GOBANG) {
                scheduleEmptyGobangRoomClose(gameRoom.getId());
                return false;
            }
            removeRoom(gameRoom.getId());
            return true;
        }
        return false;
    }

    public static GameRoom reconnectRoom(User user) {
        if (user == null || user.getId() == null) {
            return null;
        }

        GameRoom gameRoom = USER_ROOM_MAP.get(user.getIdentityKey());
        if (gameRoom == null || !gameRoom.reconnectUser(user)) {
            return null;
        }

        CONNECTION_ROOM_MAP.put(user.getId(), gameRoom);
        cancelEmptyRoomClose(gameRoom.getId());
        user.setStatus(UserStatus.PLAYING);
        user.setCurrentGame(gameRoom.getGame());
        ChannelAction.updateUserStatus(user);
        return gameRoom;
    }

    public static GameRoom getGameRoom(String roomId) {
        return GAME_ROOM_MAP.get(roomId);
    }

    public static GameRoom getGameRoomByUserId(String userId) {
        return USER_ROOM_MAP.get(userId);
    }

    public static GameRoom getGameRoomByConnectionId(String channelId) {
        return CONNECTION_ROOM_MAP.get(channelId);
    }

    static void clear() {
        EMPTY_ROOM_CLOSE_TASKS.values().forEach(task -> task.cancel(false));
        EMPTY_ROOM_CLOSE_TASKS.clear();
        GAME_ROOM_MAP.clear();
        USER_ROOM_MAP.clear();
        CONNECTION_ROOM_MAP.clear();
        gobangEmptyRoomTtlMillis = 90_000L;
    }

    static void setGobangEmptyRoomTtlMillisForTest(long ttlMillis) {
        gobangEmptyRoomTtlMillis = ttlMillis;
    }

    private static void scheduleEmptyGobangRoomClose(String roomId) {
        if (roomId == null || EMPTY_ROOM_CLOSE_TASKS.containsKey(roomId)) {
            return;
        }
        ScheduledFuture<?> task = EMPTY_ROOM_CLOSE_EXECUTOR.schedule(
                () -> closeEmptyGobangRoom(roomId),
                Math.max(1L, gobangEmptyRoomTtlMillis),
                TimeUnit.MILLISECONDS);
        ScheduledFuture<?> previous = EMPTY_ROOM_CLOSE_TASKS.putIfAbsent(roomId, task);
        if (previous != null) {
            task.cancel(false);
        }
    }

    private static void closeEmptyGobangRoom(String roomId) {
        EMPTY_ROOM_CLOSE_TASKS.remove(roomId);
        GameRoom gameRoom = GAME_ROOM_MAP.get(roomId);
        if (gameRoom == null || gameRoom.getGame() != Game.GOBANG || gameRoom.getActiveNums() > 0) {
            return;
        }
        GobangPetItemService.clearRoom(roomId);
        removeRoom(roomId);
    }

    private static void cancelEmptyRoomClose(String roomId) {
        if (roomId == null) {
            return;
        }
        ScheduledFuture<?> task = EMPTY_ROOM_CLOSE_TASKS.remove(roomId);
        if (task != null) {
            task.cancel(false);
        }
    }

}
