package com.pulock.payflow.controller;

import com.pulock.payflow.model.Order;
import com.pulock.payflow.model.OrderStatus;

// What the success / cancel pages see. No internal fields (e.g. Stripe session id).
public record OrderStatusResponse(
        Long orderId,
        OrderStatus status,
        String productName,
        int quantity,
        long amountCents,
        String currency
) {

    // Needs order.product loaded (repository methods use @EntityGraph).
    public static OrderStatusResponse from(Order order) {
        return new OrderStatusResponse(
                order.getId(),
                order.getStatus(),
                order.getProduct().getName(),
                order.getQuantity(),
                order.getAmountCents(),
                order.getCurrency()
        );
    }
}
