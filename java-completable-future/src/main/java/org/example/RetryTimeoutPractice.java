package org.example;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class RetryTimeoutPractice {
    public static void run() {
        AtomicInteger attempts = new AtomicInteger();

        Supplier<CompletableFuture<String>> task = () -> {
            int attempt = attempts.incrementAndGet();

            // Attempt-level timeout: each logical attempt is considered failed
            // if its future does not complete within 1 second.
            return slowTask(attempt)
                .orTimeout(1, TimeUnit.SECONDS);
        };

        CompletableFuture<String> result =
            RetryPractice.retryWithBackoff(
                    task,
                    3,
                    Duration.ofMillis(200),
                    1.0,
                    0.0
                )
                // Overall timeout: the whole retry chain, including attempts
                // and backoff delays, must finish within this budget.
                .orTimeout(2500, TimeUnit.MILLISECONDS);

        try {
            System.out.println(result.join());
        } catch (CompletionException ex) {
            Throwable cause = unwrap(ex);
            log("final failure: " + cause.getClass().getSimpleName());
        }

        sleep(4000);
        log("main end");
    }

    static CompletableFuture<String> slowTask(int attempt) {
        return CompletableFuture.supplyAsync(() -> {
            log("attempt " + attempt + " start underlying work");
            // This simulates a blocking call that does not stop just because
            // the CompletableFuture returned to the caller timed out.
            sleep(3000);
            log("attempt " + attempt + " end underlying work");
            return "OK-" + attempt;
        });
    }

    static Throwable unwrap(Throwable ex) {
        if (ex instanceof CompletionException && ex.getCause() != null) {
            return ex.getCause();
        }
        return ex;
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
