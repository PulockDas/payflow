package com.pulock.payflow.model;

/**
 * Lifecycle of an order:
 *
 *   PENDING --(verified webhook: payment succeeded)--> PAID --(refund)--> REFUNDED
 *      \
 *       --(checkout abandoned / session expired)--> CANCELLED
 */
public enum OrderStatus {
    PENDING,   // order created, customer is on the Stripe Checkout page
    PAID,      // set ONLY by a verified Stripe webhook (M5)
    CANCELLED, // never paid
    REFUNDED   // money returned (M6)
}
