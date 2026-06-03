package org.example.project;

import java.util.List;

public interface ProfileClients {
    UserProfile fetchUser(String userId);

    List<OrderSummary> fetchRecentOrders(String userId);

    List<Recommendation> fetchRecommendations(String userId);

    int fetchCouponCount(String userId);
}
