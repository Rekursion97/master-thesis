import de.umr.lambda.linearfunction.LineFitter;
import de.umr.lambda.lowerbound.LowerBoundContext;
import de.umr.lambda.lowerbound.MsmBglbLowerBound;
import de.umr.lambda.lowerbound.PiecewiseLinearEnvelopeLowerBound;
import de.umr.lambda.msm.MSM;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MsmBglbLowerBoundTest {
    private static final double EPS = 1e-9;

    private final MsmBglbLowerBound lowerBound = new MsmBglbLowerBound();

    @Test
    public void paperExampleStyleSeriesDoesNotExceedExactMsm() {
        double[] x = {5, 2, 3, 7, 4};
        double[] y = {2, 3, 4, 5, 10};

        assertLowerBound(x, y, 1.0, 1);
    }

    @Test
    public void identicalSeriesBoundIsZero() {
        double[] x = {1, 3, 2, 5, 4, 4};

        double bound = lowerBound.estimate(x, x, 0.5, 1);

        assertTrue(bound <= EPS);
    }

    @Test
    public void singlePointSeriesMatchesExactMsm() {
        double[] x = {3.0};
        double[] y = {-2.0};

        assertLowerBound(x, y, 0.5, 0);
    }

    @Test
    public void bglbContributionsSumToEstimate() {
        double[] x = {1.0, 3.0, 2.0, 8.0, 4.0, 5.0};
        double[] y = {1.2, 2.8, 2.5, 4.0, 4.3, 5.1};
        double c = 0.5;
        int window = 2;

        assertEquals(
                lowerBound.estimate(x, y, c, window),
                sum(lowerBound.contributions(x, y, c, window)),
                1e-12);
    }

    @Test
    public void lineContributionsSumToEstimate() {
        double[] x = {1.0, 3.0, 2.0, 8.0, 4.0, 5.0, 6.0, 6.5};
        double[] y = {1.2, 2.8, 2.5, 4.0, 4.3, 5.1, 5.7, 6.2};
        LowerBoundContext context = new LowerBoundContext(
                0.5,
                3,
                LineFitter.FitType.L2,
                0.0,
                0);
        PiecewiseLinearEnvelopeLowerBound lineBound =
                PiecewiseLinearEnvelopeLowerBound.twoSidedRepair();

        assertEquals(
                lineBound.estimate(x, y, context),
                sum(lineBound.contributions(x, y, context)),
                1e-9);
    }

    @Test
    public void randomizedBoundNeverExceedsExactMsm() {
        Random random = new Random(7);

        for (int test = 0; test < 10_000; test++) {
            int n = 2 + random.nextInt(30);
            double[] x = randomSeries(random, n);
            double[] y = randomSeries(random, n);
            double c = 0.01 + random.nextDouble() * 3.0;
            int window = random.nextInt(n);

            assertLowerBound(x, y, c, window);
        }
    }

    @Test
    public void rectangularVariantNeverExceedsExactMsmOrPaperBound() {
        Random random = new Random(11);
        MsmBglbLowerBound rectangle =
                new MsmBglbLowerBound(MsmBglbLowerBound.Type.SEGMENT_RECTANGLE);

        for (int test = 0; test < 10_000; test++) {
            int n = 2 + random.nextInt(30);
            double[] x = randomSeries(random, n);
            double[] y = randomSeries(random, n);
            double c = 0.01 + random.nextDouble() * 3.0;
            int window = random.nextInt(n);

            double paper = lowerBound.estimate(x, y, c, window);
            double rectangular = rectangle.estimate(x, y, c, window);
            double exact = MSM.distance(x, y, c, window);

            assertTrue(
                    "rectangular=" + rectangular + " paper=" + paper,
                    rectangular <= paper + EPS);
            assertTrue(
                    "rectangular=" + rectangular + " exact=" + exact,
                    rectangular <= exact + EPS);
        }
    }

    private void assertLowerBound(double[] x, double[] y, double c, int window) {
        double bound = lowerBound.estimate(x, y, c, window);
        double exact = MSM.distance(x, y, c, window);

        assertTrue(
                "bound=" + bound + " exact=" + exact,
                bound <= exact + EPS);
    }

    private static double[] randomSeries(Random random, int n) {
        double[] series = new double[n];
        double value = random.nextDouble() * 4.0 - 2.0;

        for (int i = 0; i < n; i++) {
            value += random.nextGaussian() * 0.7;
            series[i] = value;
        }

        return series;
    }

    private static double sum(double[] values) {
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum;
    }
}
