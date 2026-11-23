package com.pulock.payflow.checkout;

import com.pulock.payflow.model.Order;
import com.pulock.payflow.model.Product;
import com.pulock.payflow.repository.OrderRepository;
import com.pulock.payflow.repository.ProductRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final String appBaseUrl;

    public CheckoutService(ProductRepository productRepository,
                           OrderRepository orderRepository,
                           @Value("${app.base-url}") String appBaseUrl) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.appBaseUrl = appBaseUrl;
    }

    // No @Transactional: don't hold a DB transaction open during the Stripe call.
    public CheckoutResponse startCheckout(CheckoutRequest checkoutRequest) {
        Product product = productRepository.findById(checkoutRequest.productId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Product " + checkoutRequest.productId() + " not found"));

        int quantity = checkoutRequest.quantity();
        // Price from the DB, never the browser. multiplyExact throws on overflow.
        long totalAmountCents = Math.multiplyExact(product.getPriceCents(), quantity);

        Order order = orderRepository.save(
                new Order(product, quantity, totalAmountCents, product.getCurrency()));

        Session checkoutSession = createStripeCheckoutSession(order, product);

        order.attachStripeCheckoutSession(checkoutSession.getId());
        orderRepository.save(order);

        log.info("Order {} created with Stripe session {}", order.getId(), checkoutSession.getId());
        return new CheckoutResponse(order.getId(), checkoutSession.getUrl());
    }

    private Session createStripeCheckoutSession(Order order, Product product) {
        SessionCreateParams.LineItem.PriceData.ProductData stripeProductData =
                SessionCreateParams.LineItem.PriceData.ProductData.builder()
                        .setName(product.getName())
                        .build();

        SessionCreateParams.LineItem.PriceData stripePriceData =
                SessionCreateParams.LineItem.PriceData.builder()
                        .setCurrency(product.getCurrency())
                        .setUnitAmount(product.getPriceCents())
                        .setProductData(stripeProductData)
                        .build();

        SessionCreateParams.LineItem lineItem = SessionCreateParams.LineItem.builder()
                .setPriceData(stripePriceData)
                .setQuantity((long) order.getQuantity())
                .build();

        SessionCreateParams sessionParams = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .addLineItem(lineItem)
                // Our order id, visible on the session in the dashboard and in webhooks.
                .setClientReferenceId(order.getId().toString())
                // Static pages in resources/static/checkout. Stripe fills in {CHECKOUT_SESSION_ID}.
                .setSuccessUrl(appBaseUrl + "/checkout/success.html?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(appBaseUrl + "/checkout/cancel.html?order_id=" + order.getId())
                .build();

        try {
            return Session.create(sessionParams);
        } catch (StripeException stripeException) {
            log.error("Stripe session creation failed for order {}", order.getId(), stripeException);
            order.markCancelled();
            orderRepository.save(order);
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Payment provider unavailable, please try again");
        }
    }
}
