package com.fengsheng;

import org.apache.log4j.Logger;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class GameExecutor {
    private static final Logger log = Logger.getLogger(GameExecutor.class);
    private final static GameExecutor[] executors = new GameExecutor[(Runtime.getRuntime().availableProcessors() + 1) / 2];

    private final BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(1024);

    private GameExecutor() {
    }

    private void post(Runnable callback) {
        try {
            queue.put(callback);
        } catch (InterruptedException e) {
            log.error("put queue interrupted", e);
        }
    }

    public static void post(Game game, Runnable callback) {
        int mod = game.id % executors.length;
        if (executors[mod] == null) {
            synchronized (GameExecutor.class) {
                if (executors[mod] == null) {
                    executors[mod] = new GameExecutor();
                    Thread thread = new Thread(() -> {
                        while (true) {
                            try {
                                executors[mod].queue.take().run();
                            } catch (InterruptedException e) {
                                log.error("take queue interrupted", e);
                            }
                        }
                    });
                    thread.setDaemon(true);
                    thread.start();
                }
            }
        }
        executors[mod].post(callback);
    }
}
