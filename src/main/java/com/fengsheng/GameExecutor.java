package com.fengsheng;

import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import org.apache.log4j.Logger;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public final class GameExecutor implements Runnable {
    private static final Logger log = Logger.getLogger(GameExecutor.class);
    private static final GameExecutor[] executors = new GameExecutor[(Runtime.getRuntime().availableProcessors() + 1) / 2];
    public static final HashedWheelTimer TimeWheel = new HashedWheelTimer();

    private final BlockingQueue<GameAndCallback> queue = new LinkedBlockingQueue<>();

    private GameExecutor() {
    }

    @SuppressWarnings("InfiniteLoopStatement")
    @Override
    public void run() {
        while (true) try {
            GameAndCallback gameAndCallback = queue.take();
            if (!gameAndCallback.game().isEnd()) gameAndCallback.callback().run();
        } catch (InterruptedException e) {
            log.error("take queue interrupted", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("catch throwable", e);
        }
    }

    private void post(GameAndCallback gameAndCallback) {
        try {
            queue.put(gameAndCallback);
        } catch (InterruptedException e) {
            log.error("put queue interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * （重要）由游戏的主线程去执行一段逻辑。
     * <p>
     * 绝大部分逻辑代码都应该由游戏的主线程去执行，因此不需要加锁。
     */
    public static void post(final Game game, final Runnable callback) {
        int mod = game.getId() % executors.length;
        if (executors[mod] == null) {
            synchronized (GameExecutor.class) {
                if (executors[mod] == null) {
                    executors[mod] = new GameExecutor();
                    Thread thread = new Thread(executors[mod]);
                    thread.setDaemon(true);
                    thread.start();
                }
            }
        }
        executors[mod].post(new GameAndCallback(game, callback));
    }


    /**
     * （重要）在一段时间延迟后，由游戏的主线程去执行一段逻辑。
     * <p>
     * 绝大部分逻辑代码都应该由游戏的主线程去执行，因此不需要加锁。
     */
    public static Timeout post(final Game game, final Runnable callback, long delay, TimeUnit unit) {
        return TimeWheel.newTimeout(timeout -> post(game, callback), delay, unit);
    }

    private record GameAndCallback(Game game, Runnable callback) {

    }
}
