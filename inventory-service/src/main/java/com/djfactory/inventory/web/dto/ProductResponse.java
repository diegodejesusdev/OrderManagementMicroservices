package com.djfactory.inventory.web.dto;

import com.djfactory.inventory.domain.Product;

import java.math.BigDecimal;
import java.util.List;

public record ProductResponse(
        Long id,
        String sku,
        String name,
        BigDecimal unitPrice,
        int stockQuantity,
        String category,
        List<Integer> demandObservations
) {
    public static ProductResponse from(Product p) {
        return new ProductResponse(
                p.getId(),
                p.getSku(),
                p.getName(),
                p.getUnitPrice(),
                p.getStockQuantity(),
                p.getCategory(),
                List.copyOf(p.getDemandObservations())
        );
    }
}
