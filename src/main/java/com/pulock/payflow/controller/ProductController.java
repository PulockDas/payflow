package com.pulock.payflow.controller;

import com.pulock.payflow.model.Product;
import com.pulock.payflow.repository.ProductRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductRepository productRepository;

    public ProductController(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    // GET /api/products -> JSON list of all products (Jackson converts them).
    @GetMapping
    public List<Product> list() {
        return productRepository.findAll();
    }
}
