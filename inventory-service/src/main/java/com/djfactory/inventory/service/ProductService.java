package com.djfactory.inventory.service;

import com.djfactory.inventory.domain.Product;
import com.djfactory.inventory.repository.ProductRepository;
import com.djfactory.inventory.web.dto.CreateProductRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ClassificationService classificationService;

    public List<Product> findAll() {
        return productRepository.findAll();
    }

    public Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("product " + id + " not found"));
    }

    @Transactional
    public Product create(CreateProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new DuplicateSkuException("sku already in use: " + request.sku());
        }

        List<Integer> demand = request.demandObservations() == null
                ? new ArrayList<>()
                : new ArrayList<>(request.demandObservations());

        Product product = Product.builder()
                .sku(request.sku())
                .name(request.name())
                .unitPrice(request.unitPrice())
                .stockQuantity(request.stockQuantity())
                .category(request.category())
                .demandObservations(demand)
                .build();

        return productRepository.save(product);
    }

    @Transactional
    public Product adjustStock(Long id, int delta) {
        Product product = findById(id);
        long newStock = (long) product.getStockQuantity() + delta;
        if (newStock < 0) {
            throw new InsufficientStockException(
                    "stock adjustment would drop below zero: current="
                            + product.getStockQuantity() + ", delta=" + delta);
        }
        product.setStockQuantity((int) newStock);
        return productRepository.save(product);
    }

    public List<ClassificationService.ProductClassification> classifyCatalog() {
        return classificationService.classify(productRepository.findAll());
    }
}
