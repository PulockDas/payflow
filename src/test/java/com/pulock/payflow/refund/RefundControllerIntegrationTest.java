package com.pulock.payflow.refund;

import com.pulock.payflow.TestcontainersConfiguration;
import com.pulock.payflow.model.Order;
import com.pulock.payflow.model.OrderStatus;
import com.pulock.payflow.model.Product;
import com.pulock.payflow.repository.OrderRepository;
import com.pulock.payflow.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Tests the refund endpoint's guards: every case here is rejected BEFORE RefundService
// calls Stripe, so no Stripe mock is needed. The happy path (a real refund) needs Stripe
// and was tested by hand in M6.
//
// Same annotations as StripeWebhookIntegrationTest -> Spring reuses the same cached
// context and Postgres container.
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class RefundControllerIntegrationTest {

    // Must match app.admin-token in src/test/resources/application-test.yml.
    private static final String TEST_ADMIN_TOKEN = "test-admin-token";
    private static final long ORDER_AMOUNT_CENTS = 1299;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    // ---- 401: authentication is checked first ----

    @Test
    void missingAdminTokenIsRejected() throws Exception {
        Order paidOrder = createPaidOrderWithPaymentIntent();

        mockMvc.perform(post(refundUrl(paidOrder.getId())))   // no X-Admin-Token header
                .andExpect(status().isUnauthorized());

        assertThat(reload(paidOrder).getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void wrongAdminTokenIsRejected() throws Exception {
        Order paidOrder = createPaidOrderWithPaymentIntent();

        mockMvc.perform(post(refundUrl(paidOrder.getId())).header("X-Admin-Token", "wrong-token"))
                .andExpect(status().isUnauthorized());

        assertThat(reload(paidOrder).getStatus()).isEqualTo(OrderStatus.PAID);
    }

    // A wrong token must get 401 even for an order that doesn't exist: otherwise an
    // attacker could find out which order ids exist (404 vs 409) without the token.
    @Test
    void wrongAdminTokenIsRejectedBeforeOrderLookup() throws Exception {
        mockMvc.perform(post(refundUrl(Long.MAX_VALUE)).header("X-Admin-Token", "wrong-token"))
                .andExpect(status().isUnauthorized());
    }

    // ---- 404 / 409: valid token, but the order can't be refunded ----

    @Test
    void unknownOrderReturnsNotFound() throws Exception {
        postRefundAsAdmin(Long.MAX_VALUE)
                .andExpect(status().isNotFound());
    }

    @Test
    void pendingOrderCannotBeRefunded() throws Exception {
        Order pendingOrder = createPendingOrder();

        postRefundAsAdmin(pendingOrder.getId())
                .andExpect(status().isConflict());

        assertThat(reload(pendingOrder).getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    // Defensive case: PAID but no PaymentIntent recorded (should not happen after M6,
    // but older or hand-edited rows could look like this). We can't refund without it.
    @Test
    void paidOrderWithoutPaymentIntentCannotBeRefunded() throws Exception {
        Order order = createPendingOrder();
        order.markPaid();
        Order paidWithoutPaymentIntent = orderRepository.save(order);

        postRefundAsAdmin(paidWithoutPaymentIntent.getId())
                .andExpect(status().isConflict());

        assertThat(reload(paidWithoutPaymentIntent).getStatus()).isEqualTo(OrderStatus.PAID);
    }

    // ---------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------

    private ResultActions postRefundAsAdmin(Long orderId) throws Exception {
        MockHttpServletRequestBuilder request = post(refundUrl(orderId))
                .header("X-Admin-Token", TEST_ADMIN_TOKEN);
        return mockMvc.perform(request);
    }

    private static String refundUrl(Long orderId) {
        return "/api/orders/" + orderId + "/refund";
    }

    private Order createPendingOrder() {
        Product product = productRepository.save(
                new Product("Test Mug", "for tests", ORDER_AMOUNT_CENTS, "usd"));
        Order order = new Order(product, 1, ORDER_AMOUNT_CENTS, "usd");
        order.attachStripeCheckoutSession(newId("cs_test_"));
        return orderRepository.save(order);
    }

    private Order createPaidOrderWithPaymentIntent() {
        Order order = createPendingOrder();
        order.markPaid();
        order.attachStripePaymentIntent(newId("pi_"));
        return orderRepository.save(order);
    }

    private Order reload(Order order) {
        return orderRepository.findById(order.getId()).orElseThrow();
    }

    private static String newId(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }
}
