package com.pulock.payflow.repository;

import com.pulock.payflow.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

// Spring Data generates the implementation: findAll, findById, save, count, ...
public interface ProductRepository extends JpaRepository<Product, Long> {
}
