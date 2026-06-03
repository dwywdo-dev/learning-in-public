package org.example.project;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class ProfileAggregator {
    private final ProfileClients clients;
    private final Executor dbExecutor;
    private final Executor apiExecutor;
    private final Executor redisExecutor;

    public ProfileAggregator(
            ProfileClients clients,
            Executor dbExecutor,
            Executor apiExecutor,
            Executor redisExecutor
    ) {
        this.clients = clients;
        this.dbExecutor = dbExecutor;
        this.apiExecutor = apiExecutor;
        this.redisExecutor = redisExecutor;
    }

    public CompletableFuture<ProfilePage> aggregate(String userId, Duration deadline) {
        CompletableFuture<UserProfile> userProfileFuture = CompletableFuture.supplyAsync(() -> clients.fetchUser(userId), dbExecutor);

        CompletableFuture<List<OrderSummary>> orderSummariesFuture = CompletableFuture.supplyAsync(() -> clients.fetchRecentOrders(userId), dbExecutor);

        CompletableFuture<List<Recommendation>> recommendationsFuture = CompletableFuture.supplyAsync(
                () -> clients.fetchRecommendations(userId), apiExecutor
        ).exceptionally(ex -> List.of());

        CompletableFuture<Integer> couponCountFuture = CompletableFuture.supplyAsync(() -> clients.fetchCouponCount(userId), redisExecutor);

        CompletableFuture<Void> allFuture = CompletableFuture.allOf(userProfileFuture, orderSummariesFuture, recommendationsFuture, couponCountFuture);

        return allFuture.thenApply(ignored -> new ProfilePage(
                userProfileFuture.join(),
                orderSummariesFuture.join(),
                recommendationsFuture.join(),
                couponCountFuture.join()
        ));
    }
}
