package cn.xeblog.server.game.minesweeper;

import cn.xeblog.commons.entity.Response;
import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.entity.game.minesweeper.MinesweeperDTO;
import cn.xeblog.commons.enums.Game;
import cn.xeblog.commons.enums.MessageType;
import cn.xeblog.commons.game.minesweeper.NoGuessMinesweeper;
import cn.xeblog.server.cache.UserCache;
import cn.xeblog.server.pet.MiniGameRewards;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MinesweeperServiceTest {

    private final User user = user();

    @After
    public void tearDown() {
        MinesweeperDTO reset = new MinesweeperDTO("single");
        reset.setEvent(MinesweeperDTO.Event.SERVER_START_REQUEST);
        MinesweeperService.handleSingle(user, reset);
        MinesweeperService.resetGameItemSettler();
        MinesweeperService.resetBoardGenerator();
        MinesweeperService.resetMiniGameRewards();
        MinesweeperService.resetNowSupplier();
        drain(user);
    }

    @Test
    public void singleActionShouldRebuildWhenRequestedSizeChanges() {
        MinesweeperDTO large = actionRequest(16, 30, 99, 4, 4);
        MinesweeperService.handleSingle(user, large);
        MinesweeperDTO largeResponse = gameBody(readResponse(user));
        Assert.assertEquals(Integer.valueOf(16), largeResponse.getRows());
        Assert.assertEquals(Integer.valueOf(30), largeResponse.getCols());
        Assert.assertEquals(Integer.valueOf(99), largeResponse.getMines());

        MinesweeperDTO medium = actionRequest(16, 16, 40, 4, 4);
        MinesweeperService.handleSingle(user, medium);
        MinesweeperDTO mediumResponse = gameBody(readResponse(user));

        Assert.assertEquals(Integer.valueOf(16), mediumResponse.getRows());
        Assert.assertEquals(Integer.valueOf(16), mediumResponse.getCols());
        Assert.assertEquals(Integer.valueOf(40), mediumResponse.getMines());
    }

    @Test
    public void singleMineShieldShouldPreventMineFailureAndMarkSlotUsed() {
        MinesweeperService.setBoardGeneratorForTest((rows, cols, mines, firstClick) ->
                NoGuessMinesweeper.Board.fromMines(rows, cols,
                        Collections.singletonList(new NoGuessMinesweeper.Point(1, 0))));

        MinesweeperDTO openMine = actionRequest(5, 5, 1, 1, 0);
        openMine.setPetItemId("item_mine_shield");
        openMine.setPetItemSlotIndex(1);
        MinesweeperService.handleSingle(user, openMine);
        MinesweeperDTO response = gameBody(readResponse(user));

        Assert.assertEquals(MinesweeperDTO.Phase.playing, response.getPhase());
        Assert.assertEquals(Boolean.FALSE, response.getHitMine());
        Assert.assertEquals("item_mine_shield", response.getPetItemId());
        Assert.assertEquals(Integer.valueOf(1), response.getPetItemSlotIndex());
        Assert.assertEquals(Boolean.TRUE, response.getPetItemConsumed());
        Assert.assertTrue(Boolean.TRUE.equals(cell(response, 1, 0).getSharedMarked()));
    }

    @Test
    public void singleStartRequestShouldClearPreviousBoardMarks() {
        MinesweeperService.setBoardGeneratorForTest((rows, cols, mines, firstClick) ->
                NoGuessMinesweeper.Board.fromMines(rows, cols,
                        Collections.singletonList(new NoGuessMinesweeper.Point(1, 0))));

        MinesweeperDTO openMine = actionRequest(5, 5, 1, 1, 0);
        openMine.setPetItemId("item_mine_shield");
        openMine.setPetItemSlotIndex(0);
        MinesweeperService.handleSingle(user, openMine);
        MinesweeperDTO shielded = gameBody(readResponse(user));
        Assert.assertTrue(Boolean.TRUE.equals(cell(shielded, 1, 0).getSharedMarked()));

        MinesweeperDTO restart = new MinesweeperDTO("single");
        restart.setEvent(MinesweeperDTO.Event.SERVER_START_REQUEST);
        restart.setRows(5);
        restart.setCols(5);
        restart.setMines(1);
        MinesweeperService.handleSingle(user, restart);
        drain(user);

        MinesweeperDTO safeOpen = actionRequest(5, 5, 1, 0, 0);
        MinesweeperService.handleSingle(user, safeOpen);
        MinesweeperDTO restarted = gameBody(readResponse(user));

        Assert.assertFalse(Boolean.TRUE.equals(cell(restarted, 1, 0).getSharedMarked()));
    }

    @Test
    public void roomActionShouldRebuildWhenRequestedSizeChangesAfterTurnMovedToOpponent() {
        User homeowner = user("home-channel", 9101L, "home");
        User opponent = user("opponent-channel", 9102L, "opponent");
        UserCache.add(homeowner.getId(), homeowner);
        UserCache.add(opponent.getId(), opponent);
        GameRoom room = room(homeowner, opponent);
        MinesweeperService.clearRoom(room.getId());
        try {
            MinesweeperDTO large = actionRequest(room.getId(), 16, 30, 99, 4, 4);
            large.setActorKey(homeowner.getIdentityKey());
            MinesweeperService.handleRoom(homeowner, room, large);
            drain(homeowner);
            drain(opponent);

            MinesweeperDTO medium = actionRequest(room.getId(), 16, 16, 40, 4, 4);
            medium.setActorKey(homeowner.getIdentityKey());
            MinesweeperService.handleRoom(homeowner, room, medium);
            MinesweeperDTO mediumResponse = gameBody(readResponse(homeowner));

            Assert.assertEquals(Integer.valueOf(16), mediumResponse.getRows());
            Assert.assertEquals(Integer.valueOf(16), mediumResponse.getCols());
            Assert.assertEquals(Integer.valueOf(40), mediumResponse.getMines());
            Assert.assertEquals(opponent.getIdentityKey(), mediumResponse.getNextTurnPlayerKey());
        } finally {
            MinesweeperService.clearRoom(room.getId());
            UserCache.remove(homeowner.getId());
            UserCache.remove(opponent.getId());
        }
    }

    @Test
    public void roomApprovedRestartShouldClearTurnStateBeforeNextAction() {
        User homeowner = user("home-channel-restart", 9201L, "home-restart");
        User opponent = user("opponent-channel-restart", 9202L, "opponent-restart");
        UserCache.add(homeowner.getId(), homeowner);
        UserCache.add(opponent.getId(), opponent);
        GameRoom room = room("minesweeper-room-restart-test", homeowner, opponent);
        MinesweeperService.clearRoom(room.getId());
        try {
            MinesweeperDTO firstAction = actionRequest(room.getId(), 9, 9, 10, 4, 4);
            firstAction.setActorKey(homeowner.getIdentityKey());
            MinesweeperService.handleRoom(homeowner, room, firstAction);
            drain(homeowner);
            drain(opponent);

            MinesweeperDTO restart = new MinesweeperDTO(room.getId());
            restart.setEvent(MinesweeperDTO.Event.RESTART_RESPONSE);
            restart.setRestartApproved(true);
            restart.setActorKey(opponent.getIdentityKey());
            Assert.assertFalse(MinesweeperService.handleRoom(opponent, room, restart));

            MinesweeperDTO restartedAction = actionRequest(room.getId(), 9, 9, 10, 4, 4);
            restartedAction.setActorKey(homeowner.getIdentityKey());
            MinesweeperService.handleRoom(homeowner, room, restartedAction);
            MinesweeperDTO restartedResponse = gameBody(readResponse(homeowner));

            Assert.assertEquals(Integer.valueOf(9), restartedResponse.getRows());
            Assert.assertEquals(Integer.valueOf(9), restartedResponse.getCols());
            Assert.assertEquals(Integer.valueOf(10), restartedResponse.getMines());
            Assert.assertEquals(opponent.getIdentityKey(), restartedResponse.getNextTurnPlayerKey());
        } finally {
            MinesweeperService.clearRoom(room.getId());
            UserCache.remove(homeowner.getId());
            UserCache.remove(opponent.getId());
        }
    }

    @Test
    public void roomActionShouldBroadcastLastActionPositionAndActor() {
        User homeowner = user("home-channel-highlight", 9301L, "home-highlight");
        User opponent = user("opponent-channel-highlight", 9302L, "opponent-highlight");
        UserCache.add(homeowner.getId(), homeowner);
        UserCache.add(opponent.getId(), opponent);
        GameRoom room = room("minesweeper-room-highlight-test", homeowner, opponent);
        MinesweeperService.clearRoom(room.getId());
        try {
            MinesweeperDTO action = actionRequest(room.getId(), 9, 9, 10, 3, 5);
            action.setActorKey(homeowner.getIdentityKey());
            MinesweeperService.handleRoom(homeowner, room, action);
            MinesweeperDTO response = gameBody(readResponse(opponent));

            Assert.assertEquals(Integer.valueOf(3), response.getX());
            Assert.assertEquals(Integer.valueOf(5), response.getY());
            Assert.assertEquals(homeowner.getIdentityKey(), response.getActorKey());
        } finally {
            MinesweeperService.clearRoom(room.getId());
            UserCache.remove(homeowner.getId());
            UserCache.remove(opponent.getId());
        }
    }

    @Test
    public void roomShieldPlayItemShouldConsumeAndPreventFirstMineFailure() {
        User homeowner = user("home-channel-shield", 9401L, "home-shield");
        User opponent = user("opponent-channel-shield", 9402L, "opponent-shield");
        UserCache.add(homeowner.getId(), homeowner);
        UserCache.add(opponent.getId(), opponent);
        GameRoom room = room("minesweeper-room-shield-test", homeowner, opponent);
        room.getUsers().get(opponent.getIdentityKey()).setPetPlayItemId("item_mine_shield");
        MinesweeperService.clearRoom(room.getId());
        final int[] itemSettleCalls = {0};
        MinesweeperService.setBoardGeneratorForTest((rows, cols, mines, firstClick) ->
                NoGuessMinesweeper.Board.fromMines(rows, cols,
                        Collections.singletonList(new NoGuessMinesweeper.Point(1, 0))));
        MinesweeperService.setGameItemSettlerForTest((targetRoom, playerKey, itemId, slot, status) -> {
            itemSettleCalls[0]++;
            Assert.assertEquals(room.getId(), targetRoom.getId());
            Assert.assertEquals(opponent.getIdentityKey(), playerKey);
            Assert.assertEquals("item_mine_shield", itemId);
            Assert.assertEquals("gameplay", slot);
            Assert.assertEquals("consumed", status);
        });
        try {
            MinesweeperDTO safeOpen = actionRequest(room.getId(), 5, 5, 1, 0, 0);
            safeOpen.setActorKey(homeowner.getIdentityKey());
            MinesweeperService.handleRoom(homeowner, room, safeOpen);
            drain(homeowner);
            drain(opponent);

            MinesweeperDTO mineOpen = actionRequest(room.getId(), 5, 5, 1, 1, 0);
            mineOpen.setActorKey(opponent.getIdentityKey());
            MinesweeperService.handleRoom(opponent, room, mineOpen);
            MinesweeperDTO response = gameBody(readResponse(homeowner));

            Assert.assertEquals(MinesweeperDTO.Phase.playing, response.getPhase());
            Assert.assertEquals(Boolean.FALSE, response.getHitMine());
            Assert.assertEquals(1, itemSettleCalls[0]);
        } finally {
            MinesweeperService.clearRoom(room.getId());
            UserCache.remove(homeowner.getId());
            UserCache.remove(opponent.getId());
        }
    }

    @Test
    public void roomShieldPlayItemShouldRefundWhenGameEndsWithoutTriggering() {
        User homeowner = user("home-channel-shield-refund", 9501L, "home-shield-refund");
        User opponent = user("opponent-channel-shield-refund", 9502L, "opponent-shield-refund");
        UserCache.add(homeowner.getId(), homeowner);
        UserCache.add(opponent.getId(), opponent);
        GameRoom room = room("minesweeper-room-shield-refund-test", homeowner, opponent);
        room.getUsers().get(homeowner.getIdentityKey()).setPetPlayItemId("item_mine_shield");
        MinesweeperService.clearRoom(room.getId());
        final int[] itemSettleCalls = {0};
        MinesweeperService.setBoardGeneratorForTest((rows, cols, mines, firstClick) ->
                NoGuessMinesweeper.Board.fromMines(rows, cols,
                        Collections.singletonList(new NoGuessMinesweeper.Point(4, 4))));
        MinesweeperService.setGameItemSettlerForTest((targetRoom, playerKey, itemId, slot, status) -> {
            itemSettleCalls[0]++;
            Assert.assertEquals(room.getId(), targetRoom.getId());
            Assert.assertEquals(homeowner.getIdentityKey(), playerKey);
            Assert.assertEquals("item_mine_shield", itemId);
            Assert.assertEquals("gameplay", slot);
            Assert.assertEquals("refunded", status);
        });
        try {
            MinesweeperDTO safeOpen = actionRequest(room.getId(), 5, 5, 1, 0, 0);
            safeOpen.setActorKey(homeowner.getIdentityKey());
            MinesweeperService.handleRoom(homeowner, room, safeOpen);
            MinesweeperDTO response = gameBody(readResponse(opponent));

            Assert.assertEquals(MinesweeperDTO.Phase.won, response.getPhase());
            Assert.assertEquals(1, itemSettleCalls[0]);
        } finally {
            MinesweeperService.clearRoom(room.getId());
            UserCache.remove(homeowner.getId());
            UserCache.remove(opponent.getId());
        }
    }

    @Test
    public void roomMineMarkPlayItemShouldConsumeAndMarkOneRealMine() {
        User homeowner = user("home-channel-mine-mark", 9601L, "home-mine-mark");
        User opponent = user("opponent-channel-mine-mark", 9602L, "opponent-mine-mark");
        UserCache.add(homeowner.getId(), homeowner);
        UserCache.add(opponent.getId(), opponent);
        GameRoom room = room("minesweeper-room-mine-mark-test", homeowner, opponent);
        room.getUsers().get(homeowner.getIdentityKey()).setPetPlayItemId("item_mine_mark");
        MinesweeperService.clearRoom(room.getId());
        final int[] itemSettleCalls = {0};
        MinesweeperService.setBoardGeneratorForTest((rows, cols, mines, firstClick) ->
                NoGuessMinesweeper.Board.fromMines(rows, cols,
                        Collections.singletonList(new NoGuessMinesweeper.Point(1, 0))));
        MinesweeperService.setGameItemSettlerForTest((targetRoom, playerKey, itemId, slot, status) -> {
            itemSettleCalls[0]++;
            Assert.assertEquals(room.getId(), targetRoom.getId());
            Assert.assertEquals(homeowner.getIdentityKey(), playerKey);
            Assert.assertEquals("item_mine_mark", itemId);
            Assert.assertEquals("gameplay", slot);
            Assert.assertEquals("consumed", status);
        });
        try {
            MinesweeperDTO safeOpen = actionRequest(room.getId(), 5, 5, 1, 0, 0);
            safeOpen.setActorKey(homeowner.getIdentityKey());
            MinesweeperService.handleRoom(homeowner, room, safeOpen);
            drain(homeowner);
            drain(opponent);

            MinesweeperDTO itemUse = itemUseRequest(room.getId(), "item_mine_mark");
            itemUse.setActorKey(homeowner.getIdentityKey());
            MinesweeperService.handleRoom(homeowner, room, itemUse);
            MinesweeperDTO response = gameBody(readResponse(opponent));

            Assert.assertEquals(MinesweeperDTO.Phase.playing, response.getPhase());
            Assert.assertEquals(MinesweeperDTO.Event.ITEM_EFFECT, response.getEvent());
            Assert.assertEquals(1, itemSettleCalls[0]);
            Assert.assertTrue(response.getCells().stream().anyMatch(cell ->
                    cell.getX() == 1 && cell.getY() == 0 && Boolean.TRUE.equals(cell.getSharedMarked())));
        } finally {
            MinesweeperService.clearRoom(room.getId());
            UserCache.remove(homeowner.getId());
            UserCache.remove(opponent.getId());
        }
    }

    @Test
    public void roomMineMarkPlayItemShouldRefundWhenOwnerNeverTriggersIt() {
        User homeowner = user("home-channel-mine-mark-refund", 9701L, "home-mine-mark-refund");
        User opponent = user("opponent-channel-mine-mark-refund", 9702L, "opponent-mine-mark-refund");
        UserCache.add(homeowner.getId(), homeowner);
        UserCache.add(opponent.getId(), opponent);
        GameRoom room = room("minesweeper-room-mine-mark-refund-test", homeowner, opponent);
        room.getUsers().get(opponent.getIdentityKey()).setPetPlayItemId("item_mine_mark");
        MinesweeperService.clearRoom(room.getId());
        final int[] itemSettleCalls = {0};
        MinesweeperService.setBoardGeneratorForTest((rows, cols, mines, firstClick) ->
                NoGuessMinesweeper.Board.fromMines(rows, cols,
                        Collections.singletonList(new NoGuessMinesweeper.Point(4, 4))));
        MinesweeperService.setGameItemSettlerForTest((targetRoom, playerKey, itemId, slot, status) -> {
            itemSettleCalls[0]++;
            Assert.assertEquals(room.getId(), targetRoom.getId());
            Assert.assertEquals(opponent.getIdentityKey(), playerKey);
            Assert.assertEquals("item_mine_mark", itemId);
            Assert.assertEquals("gameplay", slot);
            Assert.assertEquals("refunded", status);
        });
        try {
            MinesweeperDTO safeOpen = actionRequest(room.getId(), 5, 5, 1, 0, 0);
            safeOpen.setActorKey(homeowner.getIdentityKey());
            MinesweeperService.handleRoom(homeowner, room, safeOpen);
            MinesweeperDTO response = gameBody(readResponse(opponent));

            Assert.assertEquals(MinesweeperDTO.Phase.won, response.getPhase());
            Assert.assertEquals(1, itemSettleCalls[0]);
        } finally {
            MinesweeperService.clearRoom(room.getId());
            UserCache.remove(homeowner.getId());
            UserCache.remove(opponent.getId());
        }
    }

    @Test
    public void roomNewMinesweeperPlayItemWithoutRuntimeEffectShouldRefundWhenGameEnds() {
        User homeowner = user("home-channel-mine-counter-refund", 9731L, "home-mine-counter-refund");
        User opponent = user("opponent-channel-mine-counter-refund", 9732L, "opponent-mine-counter-refund");
        UserCache.add(homeowner.getId(), homeowner);
        UserCache.add(opponent.getId(), opponent);
        GameRoom room = room("minesweeper-room-mine-counter-refund-test", homeowner, opponent);
        room.getUsers().get(opponent.getIdentityKey()).setPetPlayItemId("item_mine_counter");
        MinesweeperService.clearRoom(room.getId());
        final int[] itemSettleCalls = {0};
        MinesweeperService.setBoardGeneratorForTest((rows, cols, mines, firstClick) ->
                NoGuessMinesweeper.Board.fromMines(rows, cols,
                        Collections.singletonList(new NoGuessMinesweeper.Point(4, 4))));
        MinesweeperService.setGameItemSettlerForTest((targetRoom, playerKey, itemId, slot, status) -> {
            itemSettleCalls[0]++;
            Assert.assertEquals(room.getId(), targetRoom.getId());
            Assert.assertEquals(opponent.getIdentityKey(), playerKey);
            Assert.assertEquals("item_mine_counter", itemId);
            Assert.assertEquals("gameplay", slot);
            Assert.assertEquals("refunded", status);
        });
        try {
            MinesweeperDTO safeOpen = actionRequest(room.getId(), 5, 5, 1, 0, 0);
            safeOpen.setActorKey(homeowner.getIdentityKey());
            MinesweeperService.handleRoom(homeowner, room, safeOpen);
            MinesweeperDTO response = gameBody(readResponse(opponent));

            Assert.assertEquals(MinesweeperDTO.Phase.won, response.getPhase());
            Assert.assertEquals(1, itemSettleCalls[0]);
        } finally {
            MinesweeperService.clearRoom(room.getId());
            UserCache.remove(homeowner.getId());
            UserCache.remove(opponent.getId());
        }
    }

    @Test
    public void roomSafePingPlayItemShouldReturnSafeTargetAndConsume() {
        User homeowner = user("home-channel-safe-ping", 9741L, "home-safe-ping");
        User opponent = user("opponent-channel-safe-ping", 9742L, "opponent-safe-ping");
        UserCache.add(homeowner.getId(), homeowner);
        UserCache.add(opponent.getId(), opponent);
        GameRoom room = room("minesweeper-room-safe-ping-test", homeowner, opponent);
        room.getUsers().get(homeowner.getIdentityKey()).setPetPlayItemId("item_mine_safe_ping");
        MinesweeperService.clearRoom(room.getId());
        final int[] itemSettleCalls = {0};
        MinesweeperService.setBoardGeneratorForTest((rows, cols, mines, firstClick) ->
                NoGuessMinesweeper.Board.fromMines(rows, cols,
                        Collections.singletonList(new NoGuessMinesweeper.Point(1, 0))));
        MinesweeperService.setGameItemSettlerForTest((targetRoom, playerKey, itemId, slot, status) -> {
            itemSettleCalls[0]++;
            Assert.assertEquals(room.getId(), targetRoom.getId());
            Assert.assertEquals(homeowner.getIdentityKey(), playerKey);
            Assert.assertEquals("item_mine_safe_ping", itemId);
            Assert.assertEquals("gameplay", slot);
            Assert.assertEquals("consumed", status);
        });
        try {
            MinesweeperDTO safeOpen = actionRequest(room.getId(), 5, 5, 1, 0, 0);
            safeOpen.setActorKey(homeowner.getIdentityKey());
            MinesweeperService.handleRoom(homeowner, room, safeOpen);
            drain(homeowner);
            drain(opponent);

            MinesweeperDTO itemUse = itemUseRequest(room.getId(), "item_mine_safe_ping");
            itemUse.setActorKey(homeowner.getIdentityKey());
            MinesweeperService.handleRoom(homeowner, room, itemUse);
            MinesweeperDTO response = gameBody(readResponse(opponent));

            Assert.assertEquals(MinesweeperDTO.Event.ITEM_EFFECT, response.getEvent());
            Assert.assertNotNull(response.getPetItemExpiresAt());
            Assert.assertTrue(response.getPetItemExpiresAt() > 0);
            Assert.assertFalse(response.getPetItemTargetX() == 1 && response.getPetItemTargetY() == 0);
            Assert.assertEquals(1, itemSettleCalls[0]);
        } finally {
            MinesweeperService.clearRoom(room.getId());
            UserCache.remove(homeowner.getId());
            UserCache.remove(opponent.getId());
        }
    }

    @Test
    public void roomMineCounterPlayItemShouldCountTargetAreaAndConsume() {
        User homeowner = user("home-channel-counter", 9751L, "home-counter");
        User opponent = user("opponent-channel-counter", 9752L, "opponent-counter");
        UserCache.add(homeowner.getId(), homeowner);
        UserCache.add(opponent.getId(), opponent);
        GameRoom room = room("minesweeper-room-counter-test", homeowner, opponent);
        room.getUsers().get(homeowner.getIdentityKey()).setPetPlayItemId("item_mine_counter");
        MinesweeperService.clearRoom(room.getId());
        final int[] itemSettleCalls = {0};
        MinesweeperService.setBoardGeneratorForTest((rows, cols, mines, firstClick) ->
                NoGuessMinesweeper.Board.fromMines(rows, cols, java.util.Arrays.asList(
                        new NoGuessMinesweeper.Point(0, 0),
                        new NoGuessMinesweeper.Point(2, 2),
                        new NoGuessMinesweeper.Point(4, 0))));
        MinesweeperService.setGameItemSettlerForTest((targetRoom, playerKey, itemId, slot, status) -> {
            itemSettleCalls[0]++;
            Assert.assertEquals(room.getId(), targetRoom.getId());
            Assert.assertEquals(homeowner.getIdentityKey(), playerKey);
            Assert.assertEquals("item_mine_counter", itemId);
            Assert.assertEquals("gameplay", slot);
            Assert.assertEquals("consumed", status);
        });
        try {
            MinesweeperDTO safeOpen = actionRequest(room.getId(), 5, 5, 3, 4, 4);
            safeOpen.setActorKey(homeowner.getIdentityKey());
            MinesweeperService.handleRoom(homeowner, room, safeOpen);
            drain(homeowner);
            drain(opponent);

            MinesweeperDTO itemUse = itemUseRequest(room.getId(), "item_mine_counter");
            itemUse.setActorKey(homeowner.getIdentityKey());
            itemUse.setX(1);
            itemUse.setY(1);
            MinesweeperService.handleRoom(homeowner, room, itemUse);
            MinesweeperDTO response = gameBody(readResponse(opponent));

            Assert.assertEquals(MinesweeperDTO.Event.ITEM_EFFECT, response.getEvent());
            Assert.assertEquals(Integer.valueOf(2), response.getPetItemCounterMines());
            Assert.assertEquals(Integer.valueOf(1), response.getPetItemTargetX());
            Assert.assertEquals(Integer.valueOf(1), response.getPetItemTargetY());
            Assert.assertEquals(1, itemSettleCalls[0]);
        } finally {
            MinesweeperService.clearRoom(room.getId());
            UserCache.remove(homeowner.getId());
            UserCache.remove(opponent.getId());
        }
    }

    @Test
    public void roomMetalDetectorPlayItemShouldConsumeAndMarkTwoRealMines() {
        User homeowner = user("home-channel-metal-detector", 9801L, "home-metal-detector");
        User opponent = user("opponent-channel-metal-detector", 9802L, "opponent-metal-detector");
        UserCache.add(homeowner.getId(), homeowner);
        UserCache.add(opponent.getId(), opponent);
        GameRoom room = room("minesweeper-room-metal-detector-test", homeowner, opponent);
        room.getUsers().get(homeowner.getIdentityKey()).setPetPlayItemId("item_mine_detector");
        MinesweeperService.clearRoom(room.getId());
        final int[] itemSettleCalls = {0};
        MinesweeperService.setBoardGeneratorForTest((rows, cols, mines, firstClick) ->
                NoGuessMinesweeper.Board.fromMines(rows, cols, java.util.Arrays.asList(
                        new NoGuessMinesweeper.Point(1, 0),
                        new NoGuessMinesweeper.Point(2, 0),
                        new NoGuessMinesweeper.Point(4, 4))));
        MinesweeperService.setGameItemSettlerForTest((targetRoom, playerKey, itemId, slot, status) -> {
            itemSettleCalls[0]++;
            Assert.assertEquals(room.getId(), targetRoom.getId());
            Assert.assertEquals(homeowner.getIdentityKey(), playerKey);
            Assert.assertEquals("item_mine_detector", itemId);
            Assert.assertEquals("gameplay", slot);
            Assert.assertEquals("consumed", status);
        });
        try {
            MinesweeperDTO safeOpen = actionRequest(room.getId(), 5, 5, 3, 0, 0);
            safeOpen.setActorKey(homeowner.getIdentityKey());
            MinesweeperService.handleRoom(homeowner, room, safeOpen);
            drain(homeowner);
            drain(opponent);

            MinesweeperDTO itemUse = itemUseRequest(room.getId(), "item_mine_detector");
            itemUse.setActorKey(homeowner.getIdentityKey());
            MinesweeperService.handleRoom(homeowner, room, itemUse);
            MinesweeperDTO response = gameBody(readResponse(opponent));

            Assert.assertEquals(MinesweeperDTO.Phase.playing, response.getPhase());
            Assert.assertEquals(MinesweeperDTO.Event.ITEM_EFFECT, response.getEvent());
            Assert.assertEquals(1, itemSettleCalls[0]);
            long markedMines = response.getCells().stream()
                    .filter(cell -> Boolean.TRUE.equals(cell.getSharedMarked()))
                    .count();
            Assert.assertEquals(2L, markedMines);
            Assert.assertTrue(response.getCells().stream().anyMatch(cell ->
                    cell.getX() == 1 && cell.getY() == 0 && Boolean.TRUE.equals(cell.getSharedMarked())));
            Assert.assertTrue(response.getCells().stream().anyMatch(cell ->
                    cell.getX() == 2 && cell.getY() == 0 && Boolean.TRUE.equals(cell.getSharedMarked())));
        } finally {
            MinesweeperService.clearRoom(room.getId());
            UserCache.remove(homeowner.getId());
            UserCache.remove(opponent.getId());
        }
    }

    @Test
    public void roomSecondCarrySlotPlayItemShouldConsumeInteractionSlot() {
        User homeowner = user("home-channel-second-slot", 9821L, "home-second-slot");
        User opponent = user("opponent-channel-second-slot", 9822L, "opponent-second-slot");
        UserCache.add(homeowner.getId(), homeowner);
        UserCache.add(opponent.getId(), opponent);
        GameRoom room = room("minesweeper-room-second-slot-test", homeowner, opponent);
        room.getUsers().get(homeowner.getIdentityKey()).setPetInteractionItemId("item_mine_detector");
        MinesweeperService.clearRoom(room.getId());
        final int[] itemSettleCalls = {0};
        MinesweeperService.setBoardGeneratorForTest((rows, cols, mines, firstClick) ->
                NoGuessMinesweeper.Board.fromMines(rows, cols, java.util.Arrays.asList(
                        new NoGuessMinesweeper.Point(1, 0),
                        new NoGuessMinesweeper.Point(2, 0))));
        MinesweeperService.setGameItemSettlerForTest((targetRoom, playerKey, itemId, slot, status) -> {
            itemSettleCalls[0]++;
            Assert.assertEquals(room.getId(), targetRoom.getId());
            Assert.assertEquals(homeowner.getIdentityKey(), playerKey);
            Assert.assertEquals("item_mine_detector", itemId);
            Assert.assertEquals("interaction", slot);
            Assert.assertEquals("consumed", status);
        });
        try {
            MinesweeperDTO safeOpen = actionRequest(room.getId(), 5, 5, 2, 0, 0);
            safeOpen.setActorKey(homeowner.getIdentityKey());
            MinesweeperService.handleRoom(homeowner, room, safeOpen);
            drain(homeowner);
            drain(opponent);

            MinesweeperDTO itemUse = itemUseRequest(room.getId(), "item_mine_detector");
            itemUse.setActorKey(homeowner.getIdentityKey());
            MinesweeperService.handleRoom(homeowner, room, itemUse);
            MinesweeperDTO response = gameBody(readResponse(opponent));

            Assert.assertEquals(MinesweeperDTO.Event.ITEM_EFFECT, response.getEvent());
            Assert.assertEquals(1, itemSettleCalls[0]);
        } finally {
            MinesweeperService.clearRoom(room.getId());
            UserCache.remove(homeowner.getId());
            UserCache.remove(opponent.getId());
        }
    }

    @Test
    public void roomMetalDetectorPlayItemShouldRefundWhenOwnerNeverTriggersIt() {
        User homeowner = user("home-channel-metal-detector-refund", 9901L, "home-metal-detector-refund");
        User opponent = user("opponent-channel-metal-detector-refund", 9902L, "opponent-metal-detector-refund");
        UserCache.add(homeowner.getId(), homeowner);
        UserCache.add(opponent.getId(), opponent);
        GameRoom room = room("minesweeper-room-metal-detector-refund-test", homeowner, opponent);
        room.getUsers().get(opponent.getIdentityKey()).setPetPlayItemId("item_mine_detector");
        MinesweeperService.clearRoom(room.getId());
        final int[] itemSettleCalls = {0};
        MinesweeperService.setBoardGeneratorForTest((rows, cols, mines, firstClick) ->
                NoGuessMinesweeper.Board.fromMines(rows, cols,
                        Collections.singletonList(new NoGuessMinesweeper.Point(4, 4))));
        MinesweeperService.setGameItemSettlerForTest((targetRoom, playerKey, itemId, slot, status) -> {
            itemSettleCalls[0]++;
            Assert.assertEquals(room.getId(), targetRoom.getId());
            Assert.assertEquals(opponent.getIdentityKey(), playerKey);
            Assert.assertEquals("item_mine_detector", itemId);
            Assert.assertEquals("gameplay", slot);
            Assert.assertEquals("refunded", status);
        });
        try {
            MinesweeperDTO safeOpen = actionRequest(room.getId(), 5, 5, 1, 0, 0);
            safeOpen.setActorKey(homeowner.getIdentityKey());
            MinesweeperService.handleRoom(homeowner, room, safeOpen);
            MinesweeperDTO response = gameBody(readResponse(opponent));

            Assert.assertEquals(MinesweeperDTO.Phase.won, response.getPhase());
            Assert.assertEquals(1, itemSettleCalls[0]);
        } finally {
            MinesweeperService.clearRoom(room.getId());
            UserCache.remove(homeowner.getId());
            UserCache.remove(opponent.getId());
        }
    }

    @Test
    public void roomWonShouldApplyMiniGameRewardsForAllPlayers() {
        User homeowner = user("home-channel-mini-game-reward", 9911L, "home-mini-game-reward");
        User opponent = user("opponent-channel-mini-game-reward", 9912L, "opponent-mini-game-reward");
        UserCache.add(homeowner.getId(), homeowner);
        UserCache.add(opponent.getId(), opponent);
        GameRoom room = room("minesweeper-room-mini-game-reward-test", homeowner, opponent);
        MinesweeperService.clearRoom(room.getId());
        List<String> miniGameEvents = new ArrayList<>();
        long[] now = {1_000_000L, 1_061_000L};
        int[] nowIndex = {0};
        MinesweeperService.setNowSupplierForTest(() -> now[Math.min(nowIndex[0]++, now.length - 1)]);
        MinesweeperService.setMiniGameRewardsForTest(new MiniGameRewards() {
            @Override
            public void apply(long accountId, Game game, boolean win, long durationSeconds) {
                miniGameEvents.add(accountId + ":" + game + ":" + win + ":" + durationSeconds);
            }

            @Override
            public void applyRoomBonus(Game game, List<Long> accountIds, long durationSeconds) {
                miniGameEvents.add("room:" + game + ":" + accountIds + ":" + durationSeconds);
            }
        });
        MinesweeperService.setBoardGeneratorForTest((rows, cols, mines, firstClick) ->
                NoGuessMinesweeper.Board.fromMines(rows, cols,
                        Collections.singletonList(new NoGuessMinesweeper.Point(4, 4))));
        try {
            MinesweeperDTO firstOpen = actionRequest(room.getId(), 5, 5, 1, 0, 0);
            firstOpen.setActorKey(homeowner.getIdentityKey());
            MinesweeperService.handleRoom(homeowner, room, firstOpen);
            MinesweeperDTO response = gameBody(readResponse(homeowner));

            Assert.assertEquals(MinesweeperDTO.Phase.won, response.getPhase());
            Assert.assertEquals(3, miniGameEvents.size());
            Assert.assertTrue(miniGameEvents.contains("9911:MINESWEEPER:true:61"));
            Assert.assertTrue(miniGameEvents.contains("9912:MINESWEEPER:true:61"));
            Assert.assertTrue(miniGameEvents.contains("room:MINESWEEPER:[9911, 9912]:61"));
        } finally {
            MinesweeperService.clearRoom(room.getId());
            UserCache.remove(homeowner.getId());
            UserCache.remove(opponent.getId());
        }
    }

    private static MinesweeperDTO actionRequest(int rows, int cols, int mines, int x, int y) {
        return actionRequest("single", rows, cols, mines, x, y);
    }

    private static MinesweeperDTO actionRequest(String roomId, int rows, int cols, int mines, int x, int y) {
        MinesweeperDTO dto = new MinesweeperDTO(roomId);
        dto.setEvent(MinesweeperDTO.Event.SERVER_ACTION_REQUEST);
        dto.setAction(MinesweeperDTO.ActionType.OPEN);
        dto.setRows(rows);
        dto.setCols(cols);
        dto.setMines(mines);
        dto.setX(x);
        dto.setY(y);
        return dto;
    }

    private static MinesweeperDTO itemUseRequest(String roomId, String itemId) {
        MinesweeperDTO dto = new MinesweeperDTO(roomId);
        dto.setEvent(MinesweeperDTO.Event.ITEM_USE_REQUEST);
        dto.setPetItemId(itemId);
        dto.setRows(5);
        dto.setCols(5);
        dto.setMines(1);
        return dto;
    }

    private static User user() {
        User user = new User();
        user.setId("minesweeper-test-channel");
        user.setAccountId(9001L);
        user.setAccount("minesweeper-test");
        user.setNickname("扫雷测试");
        user.setUuid("minesweeper-test-uuid");
        user.setChannel(new EmbeddedChannel());
        return user;
    }

    private static User user(String channelId, long accountId, String account) {
        User user = new User();
        user.setId(channelId);
        user.setAccountId(accountId);
        user.setAccount(account);
        user.setNickname(account);
        user.setUuid(account + "-uuid");
        user.setChannel(new EmbeddedChannel());
        return user;
    }

    private static GameRoom room(User homeowner, User opponent) {
        return room("minesweeper-room-test", homeowner, opponent);
    }

    private static GameRoom room(String roomId, User homeowner, User opponent) {
        GameRoom room = new GameRoom();
        room.setId(roomId);
        room.setGame(Game.MINESWEEPER);
        room.setNums(2);
        room.setHomeowner(homeowner);
        room.getUsers().put(homeowner.getIdentityKey(), new GameRoom.Player(homeowner));
        room.getUsers().put(opponent.getIdentityKey(), new GameRoom.Player(opponent));
        return room;
    }

    private static MinesweeperDTO gameBody(Response response) {
        Assert.assertNotNull(response);
        Assert.assertEquals(MessageType.GAME, response.getType());
        Assert.assertTrue(response.getBody() instanceof MinesweeperDTO);
        return (MinesweeperDTO) response.getBody();
    }

    private static cn.xeblog.commons.entity.game.minesweeper.MinesweeperCellDTO cell(
            MinesweeperDTO dto, int x, int y) {
        for (cn.xeblog.commons.entity.game.minesweeper.MinesweeperCellDTO cell : dto.getCells()) {
            if (cell.getX() == x && cell.getY() == y) {
                return cell;
            }
        }
        throw new AssertionError("未找到扫雷格子: " + x + "," + y);
    }

    private static Response readResponse(User user) {
        return ((EmbeddedChannel) user.getChannel()).readOutbound();
    }

    private static void drain(User user) {
        while (readResponse(user) != null) {
        }
    }
}
