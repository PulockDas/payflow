package com.pulock.payflow.refund;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
public class RefundController {

    private final RefundService refundService;
    private final byte[] adminTokenBytes;

    public RefundController(RefundService refundService,
                            @Value("${app.admin-token}") String adminToken) {
        if (adminToken == null || adminToken.isBlank()) {
            throw new IllegalStateException("Admin token is missing. Set ADMIN_API_TOKEN in .env.");
        }
        this.refundService = refundService;
        this.adminTokenBytes = adminToken.getBytes(StandardCharsets.UTF_8);
    }

    // 202 Accepted: refund requested; the order becomes REFUNDED via the webhook.
    @PostMapping("/api/orders/{orderId}/refund")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RefundResponse requestRefund(@PathVariable Long orderId,
                                        @RequestHeader(name = "X-Admin-Token", required = false) String providedToken) {
        requireValidAdminToken(providedToken);
        return refundService.requestFullRefund(orderId);
    }

    // Constant-time comparison: timing doesn't reveal how many characters were right.
    private void requireValidAdminToken(String providedToken) {
        boolean isValid = providedToken != null
                && MessageDigest.isEqual(providedToken.getBytes(StandardCharsets.UTF_8), adminTokenBytes);
        if (!isValid) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing admin token");
        }
    }
}
