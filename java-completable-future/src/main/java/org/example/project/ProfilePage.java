package org.example.project;

import java.util.List;

public record ProfilePage(
    UserProfile user,
    List<OrderSummary> recentOrders,
    List<Recommendation> recommendations,
    int couponCount
) {
}

record UserProfile(String userId, String name) {
}

record OrderSummary(String orderId, int amount) {
}

record Recommendation(String productId, String name) {
}
