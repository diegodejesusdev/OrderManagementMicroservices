package com.djfactory.inventory.web;

import com.djfactory.inventory.service.ProductService;
import com.djfactory.inventory.web.dto.CreateProductRequest;
import com.djfactory.inventory.web.dto.ProductClassificationResponse;
import com.djfactory.inventory.web.dto.ProductResponse;
import com.djfactory.inventory.web.dto.StockAdjustmentRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public List<ProductResponse> list() {
        return productService.findAll().stream().map(ProductResponse::from).toList();
    }

    @GetMapping("/classification")
    public List<ProductClassificationResponse> classification() {
        return productService.classifyCatalog().stream()
                .map(ProductClassificationResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable Long id) {
        return ProductResponse.from(productService.findById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody CreateProductRequest request) {
        return ProductResponse.from(productService.create(request));
    }

    @PatchMapping("/{id}/stock")
    public ProductResponse adjustStock(
            @PathVariable Long id,
            @Valid @RequestBody StockAdjustmentRequest request
    ) {
        return ProductResponse.from(productService.adjustStock(id, request.delta()));
    }
}
