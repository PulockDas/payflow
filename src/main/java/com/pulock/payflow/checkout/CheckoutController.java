package com.pulock.payflow.checkout;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/checkout")
public class CheckoutController {

    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    // @Valid: invalid body -> 400 before this method runs.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED) // 201: a new order was created
    public CheckoutResponse startCheckout(@Valid @RequestBody CheckoutRequest checkoutRequest) {
        return checkoutService.startCheckout(checkoutRequest);
    }
}
