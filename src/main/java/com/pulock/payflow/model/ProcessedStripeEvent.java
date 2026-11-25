package com.pulock.payflow.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

// One row per handled Stripe event. The event id is the primary key, so the DB
// rejects the same event twice (idempotency).
@Entity
@Table(name = "processed_stripe_events")
public class ProcessedStripeEvent implements Persistable<String> {

    @Id
    @Column(length = 255)
    private String stripeEventId; // "evt_..."

    @Column(nullable = false)
    private String eventType; // e.g. "checkout.session.completed"

    @Column(nullable = false, updatable = false)
    private Instant processedAt;

    // Persistable: makes save() INSERT (duplicate -> error) instead of merge (silent update).
    @Transient
    private boolean newEntity = true;

    protected ProcessedStripeEvent() {
    }

    public ProcessedStripeEvent(String stripeEventId, String eventType) {
        this.stripeEventId = stripeEventId;
        this.eventType = eventType;
        this.processedAt = Instant.now();
    }

    @Override
    public String getId() {
        return stripeEventId;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.newEntity = false;
    }

    public String getEventType() {
        return eventType;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
