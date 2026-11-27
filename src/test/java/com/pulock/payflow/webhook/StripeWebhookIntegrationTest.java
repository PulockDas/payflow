package com.pulock.payflow.webhook;

import com.pulock.payflow.TestcontainersConfiguration;
import com.pulock.payflow.model.Order;
import com.pulock.payflow.model.OrderStatus;
import com.pulock.payflow.model.Product;
import com.pulock.payflow.repository.OrderRepository;
import com.pulock.payflow.repository.ProcessedStripeEventRepository;
import com.pulock.payflow.repository.ProductRepository;
import com.stripe.Stripe;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Full chain: HTTP request -> StripeWebhookController (real signature check)
// -> StripeWebhookService -> real Postgres (Testcontainers). Stripe itself is never called:
// we build the events ourselves and sign them with StripeTestSigner.
//
// NOT @Transactional on purpose: the webhook service commits its own transaction, and we
// want to read back what was really committed, exactly like production.
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class StripeWebhookIntegrationTest {

    private static final String WEBHOOK_URL = "/api/stripe/webhook";
    private static final long ORDER_AMOUNT_CENTS = 1299;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProcessedStripeEventRepository processedStripeEventRepository;

    @Test
    void invalidSignatureIsRejected() throws Exception {
        Order order = createPendingOrder();
        String eventId = newId("evt_");
        String payload = checkoutSessionEvent(eventId, "checkout.session.completed",
                order.getStripeCheckoutSessionId(), "paid", ORDER_AMOUNT_CENTS, newId("pi_"));

        // Signed with the WRONG secret, as an attacker without our secret would have to.
        postWebhook(payload, StripeTestSigner.signatureHeader(payload, "whsec_attacker_guess"))
                .andExpect(status().isBadRequest());

        // Nothing happened: order untouched, event not recorded.
        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(processedStripeEventRepository.existsById(eventId)).isFalse();
    }

    @Test 
    void completedAndPaidSessionMarksOrderPaid() throws Exception {
        Order order = createPendingOrder();
        String eventId = newId("evt_");
        String payload = checkoutSessionEvent(eventId, "checkout.session.completed", order.getStripeCheckoutSessionId(), "paid", ORDER_AMOUNT_CENTS, newId("pi_"));
        
        postWebhook(payload, StripeTestSigner.signatureHeader(payload, StripeTestSigner.TEST_WEBHOOK_SECRET)).andExpect(status().isOk());

        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(processedStripeEventRepository.existsById(eventId)).isTrue();
    }

    @Test
    void duplicateEventIsProcessedOnlyOnce() throws Exception {
        Order firstOrder = createPendingOrder();
        Order secondOrder = createPendingOrder();
        String eventId = newId("evt_");

        postSignedWebhook(checkoutSessionEvent(eventId, "checkout.session.completed",
                firstOrder.getStripeCheckoutSessionId(), "paid", ORDER_AMOUNT_CENTS, newId("pi_")))
                .andExpect(status().isOk());

        // Same event id, but pointing at a different PENDING order. If we deduplicated by
        // anything other than the event id, the second order would become PAID.
        // (Re-sending the identical payload would not prove much: the first order is no
        // longer PENDING, so the status check alone would already stop it.)
        postSignedWebhook(checkoutSessionEvent(eventId, "checkout.session.completed",
                secondOrder.getStripeCheckoutSessionId(), "paid", ORDER_AMOUNT_CENTS, newId("pi_")))
                .andExpect(status().isOk());

        assertThat(reload(firstOrder).getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(reload(secondOrder).getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void amountMismatchDoesNotMarkOrderPaid() throws Exception {
        Order order = createPendingOrder();
        String eventId = newId("evt_");
        String payload = checkoutSessionEvent(eventId, "checkout.session.completed",
                order.getStripeCheckoutSessionId(), "paid", ORDER_AMOUNT_CENTS + 1, newId("pi_"));

        // 200, not an error: retrying would never fix a wrong amount, so Stripe should stop.
        postSignedWebhook(payload).andExpect(status().isOk());

        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(processedStripeEventRepository.existsById(eventId)).isTrue();
    }

    @Test
    void expiredSessionCancelsPendingOrder() throws Exception {
        Order order = createPendingOrder();
        String payload = checkoutSessionEvent(newId("evt_"), "checkout.session.expired",
                order.getStripeCheckoutSessionId(), "unpaid", ORDER_AMOUNT_CENTS, null);

        postSignedWebhook(payload).andExpect(status().isOk());

        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void fullRefundMarksPaidOrderRefunded() throws Exception {
        String paymentIntentId = newId("pi_");
        Order order = createPaidOrder(paymentIntentId);
        String payload = chargeRefundedEvent(newId("evt_"), paymentIntentId,
                ORDER_AMOUNT_CENTS, ORDER_AMOUNT_CENTS, true);

        postSignedWebhook(payload).andExpect(status().isOk());

        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.REFUNDED);
    }

    @Test
    void partialRefundKeepsOrderPaid() throws Exception {
        String paymentIntentId = newId("pi_");
        Order order = createPaidOrder(paymentIntentId);
        String payload = chargeRefundedEvent(newId("evt_"), paymentIntentId,
                ORDER_AMOUNT_CENTS, 500, false);

        postSignedWebhook(payload).andExpect(status().isOk());

        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.PAID);
    }

    // ---------------------------------------------------------------------------------
    // Helpers: database setup
    // ---------------------------------------------------------------------------------

    // A PENDING order with its own unique Stripe session id. Unique ids keep tests
    // independent: the database is shared by all tests in this class and is not wiped.
    private Order createPendingOrder() {
        Product product = productRepository.save(
                new Product("Test Mug", "for tests", ORDER_AMOUNT_CENTS, "usd"));
        Order order = new Order(product, 1, ORDER_AMOUNT_CENTS, "usd");
        order.attachStripeCheckoutSession(newId("cs_test_"));
        return orderRepository.save(order);
    }

    // A PAID order with a PaymentIntent, as if checkout had already succeeded.
    // Setup shortcut for the refund tests (the "paid" path itself is tested by the webhook).
    private Order createPaidOrder(String paymentIntentId) {
        Order order = createPendingOrder();
        order.markPaid();
        order.attachStripePaymentIntent(paymentIntentId);
        return orderRepository.save(order);
    }

    // Reads the order again from the database, to see what the webhook committed.
    private Order reload(Order order) {
        return orderRepository.findById(order.getId()).orElseThrow();
    }

    // ---------------------------------------------------------------------------------
    // Helpers: HTTP
    // ---------------------------------------------------------------------------------

    // Sends the payload correctly signed with the test secret.
    private ResultActions postSignedWebhook(String payload) throws Exception {
        return postWebhook(payload, StripeTestSigner.signatureHeader(payload));
    }

    private ResultActions postWebhook(String payload, String signatureHeader) throws Exception {
        return mockMvc.perform(post(WEBHOOK_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Stripe-Signature", signatureHeader)
                .content(payload));
    }

    // ---------------------------------------------------------------------------------
    // Helpers: fake Stripe events (only the fields our code reads)
    // ---------------------------------------------------------------------------------

    // For checkout.session.completed and checkout.session.expired.
    // paymentIntentId may be null (expired sessions have none).
    private static String checkoutSessionEvent(String eventId, String eventType, String sessionId,
                                               String paymentStatus, long amountTotal,
                                               String paymentIntentId) {
        String dataObject = """
                {
                  "id": "%s",
                  "object": "checkout.session",
                  "payment_status": "%s",
                  "amount_total": %d,
                  "payment_intent": %s
                }""".formatted(sessionId, paymentStatus, amountTotal, jsonStringOrNull(paymentIntentId));
        return event(eventId, eventType, dataObject);
    }

    // For charge.refunded. Full refund: amountRefunded == amount and fullyRefunded == true.
    private static String chargeRefundedEvent(String eventId, String paymentIntentId,
                                              long amount, long amountRefunded, boolean fullyRefunded) {
        String dataObject = """
                {
                  "id": "%s",
                  "object": "charge",
                  "payment_intent": "%s",
                  "amount": %d,
                  "amount_refunded": %d,
                  "refunded": %b
                }""".formatted(newId("ch_"), paymentIntentId, amount, amountRefunded, fullyRefunded);
        return event(eventId, "charge.refunded", dataObject);
    }

    // The Event envelope. api_version = the SDK's own version, so the SDK reads
    // data.object directly (the "normal" path in extractDataObject).
    private static String event(String eventId, String eventType, String dataObject) {
        return """
                {
                  "id": "%s",
                  "object": "event",
                  "api_version": "%s",
                  "type": "%s",
                  "data": { "object": %s }
                }""".formatted(eventId, Stripe.API_VERSION, eventType, dataObject);
    }

    private static String jsonStringOrNull(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    // e.g. newId("evt_") -> "evt_3f2a9c1e..."
    private static String newId(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }
}
