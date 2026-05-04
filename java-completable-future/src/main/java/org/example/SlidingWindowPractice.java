package org.example;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.IntStream;

public class SlidingWindowPractice {
    public static void run() {
        List<Integer> inputs = IntStream.rangeClosed(1, 10)
            .boxed()
            .toList();

        CompletableFuture<List<String>> result = processWithLimitFallback(
            inputs,
            3,
            SlidingWindowPractice::callMaybeFailAsync,
            "FAILED"
        );

        System.out.println(result.join());
    }

    static <T, R> CompletableFuture<List<R>> processWithLimit(
        List<T> inputs,
        int limit,
        Function<T, CompletableFuture<R>> asyncMapper
    ) {
        if (inputs.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }

        Coordinator<T, R> coordinator =
            new Coordinator<>(inputs, limit, asyncMapper);
        return coordinator.start();
    }

    static <T, R> CompletableFuture<List<R>> processWithLimitFallback(
        List<T> inputs,
        int limit,
        Function<T, CompletableFuture<R>> asyncMapper,
        R fallback
    ) {
        return processWithLimit(inputs, limit, input -> {
            try {
                return asyncMapper.apply(input)
                    .exceptionally(ex -> fallback);
            } catch (Throwable ex) {
                return CompletableFuture.completedFuture(fallback);
            }
        });
    }

    static final class Coordinator<T, R> {
        private final List<T> inputs;
        private final int limit;
        private final Function<T, CompletableFuture<R>> asyncMapper;
        private final List<R> results;
        private final CompletableFuture<List<R>> finalFuture = new CompletableFuture<>();

        private int nextIndex = 0;
        private int inFlight = 0;

        Coordinator(
            List<T> inputs,
            int limit,
            Function<T, CompletableFuture<R>> asyncMapper
        ) {
            this.inputs = inputs;
            this.limit = limit;
            this.asyncMapper = asyncMapper;
            this.results = new ArrayList<>(Collections.nCopies(inputs.size(), null));
        }

        CompletableFuture<List<R>> start() {
            startMoreIfPossible();
            return finalFuture;
        }

        private synchronized void startMoreIfPossible() {
            while (inFlight < limit && nextIndex < inputs.size()) {
                int index = nextIndex;
                nextIndex++;
                inFlight++;
                startOne(index);
            }
        }

        private void startOne(int index) {
            CompletableFuture<R> future;
            T element = inputs.get(index);

            try {
                future = asyncMapper.apply(element);
            } catch (Throwable ex) {
                handleResult(index, null, ex);
                return;
            }

            future.whenComplete((value, ex) -> {
                handleResult(index, value, ex);
            });
        }

        private synchronized void handleResult(int index, R value, Throwable ex) {
            if (finalFuture.isDone()) {
                return;
            }

            inFlight--;

            if (ex != null) {
                finalFuture.completeExceptionally(ex);
                return;
            }

            results.set(index, value);

            if (nextIndex == inputs.size() && inFlight == 0) {
                finalFuture.complete(results);
                return;
            }

            startMoreIfPossible();
        }
    }

    static CompletableFuture<String> callAsync(Integer input) {
        return CompletableFuture.supplyAsync(() -> {
            log("start " + input);
            sleep(delayMillis(input));
            log("end " + input);
            return "result-" + input;
        });
    }

    static CompletableFuture<String> callMaybeFailAsync(Integer input) {
        return CompletableFuture.supplyAsync(() -> {
            log("start " + input);
            sleep(delayMillis(input));

            if (input == 3 || input == 7) {
                log("fail " + input);
                throw new RuntimeException("failed " + input);
            }

            log("end " + input);
            return "result-" + input;
        });
    }

    static long delayMillis(Integer input) {
        if (input == 3 || input == 7) {
            return 700;
        }
        return 200;
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
