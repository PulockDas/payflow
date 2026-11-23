package com.pulock.payflow.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "orders") // "order" is a reserved word in SQL
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Many orders can point to the same product.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    // Total in cents, calculated from the DB price when the order is created.
    @Column(nullable = false)
    private long amountCents;

    @Column(nullable = false, length = 3)
    private String currency;

    // STRING stores "PENDING", not 0: safe if the enum order ever changes.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Order() {
    }

    public Order(Product product, int quantity, long amountCents, String currency) {
        this.product = product;
        this.quantity = quantity;
        this.amountCents = amountCents;
        this.currency = currency;
        this.status = OrderStatus.PENDING; // every order starts as PENDING
        this.createdAt = Instant.now();
    }

    // Status changes (PAID, REFUNDED, ...) come in M5/M6.

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public String getCurrency() {
        return currency;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
