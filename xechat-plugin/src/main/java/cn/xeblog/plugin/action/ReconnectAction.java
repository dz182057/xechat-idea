package cn.xeblog.plugin.action;

import cn.xeblog.plugin.cache.DataCache;
import cn.xeblog.plugin.client.ClientConnectConsumer;
import io.netty.channel.Channel;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 插件端自动重连管理。
 */
public final class ReconnectAction {

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "xechat-reconnect");
        t.setDaemon(true);
        return t;
    });

    private static final State STATE = new State();

    private ReconnectAction() {
    }

    public static void enable() {
        DataCache.autoReconnectEnabled = true;
        STATE.reset();
    }

    public static void disable() {
        DataCache.autoReconnectEnabled = false;
        DataCache.reconnected = false;
        DataCache.loginFromReconnect = false;
        STATE.reset();
    }

    public static void reset() {
        STATE.reset();
    }

    public static boolean shouldReconnect() {
        return DataCache.autoReconnectEnabled && DataCache.connectionAction != null;
    }

    public static void schedule() {
        if (!shouldReconnect() || !STATE.markScheduling()) {
            return;
        }

        long delayMillis = STATE.nextDelayMillis();
        ConsoleAction.showSimpleMsg("已断开连接，" + delayMillis / 1000 + " 秒后自动重连...");
        SCHEDULER.schedule(() -> {
            STATE.markIdle();
            if (!shouldReconnect()) {
                return;
            }
            DataCache.reconnected = true;
            DataCache.connectionAction.exec(new ClientConnectConsumer() {
                @Override
                public void doSucceed(Channel channel) {
                    // 登录消息由 XEChatClientHandler.channelActive 自动发送。
                }

                @Override
                public void doFailed() {
                    ConsoleAction.showSimpleMsg("重连失败，继续尝试...");
                    schedule();
                }
            });
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    public static final class State {

        private static final long MAX_DELAY_MILLIS = 30000L;

        private int attempt;

        private final AtomicBoolean scheduling = new AtomicBoolean(false);

        public long nextDelayMillis() {
            long delay = 1000L * (1L << Math.min(attempt, 5));
            attempt++;
            return Math.min(MAX_DELAY_MILLIS, delay);
        }

        public boolean markScheduling() {
            return scheduling.compareAndSet(false, true);
        }

        public void markIdle() {
            scheduling.set(false);
        }

        public void reset() {
            attempt = 0;
            scheduling.set(false);
        }
    }
}
