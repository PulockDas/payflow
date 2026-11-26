package com.pulock.payflow.refund;

import com.pulock.payflow.model.Order;
import com.pulock.payflow.model.OrderStatus;
import com.pulock.payflow.repository.OrderRepository;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.RefundCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    private final OrderRepository orderRepository;

    public RefundService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    // Only asks Stripe for the refund. The order stays PAID until the
    // charge.refunded webhook marks it REFUNDED. No @Transactional: external call.
    public RefundResponse requestFullRefund(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Order " + orderId + " not found"));

        if (order.getStatus() != OrderStatus.PAID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Order " + orderId + " is " + order.getStatus() + "; only PAID orders can be refunded");
        }
        if (order.getStripePaymentIntentId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Order " + orderId + " has no Stripe PaymentIntent recorded");
        }

        RefundCreateParams refundParams = RefundCreateParams.builder()
                .setPaymentIntent(order.getStripePaymentIntentId())
                .putMetadata("order_id", order.getId().toString()) // visible in the dashboard
                .build();

        // Same key for the same order: a retry / double click returns the SAME refund
        // instead of creating a second one. Stripe remembers keys for 24 hours.
        RequestOptions idempotentRequest = RequestOptions.builder()
                .setIdempotencyKey("refund-order-" + order.getId())
                .build();

        try {
            Refund refund = Refund.create(refundParams, idempotentRequest);
            log.info("Refund {} requested for order {} (status {})",
                    refund.getId(), order.getId(), refund.getStatus());
            return new RefundResponse(order.getId(), refund.getId(), refund.getStatus());
        } catch (InvalidRequestException rejectedByStripe) {
            // e.g. already refunded (after the 24h key window) or invalid payment intent.
            log.warn("Stripe rejected refund for order {}: {}", order.getId(), rejectedByStripe.getMessage());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Stripe rejected the refund for this order");
        } catch (StripeException stripeException) {
            log.error("Stripe refund failed for order {}", order.getId(), stripeException);
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Payment provider unavailable, please try again");
        }
    }
}
