package com.pulock.payflow.refund;

// refundStatus is Stripe's: "pending", "succeeded", "failed", ...
public record RefundResponse(
        Long orderId,
        String stripeRefundId,
        String refundStatus
) {
}
