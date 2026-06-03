package org.example.project;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryExerciseTest {
    @Test
    @Disabled("과제 5: retryWithBackoff를 직접 구현한 뒤 이 줄을 제거하세요.")
    void retryCreatesNewFutureForEachAttempt() {
        AtomicInteger attempts = new AtomicInteger();

        String result = RetryExercises.retryWithBackoff(
            () -> CompletableFuture.supplyAsync(() -> {
                int attempt = attempts.incrementAndGet();
                if (attempt < 3) {
                    throw new IllegalStateException("failed attempt " + attempt);
                }
                return "OK";
            }),
            3,
            Duration.ofMillis(10),
            1.0,
            0.0
        ).join();

        assertEquals("OK", result);
        assertEquals(3, attempts.get());
    }

    @Test
    @Disabled("과제 5-확장: attempt 소진 실패를 구현한 뒤 이 줄을 제거하세요.")
    void retryFailsAfterMaxAttempts() {
        AtomicInteger attempts = new AtomicInteger();

        assertThrows(
            java.util.concurrent.CompletionException.class,
            () -> RetryExercises.retryWithBackoff(
                () -> {
                    attempts.incrementAndGet();
                    return CompletableFuture.failedFuture(new IllegalStateException("always fails"));
                },
                2,
                Duration.ofMillis(10),
                1.0,
                0.0
            ).join()
        );

        assertEquals(2, attempts.get());
    }

    @Test
    @Disabled("과제 5-확장: jitter 계산을 구현한 뒤 이 줄을 제거하세요.")
    void jitterStaysWithinExpectedRange() {
        for (int i = 0; i < 100; i++) {
            Duration delay = RetryExercises.addJitter(Duration.ofMillis(200), 0.5);

            assertTrue(delay.toMillis() >= 100);
            assertTrue(delay.toMillis() <= 300);
        }
    }
}
