package com.pulock.payflow.checkout;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// What the browser sends. No price: it always comes from the database.
public record CheckoutRequest(
        @NotNull Long productId,
        @NotNull @Min(1) @Max(10) Integer quantity
) {
}
