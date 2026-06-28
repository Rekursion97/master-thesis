import de.umr.lambda.lowerbound.MsmBglbLowerBound;
import de.umr.lambda.msm.MSM;
import de.umr.lambda.preprocessing.TimeSeriesCleaner;
import de.umr.lambda.preprocessing.TimeSeriesCleaner.CleaningType;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TimeSeriesCleanerTest {
    @Test
    public void cleanSpikesDoesNotModifyInputOrLength() {
        double[] original = {1.0, 1.1, 1.0, 8.0, 1.2, 1.3};
        double[] copy = original.clone();

        double[] cleaned = TimeSeriesCleaner.cleanSpikes(original, 5, 3.5);

        assertArrayEquals(copy, original, 0.0);
        assertEquals(original.length, cleaned.length);
    }

    @Test
    public void isolatedSpikeIsInterpolatedBetweenValidNeighbors() {
        double[] original = {1.0, 1.1, 1.0, 8.0, 1.2, 1.3, 1.4};

        double[] cleaned = TimeSeriesCleaner.cleanSpikes(original, 5, 3.5);

        assertEquals(1.1, cleaned[3], 1e-12);
    }

    @Test
    public void consecutiveSpikeRegionIsInterpolatedTogether() {
        double[] original = {1.0, 1.1, 1.0, 8.0, 8.2, 1.2, 1.3, 1.4};

        double[] cleaned = TimeSeriesCleaner.cleanSpikes(original, 7, 3.5);

        assertEquals(1.0666666666666667, cleaned[3], 1e-12);
        assertEquals(1.1333333333333333, cleaned[4], 1e-12);
    }

    @Test
    public void cleaningErrorUsesMsmDistance() {
        double[] original = {1.0, 1.1, 1.0, 8.0, 1.2, 1.3, 1.4};
        double[] cleaned = TimeSeriesCleaner.cleanSpikes(original, 5, 3.5);
        double c = 0.5;

        assertEquals(
                MSM.distance(original, cleaned, c),
                TimeSeriesCleaner.cleaningError(original, cleaned, c),
                1e-12);
    }

    @Test
    public void cleanedTriangleLowerBoundDoesNotExceedExactMsm() {
        double[] t1 = {1.0, 1.1, 1.0, 8.0, 1.2, 1.3, 1.4, 1.5};
        double[] t2 = {1.0, 1.05, 1.1, 1.15, 1.2, -7.0, 1.35, 1.5};
        double c = 0.5;

        double lb = TimeSeriesCleaner.cleanedTriangleLowerBound(t1, t2, c, 5, 3.5);
        double exact = MSM.distance(t1, t2, c);

        assertTrue("lb=" + lb + " exact=" + exact, lb <= exact + 1e-9);
    }

    @Test
    public void cleanedTriangleEqualsBglbWhenNothingIsCleaned() {
        double[] t1 = {1.0, 1.1, 1.2, 1.3, 1.4, 1.5};
        double[] t2 = {0.9, 1.0, 1.15, 1.25, 1.35, 1.45};
        double c = 0.5;
        int window = 3;
        double threshold = 3.5;

        assertArrayEquals(t1, TimeSeriesCleaner.cleanSpikes(t1, window, threshold), 0.0);
        assertArrayEquals(t2, TimeSeriesCleaner.cleanSpikes(t2, window, threshold), 0.0);

        double plain = new MsmBglbLowerBound().estimate(t1, t2, c, window);
        double cleaned = TimeSeriesCleaner.cleanedTriangleLowerBound(t1, t2, c, window, threshold);

        assertEquals(plain, cleaned, 1e-12);
    }

    @Test
    public void winsorizedCleanerClampsExtremeValue() {
        double[] original = {1.0, 1.0, 1.1, 10.0, 1.2, 1.3, 1.3};

        double[] cleaned = TimeSeriesCleaner.clean(original, 7, 3.5, CleaningType.WINSORIZED);

        assertEquals(original.length, cleaned.length);
        assertTrue(cleaned[3] < original[3]);
        assertTrue(cleaned[3] >= 1.0);
        assertTrue(cleaned[3] <= 1.3);
    }

    @Test
    public void localLineResidualCleanerInterpolatesTrendViolation() {
        double[] original = {0.0, 1.0, 2.0, 30.0, 4.0, 5.0, 6.0};

        double[] cleaned = TimeSeriesCleaner.clean(original, 7, 3.5, CleaningType.LOCAL_LINE_RESIDUAL);

        assertEquals(3.0, cleaned[3], 1e-12);
    }

    @Test
    public void robustPeakCleanerInterpolatesLocalRiseAndFallPeak() {
        double[] original = {1.0, 1.0, 1.0, 10.0, 1.0, 1.0, 1.0};

        double[] cleaned = TimeSeriesCleaner.clean(original, 7, 3.5, CleaningType.ROBUST_PEAK);

        assertEquals(1.0, cleaned[3], 1e-12);
    }

    @Test
    public void robustPeakCleanerDoesNotReplaceRegionThatKeepsRising() {
        double[] original = {1.0, 1.0, 10.0, 10.0, 12.0, 1.0};

        double[] cleaned = TimeSeriesCleaner.clean(original, 6, 3.0, CleaningType.ROBUST_PEAK);

        assertArrayEquals(original, cleaned, 0.0);
    }

    @Test
    public void combinedLowerBoundEqualsPlainWhenNoCleanerChangesSeries() {
        double[] t1 = {2.0, 2.0, 2.0, 2.0, 2.0};
        double[] t2 = {1.0, 1.0, 1.0, 1.0, 1.0};
        double c = 0.5;
        int window = 3;
        double threshold = 3.5;

        double plain = new MsmBglbLowerBound().estimate(t1, t2, c, window);
        for (CleaningType type : CleaningType.values()) {
            assertArrayEquals(t1, TimeSeriesCleaner.clean(t1, window, threshold, type), 0.0);
            assertArrayEquals(t2, TimeSeriesCleaner.clean(t2, window, threshold, type), 0.0);
            assertEquals(
                    plain,
                    TimeSeriesCleaner.combinedCleanedTriangleLowerBound(t1, t2, c, window, threshold, type),
                    1e-12);
        }
    }
}
