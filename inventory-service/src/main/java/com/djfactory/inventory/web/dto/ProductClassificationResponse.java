package com.djfactory.inventory.web.dto;

import com.djfactory.inventory.service.ClassificationService.ProductClassification;

import java.math.BigDecimal;

public record ProductClassificationResponse(
        Long productId,
        String sku,
        String name,
        String abcClass,
        String xyzClass,
        String combinedClass,
        BigDecimal annualConsumptionValue,
        Double coefficientOfVariation
) {
    public static ProductClassificationResponse from(ProductClassification c) {
        return new ProductClassificationResponse(
                c.productId(),
                c.sku(),
                c.name(),
                c.abcClass(),
                c.xyzClass(),
                c.combinedClass(),
                c.annualConsumptionValue(),
                c.coefficientOfVariation()
        );
    }
}
