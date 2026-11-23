package com.pulock.payflow.repository;

import com.pulock.payflow.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
