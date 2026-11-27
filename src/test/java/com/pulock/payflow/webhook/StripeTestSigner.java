package com.pulock.payflow.webhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.HexFormat;

// Test helper: builds a Stripe-Signature header exactly the way Stripe does, so tests
// can send fake webhook events that pass the REAL Webhook.constructEvent check.
//
// Stripe's recipe:
//   signedPayload = "<timestamp>.<raw JSON body>"
//   signature     = hex( HMAC-SHA256(key = webhook secret, message = signedPayload) )
//   header        = "t=<timestamp>,v1=<signature>"
final class StripeTestSigner {

    // Must match stripe.webhook-secret in src/test/resources/application-test.yml.
    static final String TEST_WEBHOOK_SECRET = "whsec_test_secret";

    private StripeTestSigner() {
    }

    // Signs with the test secret and the current time (Stripe rejects timestamps
    // older than 5 minutes, which protects against replayed requests).
    static String signatureHeader(String payload) {
        return signatureHeader(payload, TEST_WEBHOOK_SECRET);
    }

    // Lets a test sign with a WRONG secret, to check that we reject it.
    static String signatureHeader(String payload, String secret) {
        long timestamp = Instant.now().getEpochSecond();
        String signedPayload = timestamp + "." + payload;
        return "t=" + timestamp + ",v1=" + hmacSha256Hex(secret, signedPayload);
    }

    private static String hmacSha256Hex(String secret, String message) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = hmac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException impossible) {
            // HmacSHA256 is built into every JDK; this can't happen in practice.
            throw new IllegalStateException(impossible);
        }
    }
}
