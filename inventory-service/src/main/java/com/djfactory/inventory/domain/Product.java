package com.djfactory.inventory.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "products",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_products_sku", columnNames = "sku")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String sku;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    @Column(length = 100)
    private String category;

    // Series of past monthly demand observations. Used to compute the
    // coefficient of variation for XYZ classification. Kept as a plain
    // integer collection on purpose — a full time-series entity is out of
    // scope for this phase.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "product_demand",
        joinColumns = @JoinColumn(name = "product_id"),
        foreignKey = @ForeignKey(name = "fk_product_demand_product")
    )
    @OrderColumn(name = "period_index")
    @Column(name = "quantity", nullable = false)
    @Builder.Default
    private List<Integer> demandObservations = new ArrayList<>();
}
