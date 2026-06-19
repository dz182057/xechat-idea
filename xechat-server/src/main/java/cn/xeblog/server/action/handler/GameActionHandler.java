package cn.xeblog.server.action.handler;

import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.game.dogbattle.DogBattleDTO;
import cn.xeblog.commons.entity.game.drawguess.DrawGuessDTO;
import cn.xeblog.commons.entity.game.gobang.GobangDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.commons.enums.Game;
import cn.xeblog.server.annotation.DoAction;
import cn.xeblog.server.builder.ResponseBuilder;
import cn.xeblog.commons.entity.game.GameDTO;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.server.cache.UserCache;
import cn.xeblog.server.game.dogbattle.DogBattleService;
import cn.xeblog.server.game.dograce.DogRaceService;
import cn.xeblog.server.game.drawguess.DrawGuessRewardService;
import cn.xeblog.server.game.gobang.GobangPetItemService;
import cn.xeblog.server.game.minesweeper.MinesweeperService;
import cn.xeblog.server.game.turtlesoup.TurtleSoupService;
import cn.xeblog.server.pet.PetGameItemDeclarationService;
import cn.xeblog.server.pet.PetGameItemRules;

/**
 * @author anlingyi
 * @date 2020/8/14
 */
@DoAction(Action.GAME)
public class GameActionHandler extends AbstractGameActionHandler<GameDTO> {

    private static final String SLOT_GAMEPLAY = "gameplay";

    @Override
    protected void process(User user, GameRoom gameRoom, GameDTO body) {
        if (!gameRoom.isPlayerConnection(user)) {
            return;
        }
        if (gameRoom.getGame() == Game.TURTLE_SOUP) {
            TurtleSoupService.handle(user, gameRoom, body);
            return;
        }
        if (gameRoom.getGame() == Game.MINESWEEPER && MinesweeperService.handleRoom(user, gameRoom, body)) {
            return;
        }
        if (gameRoom.getGame() == Game.DOG_RACE) {
            DogRaceService.handle(user, gameRoom, body);
            return;
        }
        if (gameRoom.getGame() == Game.DOG_BATTLE) {
            DogBattleDTO result = DogBattleService.handleInput(user, gameRoom, (DogBattleDTO) body);
            if (result != null) {
                gameRoom.getUsers().forEach((k, v) -> {
                    User player = UserCache.get(v.getChannelId());
                    if (player != null) {
                        player.send(ResponseBuilder.build(user, result, MessageType.GAME));
                    }
                });
            }
            return;
        }

        if (gameRoom.getGame() == Game.DRAW_GUESS) {
            handleDrawGuessPetItems(gameRoom, body);
        }
        if (gameRoom.getGame() == Game.GOBANG && body instanceof GobangDTO) {
            GobangPetItemService.handleMove(user, gameRoom, (GobangDTO) body);
        }

        gameRoom.getUsers().forEach((k, v) -> {
            if (v.isConnection(user)) {
                return;
            }

            User player = UserCache.get(v.getChannelId());
            if (player != null) {
                player.send(ResponseBuilder.build(user, body, MessageType.GAME));
            }
        });
        if (body instanceof GobangDTO && ((GobangDTO) body).getPetItemNotice() != null) {
            user.send(ResponseBuilder.build(user, body, MessageType.GAME));
        }
    }

    private void handleDrawGuessPetItems(GameRoom room, GameDTO body) {
        if (!(body instanceof DrawGuessDTO)) {
            return;
        }
        DrawGuessDTO dto = (DrawGuessDTO) body;
        if (dto.getEvent() == DrawGuessDTO.Event.START_ROUND) {
            DrawGuessRewardService.handleStart(room);
            consumeDrawGuessGuesserItems(room, resolveDrawerKey(room, dto));
            return;
        }
        if (dto.getEvent() == DrawGuessDTO.Event.CORRECT) {
            refundRemainingDrawGuessItems(room);
            DrawGuessRewardService.handleCorrect(room, dto);
        }
    }

    private void consumeDrawGuessGuesserItems(GameRoom room, String drawerKey) {
        if (drawerKey == null) {
            return;
        }
        room.getUsers().forEach((playerKey, player) -> {
            if (!drawerKey.equals(playerKey)) {
                settleDrawGuessPlayItem(room, player, "consumed");
            }
        });
    }

    private void refundRemainingDrawGuessItems(GameRoom room) {
        room.getUsers().forEach((playerKey, player) -> settleDrawGuessPlayItem(room, player, "refunded"));
    }

    private void settleDrawGuessPlayItem(GameRoom room, GameRoom.Player player, String status) {
        if (player == null || !PetGameItemRules.isPlayItem(Game.DRAW_GUESS, player.getPetPlayItemId())) {
            return;
        }
        String itemId = player.getPetPlayItemId();
        if ("consumed".equals(status)) {
            PetGameItemDeclarationService.settleConsumed(room, player.getId(), itemId, SLOT_GAMEPLAY);
        } else {
            PetGameItemDeclarationService.settleRefunded(room, player.getId(), itemId, SLOT_GAMEPLAY);
        }
        player.setPetPlayItemId(null);
    }

    private String resolveDrawerKey(GameRoom room, DrawGuessDTO dto) {
        String drawerId = trimToNull(dto.getDrawerId());
        if (drawerId != null && room.getUsers().containsKey(drawerId)) {
            return drawerId;
        }
        String drawerName = trimToNull(dto.getDrawerName());
        if (drawerName == null) {
            return null;
        }
        for (GameRoom.Player player : room.getUsers().values()) {
            if (drawerName.equals(player.getUsername()) || drawerName.equals(player.getNickname())) {
                return player.getId();
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

}
