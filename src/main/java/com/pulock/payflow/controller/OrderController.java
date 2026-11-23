package com.pulock.payflow.controller;

import com.pulock.payflow.repository.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// Read-only: these endpoints never change an order.
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderRepository orderRepository;

    public OrderController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    // Used by the success page.
    @GetMapping("/by-session/{stripeCheckoutSessionId}")
    public OrderStatusResponse getOrderByCheckoutSession(@PathVariable String stripeCheckoutSessionId) {
        return orderRepository.findByStripeCheckoutSessionId(stripeCheckoutSessionId)
                .map(OrderStatusResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    // Used by the cancel page.
    @GetMapping("/{orderId}")
    public OrderStatusResponse getOrderById(@PathVariable Long orderId) {
        return orderRepository.findWithProductById(orderId)
                .map(OrderStatusResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }
}
