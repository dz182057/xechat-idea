package cn.xeblog.server.action.handler;

import cn.xeblog.commons.entity.Response;
import cn.xeblog.commons.entity.game.CreateGameRoomDTO;
import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.server.cache.GameRoomCache;
import cn.xeblog.server.pet.PetGameItemDeclarationService;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @author anlingyi
 * @date 2022/5/25 10:41 上午
 */
@Slf4j
@DoAction(Action.CREATE_GAME_ROOM)
public class GameRoomCreateActionHandler extends AbstractActionHandler<CreateGameRoomDTO> {

    @Override
    protected void process(User user, CreateGameRoomDTO body) {
        if (user.isGuest() || user.getAccountId() <= 0L) {
            user.send(ResponseBuilder.system("游客不支持玩游戏，请登录账号后再创建房间"));
            return;
        }
        String roomId = generateRoomId();
        GameRoom gameRoom = GameRoomCache.seize(roomId);
        Response<GameRoom> response = ResponseBuilder.build(null, gameRoom, MessageType.GAME_ROOM_CREATED);
        if (gameRoom == null) {
            log.debug("游戏房间创建失败 -> roomId: {}", roomId);
            user.send(response);
            return;
        }

        gameRoom.setGame(body.getGame());
        gameRoom.setNums(body.getNums());
        gameRoom.setGameMode(body.getGameMode());
        gameRoom.setDrawGuessRoundCount(resolveDrawGuessRoundCount(body));
        gameRoom.setDrawGuessTimeLimitSeconds(resolveDrawGuessTimeLimitSeconds(body));
        gameRoom.setQuickQuizQuestionCount(body.getQuickQuizQuestionCount());
        gameRoom.setQuickQuizTimeLimitSeconds(body.getQuickQuizTimeLimitSeconds());
        gameRoom.setQuickQuizEntryFee(body.getQuickQuizEntryFee());
        gameRoom.setTacitQuizQuestionCount(resolveTacitQuizQuestionCount(body));
        gameRoom.setTurtleSoupGuessLimit(body.getTurtleSoupGuessLimit());
        gameRoom.setTurtleSoupHostMode(body.getTurtleSoupHostMode());
        gameRoom.setDogRaceMode(body.getDogRaceMode());
        gameRoom.setDogBattleRoundCount(resolveDogBattleRoundCount(body));
        gameRoom.setDogBattleAllowSkill(resolveDogBattleAllowSkill(body));
        gameRoom.setHomeowner(user);
        if (!GameRoomCache.joinRoom(roomId, user)) {
            GameRoomCache.removeRoom(roomId);
            user.send(ResponseBuilder.build(null, null, MessageType.GAME_ROOM_CREATED));
            user.send(ResponseBuilder.system("你已经在游戏房间中，请先退出当前房间"));
            log.debug("游戏房间创建失败，用户已在房间中 -> roomId: {}, user: {}", roomId, user.getUsername());
            return;
        }
        PetGameItemDeclarationService.applyDeclarationForUser(
                user,
                gameRoom,
                body.getPetItems());
        user.send(response);
        log.debug("游戏房间创建成功 -> {}", gameRoom);
    }

    private static String generateRoomId() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmssSSS"));
    }

    public static int resolveDogBattleRoundCount(CreateGameRoomDTO body) {
        int roundCount = body.getDogBattleRoundCount();
        return roundCount == 1 || roundCount == 3 || roundCount == 5 || roundCount == 7 ? roundCount : 3;
    }

    public static boolean resolveDogBattleAllowSkill(CreateGameRoomDTO body) {
        return body.getDogBattleAllowSkill() == null || body.getDogBattleAllowSkill();
    }

    public static int resolveDrawGuessRoundCount(CreateGameRoomDTO body) {
        int roundCount = body.getDrawGuessRoundCount();
        return roundCount > 0 ? Math.min(roundCount, 10) : 1;
    }

    public static int resolveDrawGuessTimeLimitSeconds(CreateGameRoomDTO body) {
        int seconds = body.getDrawGuessTimeLimitSeconds();
        return seconds == 60 || seconds == 90 || seconds == 120 ? seconds : 90;
    }

    public static int resolveTacitQuizQuestionCount(CreateGameRoomDTO body) {
        return body.getTacitQuizQuestionCount() > 0 ? body.getTacitQuizQuestionCount() : body.getQuickQuizQuestionCount();
    }

}
