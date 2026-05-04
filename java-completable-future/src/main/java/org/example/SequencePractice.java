package org.example;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class SequencePractice {
    public static void run() {
        List<CompletableFuture<String>> futures = List.of(
            delayedValue("A", 300),
            delayedFailure("B", 100).exceptionally(ex -> {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                log("B failed: " + cause.getMessage());
                return "FAILED";
            }),
            delayedValue("C", 200)
        );

        CompletableFuture<List<String>> result = sequence(futures);

        System.out.println(result.join());
    }

    static <T> CompletableFuture<List<T>> sequence(
        List<CompletableFuture<T>> futures
    ) {
        CompletableFuture<Void> allFuture = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        return allFuture.thenApply(unused -> futures.stream().map(CompletableFuture::join).toList());
    }

    static <T> CompletableFuture<List<T>> sequenceWithFallback(
        List<CompletableFuture<T>> futures,
        T fallback
    ) {
        List<CompletableFuture<T>> safeFutures = futures.stream().map(future -> future.exceptionally(ex -> fallback)).toList();
        return sequence(safeFutures);
    }

    static CompletableFuture<String> delayedValue(String value, long delayMillis) {
        return CompletableFuture.supplyAsync(() -> {
            log("start " + value);
            sleep(delayMillis);
            log("end " + value);
            return value;
        });
    }

    static CompletableFuture<String> delayedFailure(String value, long delayMillis) {
        return CompletableFuture.supplyAsync(() -> {
            log("start " + value);
            sleep(delayMillis);
            log("fail " + value);
            throw new RuntimeException("failed " + value);
        });
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
        System.out.println(Thread.currentThread().getName() + " | " + message);
    }
}
