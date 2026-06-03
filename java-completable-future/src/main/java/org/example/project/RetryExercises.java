package org.example.project;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class RetryExercises {
    private RetryExercises() {
    }

    public static <T> CompletableFuture<T> retryWithBackoff(
        Supplier<CompletableFuture<T>> task,
        int maxAttempts,
        Duration initialDelay,
        double multiplier,
        double jitterRatio
    ) {
        throw new UnsupportedOperationException("TODO: call task.get() for each new attempt");
    }

    public static Duration addJitter(Duration baseDelay, double jitterRatio) {
        throw new UnsupportedOperationException("TODO: return delay within base*(1-ratio) and base*(1+ratio)");
    }
}
