package com.pulock.payflow.webhook;

import com.pulock.payflow.model.Order;
import com.pulock.payflow.model.OrderStatus;
import com.pulock.payflow.model.ProcessedStripeEvent;
import com.pulock.payflow.repository.OrderRepository;
import com.pulock.payflow.repository.ProcessedStripeEventRepository;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

@Service
public class StripeWebhookService {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookService.class);

    private static final String CHECKOUT_SESSION_COMPLETED = "checkout.session.completed";
    private static final String CHECKOUT_SESSION_EXPIRED = "checkout.session.expired";
    private static final String CHARGE_REFUNDED = "charge.refunded";

    private final OrderRepository orderRepository;
    private final ProcessedStripeEventRepository processedStripeEventRepository;

    public StripeWebhookService(OrderRepository orderRepository,
                                ProcessedStripeEventRepository processedStripeEventRepository) {
        this.orderRepository = orderRepository;
        this.processedStripeEventRepository = processedStripeEventRepository;
    }

    // One transaction: the order update is committed together (or rolled back together).
    @Transactional
    public void handleEvent(Event event) {
        log.info("Received Stripe event {} ({})", event.getId(), event.getType());

        if (processedStripeEventRepository.existsById(event.getId())) {
            log.info("Stripe event {} already processed, skipping", event.getId());
            return;
        }

        processedStripeEventRepository.saveAndFlush(new ProcessedStripeEvent(event.getId(), event.getType()));

        switch (event.getType()) {
            case CHECKOUT_SESSION_COMPLETED -> handleCheckoutSessionCompleted(event);
            case CHECKOUT_SESSION_EXPIRED -> handleCheckoutSessionExpired(event);
            case CHARGE_REFUNDED -> handleChargeRefunded(event);
            default -> log.info("Ignoring Stripe event type {}", event.getType());
        }
    }

    private void handleCheckoutSessionCompleted(Event event) {
        Session checkoutSession = extractDataObject(event, Session.class);

        // Not ours (e.g. `stripe trigger` test events): log and return 200, so Stripe stops retrying.
        Optional<Order> matchingOrder = orderRepository.findByStripeCheckoutSessionId(checkoutSession.getId());
        if (matchingOrder.isEmpty()) {
            log.warn("No order for Stripe session {} (event {}), ignoring",
                    checkoutSession.getId(), event.getId());
            return;
        }
        Order order = matchingOrder.get();

        // Error, not warn: Stripe says it was paid, but we can't record it. Needs a human.
        if (order.getStatus() != OrderStatus.PENDING) {
            log.error("Order {} is {}, cannot mark PAID (session {}, event {})",
                    order.getId(), order.getStatus(), checkoutSession.getId(), event.getId());
            return;
        }

        if (!"paid".equals(checkoutSession.getPaymentStatus())) {
            log.info("Session {} completed but payment status is '{}'; order {} stays {}",
                    checkoutSession.getId(), checkoutSession.getPaymentStatus(), order.getId(), order.getStatus());
            return;
        }

        // Objects.equals: Long (Stripe) vs long (ours) without the == / null traps.
        if (!Objects.equals(checkoutSession.getAmountTotal(), order.getAmountCents())) {
            log.error("Amount mismatch for order {}: Stripe charged {}, order expects {}. Not marking PAID.",
                    order.getId(), checkoutSession.getAmountTotal(), order.getAmountCents());
            return;
        }

        order.markPaid();
        // No save() needed: inside @Transactional, Hibernate saves changes to loaded entities on commit.
        order.attachStripePaymentIntent(checkoutSession.getPaymentIntent());
        log.info("Order {} marked PAID (session {}, event {}, paymentIntent {})",
                order.getId(), checkoutSession.getId(), event.getId(), checkoutSession.getPaymentIntent());
    }

    private void handleCheckoutSessionExpired(Event event) {
        Session checkoutSession = extractDataObject(event, Session.class);

        // Not ours (e.g. `stripe trigger` test events): log and return 200, so Stripe stops retrying.
        Optional<Order> matchingOrder = orderRepository.findByStripeCheckoutSessionId(checkoutSession.getId());
        if (matchingOrder.isEmpty()) {
            log.warn("No order for Stripe session {} (event {}), ignoring",
                    checkoutSession.getId(), event.getId());
            return;
        }
        Order order = matchingOrder.get();

        if (order.getStatus() == OrderStatus.PENDING) {
            order.markCancelled();
            log.info("Order {} marked CANCELLED: checkout session {} expired (event {})",
                    order.getId(), checkoutSession.getId(), event.getId());
        } else {
            // Normal, e.g. already cancelled: nothing to do.
            log.info("Session {} expired; order {} is {}, left unchanged",
                    checkoutSession.getId(), order.getId(), order.getStatus());
        }
    }

    // charge.refunded carries a Charge (not a Session); the Charge knows its PaymentIntent.
    private void handleChargeRefunded(Event event) {
        Charge refundedCharge = extractDataObject(event, Charge.class);
        String paymentIntentId = refundedCharge.getPaymentIntent();

        // Not ours (e.g. `stripe trigger` test events): log and return 200.
        Optional<Order> matchingOrder = orderRepository.findByStripePaymentIntentId(paymentIntentId);
        if (matchingOrder.isEmpty()) {
            log.warn("No order for Stripe PaymentIntent {} (event {}), ignoring",
                    paymentIntentId, event.getId());
            return;
        }
        Order order = matchingOrder.get();

        // charge.refunded also fires for partial refunds; refunded == true means fully refunded.
        if (!Boolean.TRUE.equals(refundedCharge.getRefunded())) {
            log.info("Charge {} partially refunded ({} of {} cents); order {} stays {}",
                    refundedCharge.getId(), refundedCharge.getAmountRefunded(), refundedCharge.getAmount(),
                    order.getId(), order.getStatus());
            return;
        }

        if (order.getStatus() == OrderStatus.REFUNDED) {
            log.info("Order {} already REFUNDED (event {})", order.getId(), event.getId());
            return;
        }

        // Error: Stripe refunded money for an order we don't consider PAID. Needs a human.
        if (order.getStatus() != OrderStatus.PAID) {
            log.error("Order {} is {}, cannot mark REFUNDED (charge {}, event {})",
                    order.getId(), order.getStatus(), refundedCharge.getId(), event.getId());
            return;
        }

        order.markRefunded();
        log.info("Order {} marked REFUNDED (charge {}, paymentIntent {}, event {})",
                order.getId(), refundedCharge.getId(), paymentIntentId, event.getId());
    }

    // Reads the event's data object as the given type (Session, Charge, ...).
    private <T extends StripeObject> T extractDataObject(Event event, Class<T> expectedType) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();

        Optional<StripeObject> dataObject = deserializer.getObject();
        if (dataObject.isPresent()) {
            return expectedType.cast(dataObject.get());
        }

        // Empty when the event's API version differs from the SDK's: parse it anyway.
        try {
            return expectedType.cast(deserializer.deserializeUnsafe());
        } catch (EventDataObjectDeserializationException deserializationFailure) {
            throw new IllegalStateException(
                    "Could not read " + expectedType.getSimpleName() + " from Stripe event " + event.getId(),
                    deserializationFailure);
        }
    }
}
