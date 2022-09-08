package com.fengsheng;

import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import org.apache.log4j.Logger;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public final class GameExecutor implements Runnable {
    private static final Logger log = Logger.getLogger(GameExecutor.class);
    private final static GameExecutor[] executors = new GameExecutor[(Runtime.getRuntime().availableProcessors() + 1) / 2];
    public final static HashedWheelTimer TimeWheel = new HashedWheelTimer();

    private final BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(1024);

    private GameExecutor() {
    }

    @SuppressWarnings("InfiniteLoopStatement")
    @Override
    public void run() {
        while (true) try {
            queue.take().run();
        } catch (InterruptedException e) {
            log.error("take queue interrupted", e);
        } catch (Throwable e) {
            log.error("catch throwable", e);
        }
    }

    private void post(Runnable callback) {
        try {
            queue.put(callback);
        } catch (InterruptedException e) {
            log.error("put queue interrupted", e);
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
        executors[mod].post(callback);
    }


    /**
     * （重要）在一段时间延迟后，由游戏的主线程去执行一段逻辑。
     * <p>
     * 绝大部分逻辑代码都应该由游戏的主线程去执行，因此不需要加锁。
     */
    public static Timeout post(final Game game, final Runnable callback, long delay, TimeUnit unit) {
        return TimeWheel.newTimeout(timeout -> post(game, callback), delay, unit);
    }
}
