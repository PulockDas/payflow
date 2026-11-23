package com.pulock.payflow.config;

import com.pulock.payflow.model.Product;
import com.pulock.payflow.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

// Runs once after startup. Inserts demo products only if the table is empty,
// so restarting the app never creates duplicates.
@Component
public class ProductSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductSeeder.class);

    private final ProductRepository productRepository;

    public ProductSeeder(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public void run(String... args) {
        if (productRepository.count() > 0) {
            return;
        }
        productRepository.saveAll(List.of(
                new Product("Coffee Mug", "12oz ceramic mug", 1299, "usd"),
                new Product("T-Shirt", "Cotton tee, unisex", 1999, "usd"),
                new Product("Sticker Pack", "5 vinyl stickers", 499, "usd")
        ));
        log.info("Seeded 3 demo products");
    }
}
