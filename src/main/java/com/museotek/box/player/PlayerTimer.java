package com.museotek.box.player;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** One thread for the player's timers (login deadlines now, scene timers later). Tasks must be short. */
@Component
public class PlayerTimer implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(PlayerTimer.class);

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "player-timer");
        thread.setDaemon(true);
        return thread;
    });

    public ScheduledFuture<?> schedule(Runnable task, Duration delay) {
        return executor.schedule(() -> {
            // The executor would swallow the exception silently.
            try {
                task.run();
            } catch (RuntimeException e) {
                log.error("Player timer task failed", e);
            }
        }, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}
