package com.fengsheng;

import io.netty.util.HashedWheelTimer;
import org.apache.log4j.Logger;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

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
        }
    }

    private void post(Runnable callback) {
        try {
            queue.put(callback);
        } catch (InterruptedException e) {
            log.error("put queue interrupted", e);
        }
    }

    public static void post(Game game, Runnable callback) {
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
}
