package org.example.project;

import java.sql.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class FutureExercises {
    private FutureExercises() {
    }

    public static <T> CompletableFuture<List<T>> sequence(List<CompletableFuture<T>> futures) {
        CompletableFuture<Void> allFuture = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        return allFuture.thenApply(unused -> futures.stream().map(CompletableFuture::join).toList());
    }

    public static <T, R> CompletableFuture<List<R>> processInBatches(
        List<T> inputs,
        int batchSize,
        Function<T, CompletableFuture<R>> asyncMapper
    ) {
        CompletableFuture<List<R>> result = CompletableFuture.completedFuture(new ArrayList<>());
        for (List<T> batch : batchesOf(inputs, batchSize)) {
            result = result.thenCompose(accumulated -> {
                List<CompletableFuture<R>> batchFutures = batch.stream().map(asyncMapper).toList();

                return sequence(batchFutures).thenApply(batchResult -> {
                    accumulated.addAll(batchResult);
                    return accumulated;
                });
            });
        }
    }

    private static <T> List<List<T>> batchesOf(List<T> inputs, int batchSize) {
        List<List<T>> batches = new ArrayList<>();

        for (int start = 0; start < inputs.size(); start += batchSize) {
            int end = Math.min(start + batchSize, inputs.size());
            batches.add(inputs.subList(start, end));
        }

        return batches;
    }

    public static <T, R> CompletableFuture<List<R>> processWithLimit(
        List<T> inputs,
        int limit,
        Function<T, CompletableFuture<R>> asyncMapper
    ) {
        throw new UnsupportedOperationException("TODO: keep at most limit tasks in flight");
    }

    public static <T, R> CompletableFuture<List<R>> processWithLimitFallback(
        List<T> inputs,
        int limit,
        Function<T, CompletableFuture<R>> asyncMapper,
        BiFunction<T, Throwable, R> fallback
    ) {
        throw new UnsupportedOperationException("TODO: continue after individual item failure");
    }
}
