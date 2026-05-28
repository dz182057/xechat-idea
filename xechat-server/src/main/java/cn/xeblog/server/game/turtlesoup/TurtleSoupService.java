package cn.xeblog.server.game.turtlesoup;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.xeblog.commons.entity.Response;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.GameDTO;
import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.game.turtlesoup.TurtleSoupDTO;
import cn.xeblog.commons.entity.game.turtlesoup.TurtleSoupLogItemDTO;
import cn.xeblog.commons.entity.game.turtlesoup.TurtleSoupRecordDTO;
import cn.xeblog.commons.entity.game.turtlesoup.TurtleSoupStoryDTO;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.account.DbInitializer;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.cache.UserCache;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSession;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 海龟汤题库、回合状态和历史记录。
 */
@Slf4j
public final class TurtleSoupService {

    private static final int DEFAULT_GUESS_LIMIT = 3;
    private static final Map<String, RoomState> ROOM_STATES = new ConcurrentHashMap<>();

    private TurtleSoupService() {
    }

    public static List<TurtleSoupStoryDTO> listStories() {
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            List<TurtleSoupStory> rows = session.getMapper(TurtleSoupMapper.class).listActiveStories();
            List<TurtleSoupStoryDTO> result = new ArrayList<>(rows.size());
            for (TurtleSoupStory row : rows) {
                result.add(toStoryDTO(row));
            }
            return result;
        }
    }

    public static List<TurtleSoupStoryDTO> saveStories(List<TurtleSoupStoryDTO> stories) {
        List<TurtleSoupStoryDTO> normalized = normalizeStories(stories);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("海龟汤题库不能为空");
        }

        long now = System.currentTimeMillis();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            TurtleSoupMapper mapper = session.getMapper(TurtleSoupMapper.class);
            mapper.deactivateAllStories();
            for (int i = 0; i < normalized.size(); i++) {
                TurtleSoupStoryDTO dto = normalized.get(i);
                mapper.upsertStory(TurtleSoupStory.builder()
                        .title(dto.getTitle())
                        .surface(dto.getSurface())
                        .bottom(dto.getBottom())
                        .difficulty(dto.getDifficulty())
                        .tags(dto.getTags())
                        .sortOrder(i)
                        .active(1)
                        .createdAt(now)
                        .updatedAt(now)
                        .build());
            }
            session.commit();
            return normalized;
        }
    }

    public static void nextStory(User user, GameRoom room) {
        List<User> players = getRoomUsers(room);
        if (players.size() < 2) {
            throw new IllegalArgumentException("需要双方都在房间内才能出题");
        }

        RoomState old = ROOM_STATES.get(room.getId());
        boolean changingPreview = old != null && !old.confirmed && !old.finished;
        if (!changingPreview && !room.isHomeowner(user.getUsername())) {
            throw new IllegalArgumentException("仅房主可以开始下一轮海龟汤");
        }
        if (changingPreview && !user.getId().equals(old.hostId)) {
            throw new IllegalArgumentException("只有本轮主持人可以换题");
        }

        User host = changingPreview ? findPlayer(players, old.hostId) : chooseHost(room, players, old);
        if (host == null) {
            throw new IllegalArgumentException("主持人不在房间内");
        }
        User guesser = players.get(0).getId().equals(host.getId()) ? players.get(1) : players.get(0);
        List<Long> excludedStoryIds = changingPreview ? new ArrayList<>(old.previewedStoryIds) : new ArrayList<>();
        TurtleSoupStory story;
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            story = session.getMapper(TurtleSoupMapper.class).randomAvailableStory(
                    playerKey(host), playerKey(guesser), excludedStoryIds);
        }
        if (story == null) {
            throw new IllegalArgumentException("双方已做过或本轮已换过的题会被排除，当前没有可用海龟汤题目");
        }

        int guessLimit = room.getTurtleSoupGuessLimit() <= 0
                ? DEFAULT_GUESS_LIMIT
                : Math.max(1, Math.min(10, room.getTurtleSoupGuessLimit()));
        RoomState state = changingPreview ? old : new RoomState();
        state.roomId = room.getId();
        state.story = story;
        state.roundNo = changingPreview ? old.roundNo : old == null ? 1 : old.roundNo + 1;
        state.hostId = host.getId();
        state.hostKey = playerKey(host);
        state.hostName = host.getUsername();
        state.guesserId = guesser.getId();
        state.guesserKey = playerKey(guesser);
        state.guesserName = guesser.getUsername();
        state.guessLimit = guessLimit;
        state.guessUsed = 0;
        state.bestResult = null;
        state.awaitingJudgment = false;
        state.startedAt = 0;
        state.confirmed = false;
        state.finished = false;
        state.logs.clear();
        state.previewedStoryIds.add(story.getId());
        ROOM_STATES.put(room.getId(), state);

        sendPreview(state, host, true);
        sendPreview(state, guesser, false);
    }

    public static void handle(User user, GameRoom room, GameDTO body) {
        TurtleSoupDTO dto = body instanceof TurtleSoupDTO
                ? (TurtleSoupDTO) body
                : JSONUtil.toBean(JSONUtil.toJsonStr(body), TurtleSoupDTO.class);
        if (dto == null || dto.getEvent() == null) {
            return;
        }
        RoomState state = ROOM_STATES.get(room.getId());
        if (state == null || state.finished) {
            user.send(ResponseBuilder.system("当前没有进行中的海龟汤"));
            return;
        }

        switch (dto.getEvent()) {
            case CONFIRM_STORY:
                confirmStory(user, room, state);
                break;
            case QUESTION:
                question(user, room, state, dto);
                break;
            case ANSWER:
                answer(user, room, state, dto);
                break;
            case GUESS:
                guess(user, room, state, dto);
                break;
            case JUDGE:
                judge(user, room, state, dto);
                break;
            default:
                break;
        }
    }

    public static List<TurtleSoupRecordDTO> myRecords(User user) {
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            return toRecordDTOs(session.getMapper(TurtleSoupMapper.class).listRecordsByPlayer(playerKey(user)));
        }
    }

    public static List<TurtleSoupRecordDTO> allRecords() {
        try (SqlSession session = DbInitializer.factory().openSession(true)) {
            return toRecordDTOs(session.getMapper(TurtleSoupMapper.class).listAllRecords());
        }
    }

    public static void clearRoom(String roomId) {
        ROOM_STATES.remove(roomId);
    }

    public static String playerKey(User user) {
        return user.getAccountId() > 0 ? String.valueOf(user.getAccountId()) : user.getId();
    }

    private static void confirmStory(User user, GameRoom room, RoomState state) {
        if (!user.getId().equals(state.hostId)) {
            user.send(ResponseBuilder.system("只有主持人可以确认题目"));
            return;
        }
        if (state.confirmed) {
            return;
        }
        state.confirmed = true;
        state.startedAt = System.currentTimeMillis();
        User host = UserCache.get(state.hostId);
        User guesser = UserCache.get(state.guesserId);
        if (host != null) {
            sendStart(state, host, true);
        }
        if (guesser != null) {
            sendStart(state, guesser, false);
        }
    }

    private static void question(User user, GameRoom room, RoomState state, TurtleSoupDTO dto) {
        if (!ensureConfirmed(user, state)) {
            return;
        }
        if (!user.getId().equals(state.guesserId)) {
            user.send(ResponseBuilder.system("只有猜题人可以提问"));
            return;
        }
        String content = trim(dto.getContent());
        if (StrUtil.isBlank(content)) {
            return;
        }
        state.logs.add(new TurtleSoupLogItemDTO("QUESTION", user.getId(), user.getUsername(),
                content, null, null, System.currentTimeMillis()));
        TurtleSoupDTO event = baseEvent(state, TurtleSoupDTO.Event.QUESTION);
        event.setContent(content);
        sendToRoom(room, ResponseBuilder.build(user, event, MessageType.GAME));
    }

    private static void answer(User user, GameRoom room, RoomState state, TurtleSoupDTO dto) {
        if (!ensureConfirmed(user, state)) {
            return;
        }
        if (!user.getId().equals(state.hostId)) {
            user.send(ResponseBuilder.system("只有主持人可以回答问题"));
            return;
        }
        String answer = trim(dto.getAnswer());
        if (StrUtil.isBlank(answer)) {
            return;
        }
        state.logs.add(new TurtleSoupLogItemDTO("ANSWER", user.getId(), user.getUsername(),
                null, answer, null, System.currentTimeMillis()));
        TurtleSoupDTO event = baseEvent(state, TurtleSoupDTO.Event.ANSWER);
        event.setAnswer(answer);
        sendToRoom(room, ResponseBuilder.build(user, event, MessageType.GAME));
    }

    private static void guess(User user, GameRoom room, RoomState state, TurtleSoupDTO dto) {
        if (!ensureConfirmed(user, state)) {
            return;
        }
        if (!user.getId().equals(state.guesserId)) {
            user.send(ResponseBuilder.system("只有猜题人可以猜底"));
            return;
        }
        if (state.guessUsed >= state.guessLimit) {
            user.send(ResponseBuilder.system("猜底机会已用完"));
            return;
        }
        if (state.awaitingJudgment) {
            user.send(ResponseBuilder.system("上一条猜底还未判定"));
            return;
        }
        String content = trim(dto.getContent());
        if (StrUtil.isBlank(content)) {
            return;
        }
        state.guessUsed++;
        state.awaitingJudgment = true;
        state.logs.add(new TurtleSoupLogItemDTO("GUESS", user.getId(), user.getUsername(),
                content, null, null, System.currentTimeMillis()));
        TurtleSoupDTO event = baseEvent(state, TurtleSoupDTO.Event.GUESS);
        event.setContent(content);
        sendToRoom(room, ResponseBuilder.build(user, event, MessageType.GAME));
    }

    private static void judge(User user, GameRoom room, RoomState state, TurtleSoupDTO dto) {
        if (!ensureConfirmed(user, state)) {
            return;
        }
        if (!user.getId().equals(state.hostId)) {
            user.send(ResponseBuilder.system("只有主持人可以判定猜底"));
            return;
        }
        TurtleSoupDTO.GuessResult result = dto.getGuessResult();
        if (result == null) {
            return;
        }
        if (!state.awaitingJudgment) {
            user.send(ResponseBuilder.system("当前没有待判定的猜底"));
            return;
        }
        state.awaitingJudgment = false;
        state.bestResult = better(state.bestResult, result);
        state.logs.add(new TurtleSoupLogItemDTO("JUDGE", user.getId(), user.getUsername(),
                null, null, result.name(), System.currentTimeMillis()));

        TurtleSoupDTO event = baseEvent(state, TurtleSoupDTO.Event.JUDGE);
        event.setGuessResult(result);
        sendToRoom(room, ResponseBuilder.build(user, event, MessageType.GAME));

        if (result == TurtleSoupDTO.GuessResult.CORRECT || state.guessUsed >= state.guessLimit) {
            finish(room, state);
        }
    }

    private static void finish(GameRoom room, RoomState state) {
        state.finished = true;
        TurtleSoupDTO.GuessResult finalResult = state.bestResult == null
                ? TurtleSoupDTO.GuessResult.WRONG
                : state.bestResult;
        long endedAt = System.currentTimeMillis();
        try (SqlSession session = DbInitializer.factory().openSession(false)) {
            session.getMapper(TurtleSoupMapper.class).insertRecord(TurtleSoupRecord.builder()
                    .roomId(state.roomId)
                    .storyId(state.story.getId())
                    .roundNo(state.roundNo)
                    .hostKey(state.hostKey)
                    .hostName(state.hostName)
                    .guesserKey(state.guesserKey)
                    .guesserName(state.guesserName)
                    .guessLimit(state.guessLimit)
                    .guessUsed(state.guessUsed)
                    .result(finalResult.name())
                    .qaJson(JSONUtil.toJsonStr(state.logs))
                    .startedAt(state.startedAt)
                    .endedAt(endedAt)
                    .build());
            session.commit();
        } catch (Exception e) {
            log.error("保存海龟汤历史记录失败", e);
        }

        TurtleSoupDTO reveal = baseEvent(state, TurtleSoupDTO.Event.REVEAL);
        reveal.setBottom(state.story.getBottom());
        reveal.setGuessResult(finalResult);
        reveal.setFinished(true);
        sendToRoom(room, ResponseBuilder.build(null, reveal, MessageType.GAME));
    }

    private static boolean ensureConfirmed(User user, RoomState state) {
        if (state.confirmed) {
            return true;
        }
        user.send(ResponseBuilder.system("主持人确认题目后才能正式开始"));
        return false;
    }

    private static User chooseHost(GameRoom room, List<User> players, RoomState old) {
        if (old != null) {
            return players.get(0).getId().equals(old.hostId) ? players.get(1) : players.get(0);
        }
        String mode = room.getTurtleSoupHostMode();
        if ("GUEST".equalsIgnoreCase(mode)) {
            return players.get(0).getId().equals(room.getHomeowner().getId()) ? players.get(1) : players.get(0);
        }
        if ("RANDOM".equalsIgnoreCase(mode)) {
            return players.get((int) (Math.random() * players.size()));
        }
        return room.getHomeowner();
    }

    private static void sendPreview(RoomState state, User player, boolean host) {
        TurtleSoupDTO dto = baseEvent(state, TurtleSoupDTO.Event.PREVIEW_STORY);
        dto.setBottom(null);
        if (!host) {
            dto.setTitle(null);
            dto.setSurface(null);
            dto.setDifficulty(null);
            dto.setTags(null);
        }
        player.send(ResponseBuilder.build(null, dto, MessageType.GAME));
    }

    private static void sendStart(RoomState state, User player, boolean host) {
        TurtleSoupDTO dto = baseEvent(state, TurtleSoupDTO.Event.START_ROUND);
        dto.setBottom(host ? state.story.getBottom() : null);
        player.send(ResponseBuilder.build(null, dto, MessageType.GAME));
    }

    private static TurtleSoupDTO baseEvent(RoomState state, TurtleSoupDTO.Event event) {
        TurtleSoupDTO dto = new TurtleSoupDTO(state.roomId);
        dto.setEvent(event);
        dto.setStoryId(state.story.getId());
        dto.setRoundNo(state.roundNo);
        dto.setHostId(state.hostId);
        dto.setHostName(state.hostName);
        dto.setGuesserId(state.guesserId);
        dto.setGuesserName(state.guesserName);
        dto.setTitle(state.story.getTitle());
        dto.setSurface(state.story.getSurface());
        dto.setDifficulty(state.story.getDifficulty());
        dto.setTags(state.story.getTags());
        dto.setGuessLimit(state.guessLimit);
        dto.setGuessUsed(state.guessUsed);
        dto.setFinished(state.finished);
        return dto;
    }

    private static void sendToRoom(GameRoom room, Response response) {
        room.getUsers().forEach((k, v) -> {
            User player = UserCache.get(v.getId());
            if (player != null) {
                player.send(response);
            }
        });
    }

    private static TurtleSoupDTO.GuessResult better(TurtleSoupDTO.GuessResult oldResult,
                                                    TurtleSoupDTO.GuessResult newResult) {
        if (oldResult == null) {
            return newResult;
        }
        return rank(newResult) > rank(oldResult) ? newResult : oldResult;
    }

    private static int rank(TurtleSoupDTO.GuessResult result) {
        if (result == TurtleSoupDTO.GuessResult.CORRECT) {
            return 3;
        }
        if (result == TurtleSoupDTO.GuessResult.PARTIAL) {
            return 2;
        }
        return 1;
    }

    private static List<User> getRoomUsers(GameRoom room) {
        List<User> players = new ArrayList<>();
        room.getUsers().forEach((k, v) -> {
            User user = UserCache.get(v.getId());
            if (user != null) {
                players.add(user);
            }
        });
        return players;
    }

    private static User findPlayer(List<User> players, String userId) {
        for (User player : players) {
            if (player.getId().equals(userId)) {
                return player;
            }
        }
        return null;
    }

    private static TurtleSoupStoryDTO toStoryDTO(TurtleSoupStory row) {
        return new TurtleSoupStoryDTO(row.getId(), row.getTitle(), row.getSurface(), row.getBottom(),
                row.getDifficulty(), row.getTags());
    }

    private static List<TurtleSoupStoryDTO> normalizeStories(List<TurtleSoupStoryDTO> stories) {
        Map<String, TurtleSoupStoryDTO> map = new LinkedHashMap<>();
        if (stories == null) {
            return new ArrayList<>();
        }
        for (TurtleSoupStoryDTO item : stories) {
            if (item == null || StrUtil.isBlank(item.getSurface()) || StrUtil.isBlank(item.getBottom())) {
                continue;
            }
            String surface = item.getSurface().trim();
            if (map.containsKey(surface)) {
                continue;
            }
            String title = StrUtil.isBlank(item.getTitle()) ? defaultTitle(surface) : item.getTitle().trim();
            String bottom = item.getBottom().trim();
            String difficulty = StrUtil.isBlank(item.getDifficulty()) ? null : item.getDifficulty().trim();
            String tags = StrUtil.isBlank(item.getTags()) ? null : item.getTags().trim();
            map.put(surface, new TurtleSoupStoryDTO(0, title, surface, bottom, difficulty, tags));
        }
        return new ArrayList<>(map.values());
    }

    private static String defaultTitle(String surface) {
        String compact = surface.replaceAll("\\s+", "");
        return compact.length() > 12 ? compact.substring(0, 12) + "..." : compact;
    }

    private static List<TurtleSoupRecordDTO> toRecordDTOs(List<TurtleSoupRecord> rows) {
        List<TurtleSoupRecordDTO> result = new ArrayList<>(rows.size());
        for (TurtleSoupRecord row : rows) {
            List<TurtleSoupLogItemDTO> logs = JSONUtil.parseArray(row.getQaJson())
                    .toList(TurtleSoupLogItemDTO.class);
            result.add(new TurtleSoupRecordDTO(
                    row.getId(),
                    row.getRoomId(),
                    row.getStoryId(),
                    row.getRoundNo(),
                    row.getTitle(),
                    row.getSurface(),
                    row.getBottom(),
                    row.getDifficulty(),
                    row.getTags(),
                    row.getHostKey(),
                    row.getHostName(),
                    row.getGuesserKey(),
                    row.getGuesserName(),
                    row.getGuessLimit(),
                    row.getGuessUsed(),
                    row.getResult(),
                    logs,
                    row.getStartedAt(),
                    row.getEndedAt()));
        }
        return result;
    }

    private static String trim(String text) {
        return text == null ? "" : text.trim();
    }

    private static class RoomState {
        private String roomId;
        private TurtleSoupStory story;
        private int roundNo;
        private String hostId;
        private String hostKey;
        private String hostName;
        private String guesserId;
        private String guesserKey;
        private String guesserName;
        private int guessLimit;
        private int guessUsed;
        private TurtleSoupDTO.GuessResult bestResult;
        private boolean awaitingJudgment;
        private long startedAt;
        private boolean confirmed;
        private boolean finished;
        private final List<Long> previewedStoryIds = new ArrayList<>();
        private final List<TurtleSoupLogItemDTO> logs = new ArrayList<>();
    }

}
