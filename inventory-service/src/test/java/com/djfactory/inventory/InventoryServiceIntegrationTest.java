package com.djfactory.inventory;

import com.djfactory.inventory.web.dto.CreateProductRequest;
import com.djfactory.inventory.web.dto.ProductClassificationResponse;
import com.djfactory.inventory.web.dto.ProductResponse;
import com.djfactory.inventory.web.dto.StockAdjustmentRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class InventoryServiceIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8")
            .withDatabaseName("inventory_db")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    TestRestTemplate rest;

    @Test
    void createsProductFetchesItAdjustsStockAndRejectsNegativeStock() {
        CreateProductRequest create = new CreateProductRequest(
                "SKU-CRUD-1", "Widget", new BigDecimal("9.99"), 5, "gadgets", List.of(3, 4, 5));

        ResponseEntity<ProductResponse> created =
                rest.postForEntity("/products", create, ProductResponse.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        Long id = created.getBody().id();
        assertThat(id).isNotNull();
        assertThat(created.getBody().sku()).isEqualTo("SKU-CRUD-1");
        assertThat(created.getBody().stockQuantity()).isEqualTo(5);

        ResponseEntity<ProductResponse> fetched =
                rest.getForEntity("/products/{id}", ProductResponse.class, id);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody()).isNotNull();
        assertThat(fetched.getBody().demandObservations()).containsExactly(3, 4, 5);

        ResponseEntity<ProductResponse> increased = patchStock(id, 7);
        assertThat(increased.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(increased.getBody()).isNotNull();
        assertThat(increased.getBody().stockQuantity()).isEqualTo(12);

        ResponseEntity<ProductResponse> decreased = patchStock(id, -4);
        assertThat(decreased.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(decreased.getBody()).isNotNull();
        assertThat(decreased.getBody().stockQuantity()).isEqualTo(8);

        ResponseEntity<String> negative = patchStockRaw(id, -100);
        assertThat(negative.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<ProductResponse> afterFailure =
                rest.getForEntity("/products/{id}", ProductResponse.class, id);
        assertThat(afterFailure.getBody()).isNotNull();
        assertThat(afterFailure.getBody().stockQuantity()).isEqualTo(8);
    }

    @Test
    void duplicateSkuIsRejectedWith409() {
        rest.postForEntity(
                "/products",
                new CreateProductRequest(
                        "SKU-DUP", "First", new BigDecimal("1.00"), 1, null, List.of()),
                ProductResponse.class);

        ResponseEntity<String> second = rest.postForEntity(
                "/products",
                new CreateProductRequest(
                        "SKU-DUP", "Second", new BigDecimal("2.00"), 2, null, List.of()),
                String.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void missingProductReturns404() {
        ResponseEntity<String> response =
                rest.getForEntity("/products/{id}", String.class, 999_999L);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void classificationEndpointReturnsExpectedAbcXyzForHandDesignedCatalog() {
        // Hand-designed catalog:
        //   ALPHA: unit 10, demand [24,24,24] -> ACV 720, CV 0        -> AX
        //   BETA : unit  5, demand [6,6,6,6,6] -> ACV 150, CV 0        -> BX
        //   GAMMA: unit  2, demand [5,35]      -> ACV  80, CV 0.75     -> BY
        //   DELTA: unit  5, demand [10]        -> ACV  50, single obs  -> CX
        // Total ACV = 1000 -> cumulative shares 0.72, 0.87, 0.95, 1.00.
        rest.postForEntity("/products",
                new CreateProductRequest("ALPHA", "Alpha", new BigDecimal("10"), 0, null,
                        List.of(24, 24, 24)),
                ProductResponse.class);
        rest.postForEntity("/products",
                new CreateProductRequest("BETA", "Beta", new BigDecimal("5"), 0, null,
                        List.of(6, 6, 6, 6, 6)),
                ProductResponse.class);
        rest.postForEntity("/products",
                new CreateProductRequest("GAMMA", "Gamma", new BigDecimal("2"), 0, null,
                        List.of(5, 35)),
                ProductResponse.class);
        rest.postForEntity("/products",
                new CreateProductRequest("DELTA", "Delta", new BigDecimal("5"), 0, null,
                        List.of(10)),
                ProductResponse.class);

        ResponseEntity<List<ProductClassificationResponse>> response = rest.exchange(
                "/products/classification",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<ProductClassificationResponse>>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        Map<String, ProductClassificationResponse> bySku = response.getBody().stream()
                .collect(Collectors.toMap(ProductClassificationResponse::sku, Function.identity()));

        assertThat(bySku).containsOnlyKeys("ALPHA", "BETA", "GAMMA", "DELTA");
        assertThat(bySku.get("ALPHA").combinedClass()).isEqualTo("AX");
        assertThat(bySku.get("BETA").combinedClass()).isEqualTo("BX");
        assertThat(bySku.get("GAMMA").combinedClass()).isEqualTo("BY");
        assertThat(bySku.get("DELTA").combinedClass()).isEqualTo("CX");

        assertThat(bySku.get("ALPHA").annualConsumptionValue())
                .isEqualByComparingTo(new BigDecimal("720"));
        assertThat(bySku.get("GAMMA").coefficientOfVariation()).isEqualTo(0.75);
    }

    private ResponseEntity<ProductResponse> patchStock(Long id, int delta) {
        return rest.exchange(
                "/products/{id}/stock",
                HttpMethod.PATCH,
                new HttpEntity<>(new StockAdjustmentRequest(delta)),
                ProductResponse.class,
                id);
    }

    private ResponseEntity<String> patchStockRaw(Long id, int delta) {
        return rest.exchange(
                "/products/{id}/stock",
                HttpMethod.PATCH,
                new HttpEntity<>(new StockAdjustmentRequest(delta)),
                String.class,
                id);
    }
}
