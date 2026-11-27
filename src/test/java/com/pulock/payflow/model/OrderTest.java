package com.pulock.payflow.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


class OrderTest {

    private Order newPendingOrder() {
        Product mug = new Product("Coffee Mug", "12oz ceramic mug", 1299, "usd");
        return new Order(mug, 2, 2598, "usd");
    }

    @Test
    void newOrderStartsPending() {
        Order order = newPendingOrder();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void markPaidMovesPendingToPaid() {
        Order order = newPendingOrder();

        order.markPaid();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void markPaidTwiceIsRejected() {
        Order order = newPendingOrder();
        order.markPaid();

        assertThatThrownBy(() -> order.markPaid()).isInstanceOf(IllegalStateException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void markCancelledMovesPendingToCancelled() {
        Order order = newPendingOrder();
        order.markCancelled();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void paidOrderCannotBeCancelled() {
        Order order = newPendingOrder();
        order.markPaid();

        assertThatThrownBy(() -> order.markCancelled()).isInstanceOf(IllegalStateException.class);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void markRefundedMovesPaidToRefunded() {
        Order order = newPendingOrder();
        order.markPaid();
        order.markRefunded();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
    }

    @Test
    void pendingOrderCannotBeRefunded() {
        Order order = newPendingOrder();

        assertThatThrownBy(() -> order.markRefunded()).isInstanceOf(IllegalStateException.class);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void paymentIntentCanBeAttachedOnlyOnce() {
        Order order = newPendingOrder();

        order.attachStripePaymentIntent("pi_first");

        assertThatThrownBy(() -> order.attachStripePaymentIntent("pi_second")).isInstanceOf(IllegalStateException.class);

        assertThat(order.getStripePaymentIntentId()).isEqualTo("pi_first");
    }
}
