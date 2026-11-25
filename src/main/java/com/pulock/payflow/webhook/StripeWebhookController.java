package com.pulock.payflow.webhook;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stripe/webhook")
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    private final StripeWebhookService stripeWebhookService;
    private final String webhookSigningSecret;

    public StripeWebhookController(StripeWebhookService stripeWebhookService,
                                   @Value("${stripe.webhook-secret}") String webhookSigningSecret) {
        if (webhookSigningSecret == null || webhookSigningSecret.isBlank()) {
            throw new IllegalStateException(
                    "Stripe webhook secret is missing. Set STRIPE_WEBHOOK_SECRET in .env "
                            + "(from `stripe listen`).");
        }
        this.stripeWebhookService = stripeWebhookService;
        this.webhookSigningSecret = webhookSigningSecret;
    }

    // Raw String body: the signature is computed over the exact bytes Stripe sent.
    @PostMapping
    public ResponseEntity<Void> receiveStripeEvent(@RequestBody String rawPayload,
                                                   @RequestHeader("Stripe-Signature") String signatureHeader) {
        Event verifiedEvent;
        try {
            verifiedEvent = Webhook.constructEvent(rawPayload, signatureHeader, webhookSigningSecret);
        } catch (SignatureVerificationException invalidSignature) {
            log.warn("Rejected Stripe webhook: invalid signature");
            return ResponseEntity.badRequest().build();
        }

        stripeWebhookService.handleEvent(verifiedEvent);
        // 2xx tells Stripe "received"; anything else (or no answer) makes Stripe retry.
        return ResponseEntity.ok().build();
    }
}
