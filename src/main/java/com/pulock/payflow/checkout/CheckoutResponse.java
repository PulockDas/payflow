package com.pulock.payflow.checkout;

// What we send back: the browser redirects to checkoutUrl.
public record CheckoutResponse(
        Long orderId,
        String checkoutUrl
) {
}
