package org.example;

import java.util.concurrent.*;

public class Main {
    public static void main(String[] args) {
        CompletableFuture<String> result = buildUserSummary();

        System.out.println(result.join());
    }

    static CompletableFuture<String> buildUserSummary() {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        return getUserIdAsync().thenCompose(userId -> {
            CompletableFuture<String> nameFuture = getUserNameAsync(userId, executor);
            CompletableFuture<Integer> orderCountFuture = getOrderCountAsync(userId, executor);
            return nameFuture.thenCombine(orderCountFuture,
                    (name, orderCount) -> {
                        log("thenCombine");
                        return name + " has " + orderCount + " orders";
                    });
        }).orTimeout(1, TimeUnit.SECONDS).exceptionally(ex -> {
            log("exceptionally");
            return "FAILED";
        }).whenComplete((value, ex) -> {
            executor.shutdown();
        });
    }

    static CompletableFuture<String> getUserIdAsync() {
        return CompletableFuture.supplyAsync(() -> {
            log("getUserIdAsync start");
            sleep(300);
            log("getUserIdAsync end");
            return "user-1";
        });
    }

    static CompletableFuture<String> getUserNameAsync(String userId, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            log("getUserNameAsync start");
            sleep(500);
            log("getUserNameAsync end");
            return "Kim";
        }, executor);
    }

    static CompletableFuture<Integer> getOrderCountAsync(String userId, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            log("getOrderCountAsync start");
            sleep(700);
            log("getOrderCountAsync end");
            return 3;
        }, executor);
    }

    static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private static void log(String message) {
        System.out.println(Thread.currentThread().getName() + " | " + message);
    }
}
