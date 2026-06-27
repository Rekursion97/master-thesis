import de.umr.lambda.linearfunction.LinearFunctionUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;


public class LowerBoundTest {

    // replace with your function
    static double fast(double a, double b, int n,
                       double minY, double maxY, double c) {
        return LinearFunctionUtil.lowerBound(a, b, n, minY, maxY, c);
    }

    static double brute(double a, double b, int n,
                        double minY, double maxY, double c) {

        double sum = 0;

        for (int i = 0; i < n; i++) {
            double y = a * i + b;
            double val = 0;

            if (y > maxY) {
                val = Math.min(y - maxY, c);
            } else if (y < minY) {
                val = Math.min(minY - y, c);
            }

            sum += val;
        }

        return sum;
    }

    void assertClose(double a, double b, int n,
                     double minY, double maxY, double c) {

        double f1 = fast(a, b, n, minY, maxY, c);
        double f2 = brute(a, b, n, minY, maxY, c);


        assertEquals(f2, f1,0.00001);
    }

    // ---------------- TESTS ----------------

    @Test
    public void testConstantInsideBounds() {
        assertClose(0, 5, 100, 0, 10, 2);
    }

    @Test
    public void testConstantAboveMax() {
        assertClose(0, 20, 50, 0, 10, 3);
    }

    @Test
    public void testConstantBelowMin() {
        assertClose(0, -5, 50, 0, 10, 4);
    }

    @Test
    public void testIncreasingNoCrossing() {
        assertClose(1, 0, 10, -100, 100, 5);
    }

    @Test
    public void testIncreasingCrossBothBounds() {
        assertClose(2, -10, 100, 0, 20, 3);
    }

    @org.testng.annotations.Test
    public void testDecreasingCrossBothBounds() {
        assertClose(-1, 50, 100, 0, 20, 2);
    }

    @Test
    public void testSmallN() {
        assertClose(3, 2, 3, 0, 10, 5);
    }

    @Test
    public void testEdgeTouchMaxY() {
        assertClose(1, 10, 20, 15, 25, 4);
    }

    @Test
    public void testEdgeTouchMinY() {
        assertClose(1, 0, 20, 5, 15, 4);
    }

    @Test
    public void testLargeRange() {
        assertClose(0.7, -100, 10000, -20, 30, 7);
    }

    @Test
    public void testNegativeSlopeWideBounds() {
        assertClose(-0.8, 200, 500, -50, 50, 6);
    }

    @Test
    public void testMixedSmallC() {
        assertClose(1.3, -5, 200, -10, 10, 0.5);
    }

    @Test
    public void testAlmostZeroSlope() {
        assertClose(1e-12, 3, 1000, 2, 4, 1);
    }

    @Test
    public void testConstantExactlyAtMax() {
        assertClose(0, 10, 100, 0, 10, 3);
    }

    @Test
    public void testConstantExactlyAtMin() {
        assertClose(0, 0, 100, 0, 10, 3);
    }

    @Test
    public void testConstantExactlyAtMaxPlusC() {
        assertClose(0, 13, 100, 0, 10, 3);
    }

    @Test
    public void testConstantExactlyAtMinMinusC() {
        assertClose(0, -3, 100, 0, 10, 3);
    }

    @Test
    public void testConstantBetweenMaxAndMaxPlusC() {
        assertClose(0, 11.5, 100, 0, 10, 3);
    }

    @Test
    public void testConstantBetweenMinMinusCAndMin() {
        assertClose(0, -1.5, 100, 0, 10, 3);
    }

    @Test
    public void testAllPointsAboveMaxPlusCIncreasing() {
        assertClose(0.5, 100, 200, 0, 10, 2);
    }

    @Test
    public void testAllPointsBelowMinMinusCIncreasing() {
        assertClose(0.5, -200, 200, 0, 10, 2);
    }

    @Test
    public void testAllPointsAboveMaxPlusCDecreasing() {
        assertClose(-0.5, 100, 200, 0, 10, 2);
    }

    @Test
    public void testAllPointsBelowMinMinusCDecreasing() {
        assertClose(-0.5, -200, 200, 0, 10, 2);
    }

    @Test
    public void testIncreasingCrossMaxOnly() {
        assertClose(1, 0, 30, -100, 10, 3);
    }

    @Test
    public void testIncreasingCrossMinOnly() {
        assertClose(1, -20, 30, 0, 100, 3);
    }

    @Test
    public void testDecreasingCrossMaxOnly() {
        assertClose(-1, 30, 30, -100, 10, 3);
    }

    @Test
    public void testDecreasingCrossMinOnly() {
        assertClose(-1, 10, 30, 0, 100, 3);
    }

    @Test
    public void testCrossExactlyAtMaxBoundary() {
        // y_5 = 10 exactly
        assertClose(2, 0, 20, -100, 10, 3);
    }

    @Test
    public void testCrossExactlyAtMaxPlusCBoundary() {
        // y_5 = 13 exactly
        assertClose(2, 3, 20, -100, 10, 3);
    }

    @Test
    public void testCrossExactlyAtMinBoundary() {
        // y_5 = 0 exactly
        assertClose(2, -10, 20, 0, 100, 3);
    }

    @Test
    public void testCrossExactlyAtMinMinusCBoundary() {
        // y_5 = -3 exactly
        assertClose(2, -13, 20, 0, 100, 3);
    }

    @Test
    public void testSinglePointInside() {
        assertClose(5, 7, 1, 0, 10, 3);
    }

    @Test
    public void testSinglePointAbove() {
        assertClose(5, 20, 1, 0, 10, 3);
    }

    @Test
    public void testSinglePointBelow() {
        assertClose(5, -5, 1, 0, 10, 3);
    }

    @Test
    public void testTwoPointsIncreasing() {
        assertClose(10, -5, 2, 0, 10, 3);
    }

    @Test
    public void testTwoPointsDecreasing() {
        assertClose(-10, 15, 2, 0, 10, 3);
    }

    @Test
    public void testVerySmallC() {
        assertClose(0.75, -20, 500, -5, 5, 1e-6);
    }

    @Test
    public void testVeryLargeC() {
        assertClose(0.75, -20, 500, -5, 5, 1e6);
    }

    @Test
    public void testHugePositiveOffset() {
        assertClose(0.1, 1e6, 1000, -10, 10, 2);
    }

    @Test
    public void testHugeNegativeOffset() {
        assertClose(0.1, -1e6, 1000, -10, 10, 2);
    }

    @Test
    public void testHugeSlopePositive() {
        assertClose(1e4, -5e5, 1000, -10, 10, 7);
    }

    @Test
    public void testHugeSlopeNegative() {
        assertClose(-1e4, 5e5, 1000, -10, 10, 7);
    }

    @Test
    public void testNZero() {
        assertClose(1, 5, 0, 0, 10, 3);
    }

    @Test
    public void testMaxOnlyRampNoCap() {
        // all contributions above maxY but below maxY+c
        assertClose(0.1, 10.1, 20, -100, 100, 1000);
    }

    @Test
    public void testMinOnlyRampNoCap() {
        // all contributions below minY but above minY-c
        assertClose(-0.1, -0.1, 20, 0, 100, 1000);
    }

    @Test
    public void testAlternatingAroundBoundsBySlope() {
        assertClose(0.49, -5, 100, -2, 2, 1.5);
    }

    @Test
    public void testLongSeriesBoundaryStress() {
        assertClose(0.03125, -100, 100000, -3, 7, 2.25);
    }

    @Test
    public void randomizedRegressionTest() {
        java.util.Random rnd = new java.util.Random(1);

        for (int t = 0; t < 10000; t++) {
            double a = -10 + 20 * rnd.nextDouble();
            double b = -100 + 200 * rnd.nextDouble();
            int n = 1 + rnd.nextInt(500);
            double minY = -50 + 100 * rnd.nextDouble();
            double maxY = minY + rnd.nextDouble() * 100;
            double c = 1e-6 + rnd.nextDouble() * 20;

            assertClose(a, b, n, minY, maxY, c);
        }
    }

    static double[] fastWithMax(double a, double b, int n,
                                double minY, double maxY, double c) {
        return LinearFunctionUtil.lowerBoundWithMaxValue(a, b, n, minY, maxY, c,0);
    }

    static double[] bruteWithMax(double a, double b, int n,
                                 double minY, double maxY, double c) {
        double sum = 0.0;
        double max = 0.0;

        for (int i = 0; i < n; i++) {
            double y = a * i + b;
            double val = 0.0;

            if (y > maxY) {
                val = Math.min(y - maxY, c);
            } else if (y < minY) {
                val = Math.min(minY - y, c);
            }

            sum += val;
            if (val > max) max = val;
        }

        return new double[]{sum, max};
    }

    void assertCloseWithMax(double a, double b, int n,
                            double minY, double maxY, double c) {
        double[] f = fastWithMax(a, b, n, minY, maxY, c);
        double[] g = bruteWithMax(a, b, n, minY, maxY, c);

        assertEquals(g[0], f[0], 0.00001);
        assertEquals(g[1], f[1], 0.00001);
    }

    @Test
    public void testWithMaxConstantInside() {
        assertCloseWithMax(0, 5, 100, 0, 10, 3);
    }

    @Test
    public void testWithMaxConstantAboveRamp() {
        assertCloseWithMax(0, 11.5, 100, 0, 10, 3);
    }

    @Test
    public void testWithMaxConstantAboveCapped() {
        assertCloseWithMax(0, 20, 100, 0, 10, 3);
    }

    @Test
    public void testWithMaxConstantBelowRamp() {
        assertCloseWithMax(0, -1.5, 100, 0, 10, 3);
    }

    @Test
    public void testWithMaxConstantBelowCapped() {
        assertCloseWithMax(0, -20, 100, 0, 10, 3);
    }

    @Test
    public void testWithMaxIncreasingUpperRampOnly() {
        assertCloseWithMax(0.1, 10.1, 20, -100, 10, 100);
    }

    @Test
    public void testWithMaxIncreasingUpperHitsCap() {
        assertCloseWithMax(2, 0, 20, -100, 10, 3);
    }

    @Test
    public void testWithMaxDecreasingUpperHitsCap() {
        assertCloseWithMax(-2, 40, 20, -100, 10, 3);
    }

    @Test
    public void testWithMaxIncreasingLowerHitsCap() {
        assertCloseWithMax(2, -40, 20, 0, 100, 3);
    }

    @Test
    public void testWithMaxDecreasingLowerHitsCap() {
        assertCloseWithMax(-2, 10, 20, 0, 100, 3);
    }

    @Test
    public void testWithMaxCrossBothSidesIncreasing() {
        assertCloseWithMax(2, -50, 100, 0, 20, 4);
    }

    @Test
    public void testWithMaxCrossBothSidesDecreasing() {
        assertCloseWithMax(-2, 150, 100, 0, 20, 4);
    }

    @Test
    public void testWithMaxExactlyMaxY() {
        assertCloseWithMax(1, 10, 20, 0, 10, 3);
    }

    @Test
    public void testWithMaxExactlyMaxYPlusC() {
        assertCloseWithMax(1, 13, 20, 0, 10, 3);
    }

    @Test
    public void testWithMaxExactlyMinY() {
        assertCloseWithMax(1, 0, 20, 0, 10, 3);
    }

    @Test
    public void testWithMaxExactlyMinYMinusC() {
        assertCloseWithMax(1, -3, 20, 0, 10, 3);
    }

    @Test
    public void testWithMaxSinglePointInside() {
        assertCloseWithMax(5, 5, 1, 0, 10, 3);
    }

    @Test
    public void testWithMaxSinglePointAbove() {
        assertCloseWithMax(5, 20, 1, 0, 10, 3);
    }

    @Test
    public void testWithMaxSinglePointBelow() {
        assertCloseWithMax(5, -10, 1, 0, 10, 3);
    }

    @Test
    public void testWithMaxNZero() {
        assertCloseWithMax(1, 5, 0, 0, 10, 3);
    }

    @Test
    public void testWithMaxTinyC() {
        assertCloseWithMax(0.7, -50, 1000, -5, 5, 1e-6);
    }

    @Test
    public void testWithMaxHugeCNoCap() {
        assertCloseWithMax(0.7, -50, 1000, -5, 5, 1e9);
    }

    @Test
    public void randomizedWithMaxRegressionTest() {
        java.util.Random rnd = new java.util.Random(2);

        for (int t = 0; t < 10000; t++) {
            double a = -20 + 40 * rnd.nextDouble();
            double b = -200 + 400 * rnd.nextDouble();
            int n = rnd.nextInt(1000);
            double minY = -100 + 200 * rnd.nextDouble();
            double maxY = minY + rnd.nextDouble() * 200;
            double c = 1e-9 + rnd.nextDouble() * 50;

            assertCloseWithMax(a, b, n, minY, maxY, c);
        }
    }

    static double[] fastSubtract(double a, double b, int n,
                                 double minY, double maxY,
                                 double c, double subtract) {
        return LinearFunctionUtil.lowerBoundWithMaxValue(
                a, b, n, minY, maxY, c, subtract);
    }

    static double[] bruteSubtract(double a, double b, int n,
                                  double minY, double maxY,
                                  double c, double subtract) {
        double sum = 0.0;
        double max = 0.0;

        for (int i = 0; i < n; i++) {
            double y = a * i + b;

            double upper = 0.0;
            if (y > maxY) {
                upper = Math.min(y - maxY, c);
            }

            double lower = 0.0;
            if (y < minY) {
                lower = Math.min(minY - y, c);
            }

            double val = Math.max(0.0, Math.max(upper, lower) - subtract);

            sum += val;
            max = Math.max(max, val);
        }

        return new double[]{sum, max};
    }

    void assertCloseSubtract(double a, double b, int n,
                             double minY, double maxY,
                             double c, double subtract) {
        double[] f = fastSubtract(a, b, n, minY, maxY, c, subtract);
        double[] g = bruteSubtract(a, b, n, minY, maxY, c, subtract);

        assertEquals(g[0], f[0], 1e-5);
        assertEquals(g[1], f[1], 1e-5);
    }

    @Test
    public void testSubtractConstantInside() {
        assertCloseSubtract(0, 5, 100, 0, 10, 3, 1);
    }

    @Test
    public void testSubtractConstantAboveRampStillPositive() {
        assertCloseSubtract(0, 12, 100, 0, 10, 5, 1);
    }

    @Test
    public void testSubtractConstantAboveRampBecomesZero() {
        assertCloseSubtract(0, 12, 100, 0, 10, 5, 3);
    }

    @Test
    public void testSubtractConstantAboveCap() {
        assertCloseSubtract(0, 100, 100, 0, 10, 5, 2);
    }

    @Test
    public void testSubtractConstantBelowRampStillPositive() {
        assertCloseSubtract(0, -2, 100, 0, 10, 5, 1);
    }

    @Test
    public void testSubtractConstantBelowRampBecomesZero() {
        assertCloseSubtract(0, -2, 100, 0, 10, 5, 3);
    }

    @Test
    public void testSubtractConstantBelowCap() {
        assertCloseSubtract(0, -100, 100, 0, 10, 5, 2);
    }

    @Test
    public void testSubtractIncreasingUpperRamp() {
        assertCloseSubtract(1, 0, 30, -100, 10, 100, 2);
    }

    @Test
    public void testSubtractIncreasingUpperCap() {
        assertCloseSubtract(2, 0, 30, -100, 10, 3, 1);
    }

    @Test
    public void testSubtractDecreasingUpperCap() {
        assertCloseSubtract(-2, 50, 30, -100, 10, 3, 1);
    }

    @Test
    public void testSubtractIncreasingLowerCap() {
        assertCloseSubtract(2, -50, 30, 0, 100, 3, 1);
    }

    @Test
    public void testSubtractDecreasingLowerCap() {
        assertCloseSubtract(-2, 10, 30, 0, 100, 3, 1);
    }

    @Test
    public void testSubtractCrossBothSidesIncreasing() {
        assertCloseSubtract(2, -50, 100, 0, 20, 4, 1.5);
    }

    @Test
    public void testSubtractCrossBothSidesDecreasing() {
        assertCloseSubtract(-2, 150, 100, 0, 20, 4, 1.5);
    }

    @Test
    public void testSubtractExactlyEqualsContribution() {
        assertCloseSubtract(0, 12, 10, 0, 10, 5, 2);
    }

    @Test
    public void testSubtractEqualsC() {
        assertCloseSubtract(1, -100, 500, -5, 5, 3, 3);
    }

    @Test
    public void testSubtractGreaterThanC() {
        assertCloseSubtract(1, -100, 500, -5, 5, 3, 4);
    }

    @Test
    public void testSubtractZeroMatchesOldBehavior() {
        assertCloseSubtract(0.7, -50, 1000, -5, 5, 7, 0);
    }

    @Test
    public void testSubtractSinglePointInside() {
        assertCloseSubtract(5, 5, 1, 0, 10, 3, 1);
    }

    @Test
    public void testSubtractSinglePointAbovePositive() {
        assertCloseSubtract(5, 20, 1, 0, 10, 3, 1);
    }

    @Test
    public void testSubtractSinglePointAboveZeroed() {
        assertCloseSubtract(5, 11, 1, 0, 10, 3, 2);
    }

    @Test
    public void testSubtractSinglePointBelowPositive() {
        assertCloseSubtract(5, -10, 1, 0, 10, 3, 1);
    }

    @Test
    public void testSubtractSinglePointBelowZeroed() {
        assertCloseSubtract(5, -1, 1, 0, 10, 3, 2);
    }

    @Test
    public void testSubtractNZero() {
        assertCloseSubtract(1, 5, 0, 0, 10, 3, 1);
    }

    @Test
    public void testSubtractTinyC() {
        assertCloseSubtract(0.7, -50, 1000, -5, 5, 1e-6, 1e-7);
    }

    @Test
    public void testSubtractHugeCNoCap() {
        assertCloseSubtract(0.7, -50, 1000, -5, 5, 1e9, 2);
    }

    @Test
    public void randomizedSubtractRegressionTest() {
        java.util.Random rnd = new java.util.Random(3);

        for (int t = 0; t < 20000; t++) {
            double a = -20 + 40 * rnd.nextDouble();
            double b = -200 + 400 * rnd.nextDouble();
            int n = rnd.nextInt(1000);

            double minY = -100 + 200 * rnd.nextDouble();
            double maxY = minY + rnd.nextDouble() * 200;

            double c = 1e-9 + rnd.nextDouble() * 50;
            double subtract = rnd.nextDouble() * 60;

            assertCloseSubtract(a, b, n, minY, maxY, c, subtract);
        }
    }

}