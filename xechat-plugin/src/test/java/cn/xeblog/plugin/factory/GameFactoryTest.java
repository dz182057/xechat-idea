package cn.xeblog.plugin.factory;

import cn.xeblog.commons.entity.User;
import cn.xeblog.commons.entity.game.GameRoom;
import cn.xeblog.commons.enums.Game;
import cn.xeblog.plugin.action.GameAction;
import cn.xeblog.plugin.cache.DataCache;
import cn.xeblog.plugin.game.AbstractGame;
import cn.xeblog.plugin.game.quickquiz.QuickQuiz;
import cn.xeblog.plugin.game.turtlesoup.TurtleSoup;
import org.junit.Assert;
import org.junit.Test;

import javax.swing.*;
import java.lang.reflect.Field;

public class GameFactoryTest {

    @Test
    public void produceQuickQuizWhenOnline() {
        DataCache.isOnline = true;

        Assert.assertNotNull(new QuickQuiz());
    }

    @Test
    public void produceTurtleSoupWhenOnline() {
        DataCache.isOnline = true;

        Assert.assertNotNull(new TurtleSoup());
    }

    @Test
    public void homeownerRoomClosedEndsCurrentGame() throws Exception {
        DataCache.isOnline = true;
        GameAction.clean();
        TestGame action = new TestGame();

        User me = new User();
        me.setId("u-1");
        me.setUsername("me");
        GameRoom room = new GameRoom();
        room.setId("room-1");
        room.setGame(Game.QUICK_QUIZ);
        room.setNums(2);
        room.setHomeowner(me);
        room.addUser(me);
        setGameRoomHandlerField(action, "gameRoom", room);
        setGameRoomHandlerField(action, "isHomeowner", true);
        setGameActionField("game", Game.QUICK_QUIZ);
        setGameActionField("action", action);
        GameAction.setRoomId("room-1");

        action.roomClosed();

        Assert.assertFalse(GameAction.playing());
        Assert.assertNull(GameAction.getGame());
        Assert.assertNull(GameAction.getRoomId());
    }

    private static void setGameRoomHandlerField(AbstractGame action, String name, Object value) throws Exception {
        Field handlerField = AbstractGame.class.getDeclaredField("gameRoomHandler");
        handlerField.setAccessible(true);
        Object handler = handlerField.get(action);
        Field field = handler.getClass().getSuperclass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(handler, value);
    }

    private static void setGameActionField(String name, Object value) throws Exception {
        Field field = GameAction.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    public static class TestGame extends AbstractGame {
        @Override
        protected void init() {
        }

        @Override
        protected void start() {
        }

        @Override
        protected JPanel getComponent() {
            return new JPanel();
        }

        @Override
        public void over() {
        }
    }
}
