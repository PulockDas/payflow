package com.pulock.payflow.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StripeConfig {

    private static final Logger log = LoggerFactory.getLogger(StripeConfig.class);

    private final String secretKey;

    // stripe.secret-key comes from application.yml, which reads STRIPE_SECRET_KEY.
    public StripeConfig(@Value("${stripe.secret-key}") String secretKey) {
        this.secretKey = secretKey;
    }

    @PostConstruct
    void init() {
        if (secretKey == null || secretKey.isBlank()) {
            // Throwing here aborts startup: better than failing on the first checkout.
            throw new IllegalStateException(
                    "Stripe secret key is missing. Set STRIPE_SECRET_KEY in .env "
                            + "(local) or as an environment variable.");
        }

        Stripe.apiKey = secretKey;

        // Never log the full key: only the last 4 characters.
        log.info("Stripe API key configured (ending in ...{})", lastFour(secretKey));
    }

    private static String lastFour(String key) {
        return key.length() > 4 ? key.substring(key.length() - 4) : "****";
    }
}
