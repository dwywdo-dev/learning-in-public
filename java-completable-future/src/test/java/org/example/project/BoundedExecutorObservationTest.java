package org.example.project;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedExecutorObservationTest {
    @Test
    @Disabled("과제 7: CallerRunsPolicy backpressure를 관찰한 뒤 이 줄을 제거하세요.")
    void callerRunsPolicyCanMakeSubmitterRunTask() {
        AtomicBoolean mainThreadRanTask = new AtomicBoolean(false);

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );

        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (int i = 1; i <= 4; i++) {
                futures.add(CompletableFuture.runAsync(() -> {
                    if (Thread.currentThread().getName().equals("main")) {
                        mainThreadRanTask.set(true);
                    }
                    sleep(100);
                }, executor));
            }

            futures.forEach(CompletableFuture::join);
            assertTrue(mainThreadRanTask.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
