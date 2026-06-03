package org.example.project;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FutureUtilityExerciseTest {
    @Test
    // @Disabled("과제 2: sequence를 직접 구현한 뒤 이 줄을 제거하세요.")
    void sequenceCollectsResultsInInputOrder() {
        CompletableFuture<String> slow = delayed("A", 150);
        CompletableFuture<String> fast = delayed("B", 50);

        List<String> result = FutureExercises.sequence(List.of(slow, fast)).join();

        assertEquals(List.of("A", "B"), result);
    }

    @Test
    // @Disabled("과제 2-확장: 실패 전파를 확인한 뒤 이 줄을 제거하세요.")
    void sequenceFailsWhenAnyFutureFails() {
        CompletableFuture<String> ok = CompletableFuture.completedFuture("A");
        CompletableFuture<String> failed = CompletableFuture.failedFuture(new IllegalStateException("boom"));

        assertThrows(
            java.util.concurrent.CompletionException.class,
            () -> FutureExercises.sequence(List.of(ok, failed)).join()
        );
    }

    @Test
    @Disabled("과제 3: batch 처리를 직접 구현한 뒤 이 줄을 제거하세요.")
    void processInBatchesStartsNextBatchAfterPreviousBatchCompletes() {
        AtomicInteger started = new AtomicInteger();

        List<String> result = FutureExercises.processInBatches(
            List.of(1, 2, 3, 4),
            2,
            input -> CompletableFuture.supplyAsync(() -> {
                int currentStarted = started.incrementAndGet();
                if (input == 3) {
                    assertTrue(currentStarted >= 3);
                }
                sleep(30);
                return "result-" + input;
            })
        ).join();

        assertEquals(List.of("result-1", "result-2", "result-3", "result-4"), result);
    }

    @Test
    @Disabled("과제 4: sliding window를 직접 구현한 뒤 이 줄을 제거하세요.")
    void processWithLimitNeverExceedsLimitAndKeepsInputOrder() {
        var executor = Executors.newFixedThreadPool(4);
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();

        try {
            List<String> result = FutureExercises.processWithLimit(
                List.of(1, 2, 3, 4, 5),
                2,
                input -> CompletableFuture.supplyAsync(() -> {
                    int current = inFlight.incrementAndGet();
                    maxInFlight.updateAndGet(previous -> Math.max(previous, current));
                    sleep(input == 1 ? 120 : 30);
                    inFlight.decrementAndGet();
                    return "result-" + input;
                }, executor)
            ).join();

            assertEquals(List.of("result-1", "result-2", "result-3", "result-4", "result-5"), result);
            assertTrue(maxInFlight.get() <= 2);
        } finally {
            executor.shutdownNow();
        }
    }

    private static CompletableFuture<String> delayed(String value, long delayMillis) {
        return CompletableFuture.supplyAsync(() -> {
            sleep(delayMillis);
            return value;
        });
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
