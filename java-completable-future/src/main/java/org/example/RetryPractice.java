package org.example;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static java.lang.Math.max;

public class RetryPractice {
    public static void run() {
        AtomicInteger attempts = new AtomicInteger();

        Supplier<CompletableFuture<String>> task = () -> CompletableFuture.supplyAsync(() -> {
            int attempt = attempts.incrementAndGet();
            log("attempt " + attempt);

            if (attempt < 3) {
                throw new RuntimeException("failed attempt " + attempt);
            }

            return "OK";
        });

        CompletableFuture<String> result = retryWithBackoff(task, 3, Duration.ofMillis(200), 2.0, 0.5);

        System.out.println(result.join());
    }

    static <T> CompletableFuture<T> retryWithBackoff(Supplier<CompletableFuture<T>> task, int maxAttempts, Duration initialDelay, double multiplier, double jitterRatio) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        if (multiplier < 1.0) {
            throw new IllegalArgumentException("multiplier must be at least 1.0");
        }
        if (jitterRatio < 0) {
            throw new IllegalArgumentException("jitterRatio must be non-negative");
        }

        return attempt(task, maxAttempts, initialDelay, multiplier, jitterRatio);
    }

    private static <T> CompletableFuture<T> attempt(Supplier<CompletableFuture<T>> task, int remainingAttempts, Duration currentDelay, double multiplier, double jitterRatio) {
        CompletableFuture<T> current;
        try {
            current = task.get();
        } catch (Throwable ex) {
            current = CompletableFuture.failedFuture(ex);
        }

        return current.handle((value, ex) -> {
            if (ex == null) {
                return CompletableFuture.<T>completedFuture(value);
            }

            if (remainingAttempts <= 1) {
                return CompletableFuture.<T>failedFuture(ex);
            }

            Duration nextDelay = multiply(currentDelay, multiplier);
            Duration actualDelay = addJitter(currentDelay, jitterRatio);

            log("retry after " + actualDelay.toMillis() + "ms");
            return delay(actualDelay)
                .thenCompose(ignored ->
                    attempt(task, remainingAttempts - 1, nextDelay, multiplier, jitterRatio)
                );
        }).thenCompose(next -> next);
    }

    private static Duration multiply(Duration duration, double multiplier) {
        return Duration.ofMillis((long) (duration.toMillis() * multiplier));
    }

    static CompletableFuture<Void> delay(Duration duration) {
        return CompletableFuture.runAsync(() -> {
        }, CompletableFuture.delayedExecutor(duration.toMillis(), TimeUnit.MILLISECONDS));
    }

    static Duration addJitter(Duration baseDelay, double jitterRatio) {
        if (jitterRatio < 0) {
            throw new IllegalArgumentException("jitterRatio must be non-negative");
        }

        long baseDelayMillis = baseDelay.toMillis();
        long upperDelayMillis = (long) (baseDelayMillis + baseDelayMillis * jitterRatio);
        long lowerDelayMillis = (long) max(0, baseDelayMillis - baseDelayMillis * jitterRatio);
        long jitteredDelay = ThreadLocalRandom.current().nextLong(lowerDelayMillis, upperDelayMillis + 1);
        return Duration.ofMillis(jitteredDelay);
    }

    static void log(String message) {
        System.out.println(System.currentTimeMillis() + " | " + Thread.currentThread().getName() + " | " + message);
    }
}
