package com.pulock.payflow.repository;

import com.pulock.payflow.model.ProcessedStripeEvent;
import org.springframework.data.jpa.repository.JpaRepository;

// Id type is String: the Stripe event id ("evt_...").
public interface ProcessedStripeEventRepository extends JpaRepository<ProcessedStripeEvent, String> {
}
