package com.pulock.payflow.repository;

import com.pulock.payflow.model.Order;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // @EntityGraph: load the product in the same query (JOIN), not lazily later.

    @EntityGraph(attributePaths = "product")
    Optional<Order> findByStripeCheckoutSessionId(String stripeCheckoutSessionId);

    // "WithProduct" is just a readable label: Spring ignores words between find...By.
    @EntityGraph(attributePaths = "product")
    Optional<Order> findWithProductById(Long orderId);
}
