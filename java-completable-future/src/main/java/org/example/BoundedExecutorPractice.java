package org.example;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class BoundedExecutorPractice {
    public static void run() {
        ExecutorService executor = new ThreadPoolExecutor(
                2,
                2,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(2),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 1; i <= 8; i++) {
            int taskId = i;
            log("submit " + taskId);

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                log("start " + taskId);
                sleep(500);
                log("end " + taskId);
            }, executor);
            futures.add(future);
        }

        futures.forEach(CompletableFuture::join);
        executor.shutdown();
    }

    static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    static void log(String message) {
        System.out.println(System.currentTimeMillis() + " | "
                + Thread.currentThread().getName() + " | "
                + message);
    }
}
