package org.example.project;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileAggregatorExerciseTest {
    @Test
    // @Disabled("과제 1: aggregate를 직접 구현한 뒤 이 줄을 제거하세요.")
    void combinesIndependentProfileData() {
        RecordingClients clients = new RecordingClients(false);
        ExecutorService db = Executors.newFixedThreadPool(2);
        ExecutorService api = Executors.newFixedThreadPool(1);
        ExecutorService redis = Executors.newFixedThreadPool(1);

        try {
            ProfileAggregator aggregator = new ProfileAggregator(clients, db, api, redis);

            ProfilePage page = aggregator.aggregate("user-1", Duration.ofSeconds(2)).join();

            assertEquals("user-1", page.user().userId());
            assertEquals(2, page.recentOrders().size());
            assertEquals(2, page.recommendations().size());
            assertEquals(3, page.couponCount());
            assertTrue(clients.usedMoreThanOneThread());
        } finally {
            db.shutdownNow();
            api.shutdownNow();
            redis.shutdownNow();
        }
    }

    @Test
    // @Disabled("과제 1-확장: 추천 API 실패 fallback을 구현한 뒤 이 줄을 제거하세요.")
    void usesEmptyRecommendationsWhenRecommendationClientFails() {
        RecordingClients clients = new RecordingClients(true);
        ExecutorService db = Executors.newFixedThreadPool(2);
        ExecutorService api = Executors.newFixedThreadPool(1);
        ExecutorService redis = Executors.newFixedThreadPool(1);

        try {
            ProfileAggregator aggregator = new ProfileAggregator(clients, db, api, redis);

            ProfilePage page = aggregator.aggregate("user-1", Duration.ofSeconds(2)).join();

            assertEquals(List.of(), page.recommendations());
            assertEquals(2, page.recentOrders().size());
        } finally {
            db.shutdownNow();
            api.shutdownNow();
            redis.shutdownNow();
        }
    }

    private static final class RecordingClients implements ProfileClients {
        private final boolean failRecommendations;
        private final java.util.Set<String> threadNames = java.util.concurrent.ConcurrentHashMap.newKeySet();

        private RecordingClients(boolean failRecommendations) {
            this.failRecommendations = failRecommendations;
        }

        @Override
        public UserProfile fetchUser(String userId) {
            record("user");
            sleep(100);
            return new UserProfile(userId, "Kim");
        }

        @Override
        public List<OrderSummary> fetchRecentOrders(String userId) {
            record("orders");
            sleep(150);
            return List.of(new OrderSummary("order-1", 10_000), new OrderSummary("order-2", 20_000));
        }

        @Override
        public List<Recommendation> fetchRecommendations(String userId) {
            record("recommendations");
            sleep(120);
            if (failRecommendations) {
                throw new IllegalStateException("recommendation api failed");
            }
            return List.of(new Recommendation("p-1", "Keyboard"), new Recommendation("p-2", "Mouse"));
        }

        @Override
        public int fetchCouponCount(String userId) {
            record("coupons");
            sleep(50);
            return 3;
        }

        boolean usedMoreThanOneThread() {
            return threadNames.size() > 1;
        }

        private void record(String operation) {
            String threadName = Thread.currentThread().getName();
            threadNames.add(threadName);
            System.out.println(threadName + " | " + operation);
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
}
