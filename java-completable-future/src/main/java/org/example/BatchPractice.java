package org.example;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.IntStream;

import static org.example.SequencePractice.sequence;

public class BatchPractice {
    public static void run() {
        List<Integer> inputs = IntStream.rangeClosed(1, 10)
            .boxed()
            .toList();

        CompletableFuture<List<String>> result = processInBatches(
            inputs,
            3,
            BatchPractice::callAsync
        );

        System.out.println(result.join());
    }

    static <T, R> CompletableFuture<List<R>> processInBatches(
        List<T> inputs,
        int batchSize,
        Function<T, CompletableFuture<R>> asyncMapper
    ) {
        CompletableFuture<List<R>> result =
            CompletableFuture.completedFuture(new ArrayList<>());

        for (List<T> batch : batchesOf(inputs, batchSize)) {
            result = result.thenCompose(accumulated -> {
                List<CompletableFuture<R>> batchFutures =
                    batch.stream().map(asyncMapper).toList();

                return sequence(batchFutures).thenApply(batchResult -> {
                    accumulated.addAll(batchResult);
                    return accumulated;
                });
            });
        }

        return result;
    }

    static CompletableFuture<String> callAsync(Integer input) {
        return CompletableFuture.supplyAsync(() -> {
            log("start " + input);
            sleep(delayMillis(input));
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

    static <T> List<List<T>> batchesOf(List<T> inputs, int batchSize) {
        List<List<T>> batches = new ArrayList<>();

        for (int start = 0; start < inputs.size(); start += batchSize) {
            int end = Math.min(start + batchSize, inputs.size());
            batches.add(inputs.subList(start, end));
        }

        return batches;
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
