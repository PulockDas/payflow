package com.pulock.payflow.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    // Money is always a long in cents: $19.99 = 1999.
    @Column(nullable = false)
    private long priceCents;

    // Lowercase ISO code, as Stripe expects (e.g. "usd").
    @Column(nullable = false, length = 3)
    private String currency;

    // JPA needs a no-args constructor; protected so our code doesn't use it.
    protected Product() {
    }

    public Product(String name, String description, long priceCents, String currency) {
        this.name = name;
        this.description = description;
        this.priceCents = priceCents;
        this.currency = currency;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public long getPriceCents() {
        return priceCents;
    }

    public String getCurrency() {
        return currency;
    }
}
