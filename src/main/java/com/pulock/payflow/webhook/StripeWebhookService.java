package com.pulock.payflow.webhook;

import com.pulock.payflow.model.Order;
import com.pulock.payflow.model.OrderStatus;
import com.pulock.payflow.model.ProcessedStripeEvent;
import com.pulock.payflow.repository.OrderRepository;
import com.pulock.payflow.repository.ProcessedStripeEventRepository;
import com.stripe.exception.EventDataObjectDeserializationException;
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

    private final OrderRepository orderRepository;
    private final ProcessedStripeEventRepository processedStripeEventRepository;

    public StripeWebhookService(OrderRepository orderRepository, ProcessedStripeEventRepository processedStripeEventRepository) {
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
            default -> log.info("Ignoring Stripe event type {}", event.getType());
        }
    }

    private void handleCheckoutSessionCompleted(Event event) {
        Session checkoutSession = extractCheckoutSession(event);

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
        log.info("Order {} marked PAID (session {}, event {})",
                order.getId(), checkoutSession.getId(), event.getId());
    }

    private void handleCheckoutSessionExpired(Event event) {
        Session checkoutSession = extractCheckoutSession(event);

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

    private Session extractCheckoutSession(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();

        Optional<StripeObject> dataObject = deserializer.getObject();
        if (dataObject.isPresent()) {
            return (Session) dataObject.get();
        }

        // Empty when the event's API version differs from the SDK's: parse it anyway.
        try {
            return (Session) deserializer.deserializeUnsafe();
        } catch (EventDataObjectDeserializationException deserializationFailure) {
            throw new IllegalStateException(
                    "Could not read checkout session from Stripe event " + event.getId(), deserializationFailure);
        }
    }
}
