package com.djfactory.inventory.service;

import com.djfactory.inventory.domain.Product;
import com.djfactory.inventory.service.ClassificationService.ProductClassification;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class ClassificationServiceTest {

    private final ClassificationService service = new ClassificationService();

    @Test
    void constantDemandSeriesHasCoefficientOfVariationZero() {
        Double cv = ClassificationService.coefficientOfVariation(List.of(10, 10, 10, 10));
        assertThat(cv).isEqualTo(0.0);
    }

    @Test
    void knownSeriesHasHandComputedCoefficientOfVariation() {
        // Series [5, 35]: mean 20, population variance = ((5-20)^2 + (35-20)^2)/2 = 225,
        // population stddev = 15, CV = 15/20 = 0.75.
        Double cv = ClassificationService.coefficientOfVariation(List.of(5, 35));
        assertThat(cv).isCloseTo(0.75, offset(1e-6));
        assertThat(ClassificationService.xyzClass(cv)).isEqualTo("Y");
    }

    @Test
    void singleObservationYieldsCvZeroAndClassX() {
        Double cv = ClassificationService.coefficientOfVariation(List.of(42));
        assertThat(cv).isEqualTo(0.0);
        assertThat(ClassificationService.xyzClass(cv)).isEqualTo("X");
    }

    @Test
    void undefinedCvIsReturnedForEmptyOrZeroMeanSeries() {
        assertThat(ClassificationService.coefficientOfVariation(List.of())).isNull();
        assertThat(ClassificationService.coefficientOfVariation(List.of(0, 0, 0))).isNull();
        assertThat(ClassificationService.xyzClass(null)).isEqualTo("N/A");
    }

    @Test
    void classifiesHandDesignedCatalogWithExpectedAbcAndXyz() {
        // Designed so that total ACV = 1000 and cumulative Pareto shares
        // fall cleanly into A / B / B / C. Combined with hand-picked demand
        // series to force X, X, Y, X on XYZ.
        Product alpha = product("ALPHA", "10", 0, List.of(24, 24, 24));         // ACV 720, CV 0 -> X
        Product beta  = product("BETA",  "5",  0, List.of(6, 6, 6, 6, 6));      // ACV 150, CV 0 -> X
        Product gamma = product("GAMMA", "2",  0, List.of(5, 35));              // ACV  80, CV 0.75 -> Y
        Product delta = product("DELTA", "5",  0, List.of(10));                 // ACV  50, single obs, CV 0 -> X

        // Feed the classifier in an intentionally scrambled order to prove
        // the ABC pass sorts by ACV internally.
        List<ProductClassification> results = service.classify(List.of(delta, gamma, alpha, beta));

        Map<String, ProductClassification> bySku = results.stream()
                .collect(Collectors.toMap(ProductClassification::sku, Function.identity()));

        assertThat(bySku.get("ALPHA").abcClass()).isEqualTo("A");
        assertThat(bySku.get("ALPHA").xyzClass()).isEqualTo("X");
        assertThat(bySku.get("ALPHA").combinedClass()).isEqualTo("AX");
        assertThat(bySku.get("ALPHA").annualConsumptionValue())
                .isEqualByComparingTo(new BigDecimal("720"));

        assertThat(bySku.get("BETA").abcClass()).isEqualTo("B");
        assertThat(bySku.get("BETA").xyzClass()).isEqualTo("X");
        assertThat(bySku.get("BETA").combinedClass()).isEqualTo("BX");

        assertThat(bySku.get("GAMMA").abcClass()).isEqualTo("B");
        assertThat(bySku.get("GAMMA").xyzClass()).isEqualTo("Y");
        assertThat(bySku.get("GAMMA").combinedClass()).isEqualTo("BY");

        assertThat(bySku.get("DELTA").abcClass()).isEqualTo("C");
        assertThat(bySku.get("DELTA").xyzClass()).isEqualTo("X");
        assertThat(bySku.get("DELTA").combinedClass()).isEqualTo("CX");
    }

    @Test
    void productWithoutDemandLandsInClassCAndNotAvailableXyz() {
        // A product with no demand history has ACV = 0. It sorts to the bottom
        // and — since it never contributes to cumulative share — always ends
        // up in class C. Its XYZ is undefined and reported as "N/A".
        Product active = product("ACTIVE", "10", 0, List.of(10, 10));
        Product empty  = product("EMPTY",  "10", 0, List.of());

        List<ProductClassification> results = service.classify(List.of(active, empty));
        Map<String, ProductClassification> bySku = results.stream()
                .collect(Collectors.toMap(ProductClassification::sku, Function.identity()));

        assertThat(bySku.get("EMPTY").abcClass()).isEqualTo("C");
        assertThat(bySku.get("EMPTY").xyzClass()).isEqualTo("N/A");
        assertThat(bySku.get("EMPTY").coefficientOfVariation()).isNull();
        assertThat(bySku.get("EMPTY").combinedClass()).isEqualTo("CN/A");
    }

    @Test
    void catalogWithZeroTotalAcvFallsEntirelyIntoClassC() {
        Product a = product("A", "10", 0, List.of());
        Product b = product("B", "10", 0, List.of(0, 0));

        List<ProductClassification> results = service.classify(List.of(a, b));

        assertThat(results).allMatch(r -> "C".equals(r.abcClass()));
    }

    private static Product product(String sku, String unitPrice, int stock, List<Integer> demand) {
        return Product.builder()
                .sku(sku)
                .name(sku)
                .unitPrice(new BigDecimal(unitPrice))
                .stockQuantity(stock)
                .demandObservations(new java.util.ArrayList<>(demand))
                .build();
    }
}
