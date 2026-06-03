package org.example.project;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeoutObservationTest {
    @Test
    @Disabled("과제 6: orTimeout 이후 underlying work가 계속될 수 있음을 관찰한 뒤 이 줄을 제거하세요.")
    void orTimeoutDoesNotAutomaticallyStopUnderlyingWork() {
        AtomicBoolean underlyingWorkEnded = new AtomicBoolean(false);

        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            sleep(300);
            underlyingWorkEnded.set(true);
            return "late result";
        }).orTimeout(50, TimeUnit.MILLISECONDS);

        assertThrows(java.util.concurrent.CompletionException.class, future::join);

        sleep(400);
        assertTrue(underlyingWorkEnded.get());
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
