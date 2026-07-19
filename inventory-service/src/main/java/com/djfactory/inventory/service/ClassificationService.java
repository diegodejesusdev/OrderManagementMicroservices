package com.djfactory.inventory.service;

import com.djfactory.inventory.domain.Product;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * ABC/XYZ classification of the product catalog.
 *
 * <p>This is <em>descriptive statistics</em>, not machine learning:
 * <ul>
 *   <li>ABC ranks products by annual consumption value (Pareto 80/15/5).</li>
 *   <li>XYZ ranks products by demand variability (coefficient of variation).</li>
 * </ul>
 *
 * <p>Both calculations are pure functions of the input list, so the service is
 * trivially unit-testable and stateless.
 */
@Service
public class ClassificationService {

    // ABC cumulative-share thresholds (Pareto 80/15/5).
    static final BigDecimal ABC_A_CUMULATIVE_THRESHOLD = new BigDecimal("0.80");
    static final BigDecimal ABC_B_CUMULATIVE_THRESHOLD = new BigDecimal("0.95");

    // XYZ coefficient-of-variation thresholds.
    static final double XYZ_X_MAX_CV = 0.5;
    static final double XYZ_Y_MAX_CV = 1.0;

    static final String NOT_AVAILABLE = "N/A";

    private static final int CV_SCALE = 6;

    public List<ProductClassification> classify(List<Product> products) {
        List<Row> rows = new ArrayList<>(products.size());
        for (Product p : products) {
            BigDecimal acv = annualConsumptionValue(p);
            Double cv = coefficientOfVariation(p.getDemandObservations());
            rows.add(new Row(p, acv, cv, xyzClass(cv)));
        }

        assignAbcClasses(rows);

        List<ProductClassification> result = new ArrayList<>(rows.size());
        for (Row r : rows) {
            String combined = r.abcClass + r.xyzClass;
            result.add(new ProductClassification(
                    r.product.getId(),
                    r.product.getSku(),
                    r.product.getName(),
                    r.abcClass,
                    r.xyzClass,
                    combined,
                    r.acv,
                    r.cv
            ));
        }
        return result;
    }

    static BigDecimal annualConsumptionValue(Product p) {
        long totalDemand = 0L;
        for (Integer q : p.getDemandObservations()) {
            totalDemand += q;
        }
        return p.getUnitPrice().multiply(BigDecimal.valueOf(totalDemand));
    }

    /**
     * Population coefficient of variation of a demand series.
     * Returns {@code null} when it is not mathematically defined — i.e. when
     * the series is empty or its mean is zero. Callers should map that to the
     * XYZ class {@code "N/A"} rather than dividing by zero.
     */
    static Double coefficientOfVariation(List<Integer> observations) {
        if (observations == null || observations.isEmpty()) {
            return null;
        }
        double sum = 0.0;
        for (int q : observations) {
            sum += q;
        }
        double mean = sum / observations.size();
        if (mean == 0.0) {
            return null;
        }
        double squaredDeviation = 0.0;
        for (int q : observations) {
            double d = q - mean;
            squaredDeviation += d * d;
        }
        double variance = squaredDeviation / observations.size();
        double stdDev = Math.sqrt(variance);
        double cv = stdDev / mean;
        return BigDecimal.valueOf(cv).setScale(CV_SCALE, RoundingMode.HALF_UP).doubleValue();
    }

    static String xyzClass(Double cv) {
        if (cv == null) {
            return NOT_AVAILABLE;
        }
        if (cv <= XYZ_X_MAX_CV) {
            return "X";
        }
        if (cv <= XYZ_Y_MAX_CV) {
            return "Y";
        }
        return "Z";
    }

    /**
     * Sorts by ACV descending and assigns A/B/C based on the running share
     * of total ACV. If total ACV is zero (no demand recorded anywhere) every
     * product falls into C — nothing has yet earned a spot in the top 80%.
     */
    private static void assignAbcClasses(List<Row> rows) {
        rows.sort(Comparator.comparing((Row r) -> r.acv).reversed());

        BigDecimal totalAcv = BigDecimal.ZERO;
        for (Row r : rows) {
            totalAcv = totalAcv.add(r.acv);
        }

        if (totalAcv.signum() == 0) {
            for (Row r : rows) {
                r.abcClass = "C";
            }
            return;
        }

        BigDecimal cumulative = BigDecimal.ZERO;
        for (Row r : rows) {
            cumulative = cumulative.add(r.acv);
            BigDecimal share = cumulative.divide(totalAcv, 6, RoundingMode.HALF_UP);
            if (share.compareTo(ABC_A_CUMULATIVE_THRESHOLD) <= 0) {
                r.abcClass = "A";
            } else if (share.compareTo(ABC_B_CUMULATIVE_THRESHOLD) <= 0) {
                r.abcClass = "B";
            } else {
                r.abcClass = "C";
            }
        }
    }

    private static final class Row {
        final Product product;
        final BigDecimal acv;
        final Double cv;
        final String xyzClass;
        String abcClass;

        Row(Product product, BigDecimal acv, Double cv, String xyzClass) {
            this.product = product;
            this.acv = acv;
            this.cv = cv;
            this.xyzClass = xyzClass;
        }
    }

    public record ProductClassification(
            Long productId,
            String sku,
            String name,
            String abcClass,
            String xyzClass,
            String combinedClass,
            BigDecimal annualConsumptionValue,
            Double coefficientOfVariation
    ) {}
}
