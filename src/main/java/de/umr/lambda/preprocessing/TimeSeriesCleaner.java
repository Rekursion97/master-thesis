package de.umr.lambda.preprocessing;

import de.umr.lambda.lowerbound.MsmBglbLowerBound;
import de.umr.lambda.msm.MSM;

import java.util.Arrays;

public final class TimeSeriesCleaner {
    private static final double NORMAL_MAD_SCALE = 0.6745;
    private static final double WINSOR_LOWER_QUANTILE = 0.05;
    private static final double WINSOR_UPPER_QUANTILE = 0.95;
    private static final MsmBglbLowerBound BGLB = new MsmBglbLowerBound();

    private TimeSeriesCleaner() {}

    public enum CleaningType {
        HAMPEL,
        WINSORIZED,
        LOCAL_LINE_RESIDUAL,
        ROBUST_PEAK
    }

    public static double[] cleanSpikes(double[] t, int window, double threshold) {
        return clean(t, window, threshold, CleaningType.HAMPEL);
    }

    public static double[] clean(double[] t, int window, double threshold, CleaningType type) {
        validateCleanerInput(t, window, threshold);
        if (type == null) {
            throw new IllegalArgumentException("cleaning type must not be null");
        }

        double[] cleaned = Arrays.copyOf(t, t.length);
        if (t.length < 3) {
            return cleaned;
        }

        return switch (type) {
            case HAMPEL -> cleanHampel(t, cleaned, window, threshold);
            case WINSORIZED -> cleanWinsorized(t, cleaned, window, threshold);
            case LOCAL_LINE_RESIDUAL -> cleanLocalLineResidual(t, cleaned, window, threshold);
            case ROBUST_PEAK -> cleanRobustPeak(t, cleaned, window, threshold);
        };
    }

    public static double cleaningError(double[] t, double[] cleaned, double c) {
        return MSM.distance(t, cleaned, c);
    }

    public static double cleaningError(double[] t, double[] cleaned, double c, int msmWindow) {
        return MSM.distance(t, cleaned, c, msmWindow);
    }

    public static double cleanedTriangleLowerBound(
            double[] t1,
            double[] t2,
            double c,
            int window,
            double threshold) {
        return cleanedTriangleLowerBound(t1, t2, c, window, threshold, CleaningType.HAMPEL);
    }

    public static double cleanedTriangleLowerBound(
            double[] t1,
            double[] t2,
            double c,
            int window,
            double threshold,
            CleaningType type) {

        double[] cleaned1 = clean(t1, window, threshold, type);
        double[] cleaned2 = clean(t2, window, threshold, type);

        double err1 = cleaningError(t1, cleaned1, c, window);
        double err2 = cleaningError(t2, cleaned2, c, window);
        double lbCleaned = bglbLowerBound(cleaned1, cleaned2, c, window);

        return Math.max(0.0, lbCleaned - err1 - err2);
    }

    public static double combinedCleanedTriangleLowerBound(
            double[] t1,
            double[] t2,
            double c,
            int window,
            double threshold,
            CleaningType type) {

        double plain = bglbLowerBound(t1, t2, c, window);
        double cleaned = cleanedTriangleLowerBound(t1, t2, c, window, threshold, type);
        return Math.max(plain, cleaned);
    }

    public static void demo() {
        double c = 0.5;
        int window = 5;
        double threshold = 3.5;

        double[] t1 = {1, 1.2, 1.1, 9.0, 1.3, 1.4, 1.5, -6.0, 1.7, 1.8};
        double[] t2 = {0.9, 1.1, 1.2, 1.3, 1.35, 1.45, 8.0, 1.6, 1.75, 1.9};
        double[] cleaned1 = clean(t1, window, threshold, CleaningType.HAMPEL);
        double[] cleaned2 = clean(t2, window, threshold, CleaningType.HAMPEL);

        System.out.println("original 1 = " + Arrays.toString(t1));
        System.out.println("cleaned 1  = " + Arrays.toString(cleaned1));
        System.out.println("original 2 = " + Arrays.toString(t2));
        System.out.println("cleaned 2  = " + Arrays.toString(cleaned2));
        System.out.println("err 1      = " + cleaningError(t1, cleaned1, c));
        System.out.println("err 2      = " + cleaningError(t2, cleaned2, c));
        System.out.println("LB         = " + cleanedTriangleLowerBound(t1, t2, c, window, threshold));
    }

    public static void main(String[] args) {
        demo();
    }

    private static double[] cleanHampel(double[] t, double[] cleaned, int window, double threshold) {
        boolean[] outlier = detectHampelOutliers(t, window, threshold);
        replaceOutlierRegions(cleaned, outlier, window);
        return cleaned;
    }

    private static double[] cleanWinsorized(double[] t, double[] cleaned, int window, double threshold) {
        for (int i = 0; i < t.length; i++) {
            WindowRange range = centeredWindow(i, t.length, window);
            double[] values = localValues(t, range, i);
            if (values.length < 2) {
                values = Arrays.copyOfRange(t, range.start(), range.end());
            }
            if (values.length == 0) {
                continue;
            }

            double median = median(values);
            double scale = robustScale(values, median);
            if (scale == 0.0) {
                continue;
            }

            double z = NORMAL_MAD_SCALE * (t[i] - median) / scale;
            if (Math.abs(z) <= threshold) {
                continue;
            }

            double[] sorted = Arrays.copyOf(values, values.length);
            Arrays.sort(sorted);
            double lower = quantile(sorted, WINSOR_LOWER_QUANTILE);
            double upper = quantile(sorted, WINSOR_UPPER_QUANTILE);
            cleaned[i] = clamp(t[i], lower, upper);
        }

        return cleaned;
    }

    private static double[] cleanLocalLineResidual(double[] t, double[] cleaned, int window, double threshold) {
        boolean[] outlier = detectLocalLineResidualOutliers(t, window, threshold);
        replaceOutlierRegions(cleaned, outlier, window);
        return cleaned;
    }

    private static double[] cleanRobustPeak(double[] t, double[] cleaned, int window, double threshold) {
        boolean[] candidate = detectRobustPeakCandidates(t, window, threshold);
        boolean[] peak = keepOnlyRiseAndFallPeaks(t, candidate, window);
        replaceOutlierRegions(cleaned, peak, window);
        return cleaned;
    }

    private static boolean[] detectHampelOutliers(double[] t, int window, double threshold) {
        boolean[] outlier = new boolean[t.length];

        for (int i = 0; i < t.length; i++) {
            WindowRange range = centeredWindow(i, t.length, window);
            double[] values = Arrays.copyOfRange(t, range.start(), range.end());

            if (values.length == 0) {
                continue;
            }

            double median = median(values);
            double scale = robustScale(values, median);
            if (scale == 0.0) {
                continue;
            }

            // Robust spike criterion: MAD/IQR-based z-score flags only points
            // that are unusual relative to their local window, not the whole series.
            double z = NORMAL_MAD_SCALE * (t[i] - median) / scale;
            outlier[i] = Math.abs(z) > threshold;
        }

        return outlier;
    }

    private static boolean[] detectLocalLineResidualOutliers(double[] t, int window, double threshold) {
        boolean[] outlier = new boolean[t.length];

        for (int i = 0; i < t.length; i++) {
            WindowRange range = centeredWindow(i, t.length, window);
            if (range.end() - range.start() < 3) {
                continue;
            }

            Line line = fitLine(t, range);
            double[] residuals = new double[range.end() - range.start()];
            for (int j = range.start(); j < range.end(); j++) {
                residuals[j - range.start()] = t[j] - line.valueAt(j);
            }

            double median = median(residuals);
            double scale = robustScale(residuals, median);
            if (scale == 0.0) {
                continue;
            }

            // Residual Hampel criterion: compare the point to a local trend
            // before applying MAD/IQR scale, so trend violations are flagged.
            double residual = t[i] - line.valueAt(i);
            double z = NORMAL_MAD_SCALE * (residual - median) / scale;
            outlier[i] = Math.abs(z) > threshold;
        }

        return outlier;
    }

    private static boolean[] detectRobustPeakCandidates(double[] t, int window, double threshold) {
        boolean[] candidate = new boolean[t.length];

        for (int i = 0; i < t.length; i++) {
            WindowRange range = centeredWindow(i, t.length, window);
            double[] values = localValues(t, range, i);
            if (values.length < 2) {
                continue;
            }

            double median = median(values);
            double scale = robustScale(values, median);
            if (scale == 0.0) {
                candidate[i] = t[i] > median;
                continue;
            }

            // Robust peak criterion: the point must be unusually high compared
            // with its local neighborhood. Shape validation happens per region.
            double z = NORMAL_MAD_SCALE * (t[i] - median) / scale;
            candidate[i] = z > threshold;
        }

        return candidate;
    }

    private static boolean[] keepOnlyRiseAndFallPeaks(double[] t, boolean[] candidate, int window) {
        boolean[] peak = new boolean[t.length];
        if (allTrue(candidate)) {
            return peak;
        }

        int i = 0;
        while (i < candidate.length) {
            if (!candidate[i]) {
                i++;
                continue;
            }

            int start = i;
            while (i < candidate.length && candidate[i]) {
                i++;
            }
            int end = i - 1;

            if (isRiseAndFallPeak(t, candidate, start, end, window)) {
                Arrays.fill(peak, start, end + 1, true);
            }
        }

        return peak;
    }

    private static boolean isRiseAndFallPeak(
            double[] t,
            boolean[] candidate,
            int start,
            int end,
            int window) {

        int length = end - start + 1;
        if (length >= window || length > Math.max(1, (int) Math.ceil(window * 0.5))) {
            return false;
        }

        int left = start - 1;
        int right = end + 1;
        if (left < 0 || right >= t.length || candidate[left] || candidate[right]) {
            return false;
        }

        double max = t[start];
        for (int i = start + 1; i <= end; i++) {
            max = Math.max(max, t[i]);
        }

        // Require a real local peak: it rises above the left side and comes
        // back down on the right side. Monotone rising regions fail here.
        return max > t[left]
                && max > t[right]
                && t[start] >= t[left]
                && t[end] >= t[right];
    }

    private static double robustScale(double[] values, double median) {
        double mad = mad(values, median);
        if (mad > 0.0) {
            return mad;
        }

        double iqr = iqr(values);
        if (iqr > 0.0) {
            return iqr / 1.349;
        }

        return meanAbsoluteDeviation(values, median);
    }

    private static void replaceOutlierRegions(double[] cleaned, boolean[] outlier, int window) {
        if (allTrue(outlier)) {
            return;
        }

        int i = 0;
        while (i < outlier.length) {
            if (!outlier[i]) {
                i++;
                continue;
            }

            int start = i;
            while (i < outlier.length && outlier[i]) {
                i++;
            }
            int end = i - 1;

            if (end - start + 1 >= window) {
                continue;
            }

            replaceRegion(cleaned, outlier, start, end, window);
        }
    }

    private static void replaceRegion(
            double[] cleaned,
            boolean[] outlier,
            int start,
            int end,
            int window) {

        int left = start - 1;
        int right = end + 1;
        boolean hasLeft = left >= 0 && !outlier[left];
        boolean hasRight = right < cleaned.length && !outlier[right];

        if (hasLeft && hasRight) {
            double leftValue = cleaned[left];
            double rightValue = cleaned[right];
            int gap = right - left;

            for (int i = start; i <= end; i++) {
                double alpha = (double) (i - left) / gap;
                cleaned[i] = leftValue + alpha * (rightValue - leftValue);
            }
        } else if (hasLeft) {
            Arrays.fill(cleaned, start, end + 1, cleaned[left]);
        } else if (hasRight) {
            Arrays.fill(cleaned, start, end + 1, cleaned[right]);
        } else {
            double localMedian = localMedian(cleaned, start, end, window);
            Arrays.fill(cleaned, start, end + 1, localMedian);
        }
    }

    public static double median(double[] values) {
        if (values.length == 0) {
            throw new IllegalArgumentException("median requires at least one value");
        }

        double[] copy = Arrays.copyOf(values, values.length);
        Arrays.sort(copy);
        return sortedMedian(copy);
    }

    public static double mad(double[] values, double median) {
        double[] deviations = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            deviations[i] = Math.abs(values[i] - median);
        }
        return median(deviations);
    }

    public static double iqr(double[] values) {
        if (values.length < 2) {
            return 0.0;
        }

        double[] copy = Arrays.copyOf(values, values.length);
        Arrays.sort(copy);
        double q1 = quantile(copy, 0.25);
        double q3 = quantile(copy, 0.75);
        return q3 - q1;
    }

    private static double meanAbsoluteDeviation(double[] values, double median) {
        double sum = 0.0;
        for (double value : values) {
            sum += Math.abs(value - median);
        }
        return sum / values.length;
    }

    private static double localMedian(double[] values, int start, int end, int window) {
        int center = (start + end) / 2;
        WindowRange range = centeredWindow(center, values.length, window);
        return median(Arrays.copyOfRange(values, range.start(), range.end()));
    }

    private static double[] localValues(double[] values, WindowRange range, int excludedIndex) {
        int length = range.end() - range.start();
        if (excludedIndex < range.start() || excludedIndex >= range.end()) {
            return Arrays.copyOfRange(values, range.start(), range.end());
        }

        double[] copy = new double[length - 1];
        int out = 0;
        for (int i = range.start(); i < range.end(); i++) {
            if (i != excludedIndex) {
                copy[out++] = values[i];
            }
        }
        return copy;
    }

    private static Line fitLine(double[] values, WindowRange range) {
        int n = range.end() - range.start();
        if (n <= 1) {
            return new Line(0.0, values[range.start()]);
        }

        double sumX = 0.0;
        double sumY = 0.0;
        double sumXX = 0.0;
        double sumXY = 0.0;

        for (int i = range.start(); i < range.end(); i++) {
            double x = i;
            double y = values[i];
            sumX += x;
            sumY += y;
            sumXX += x * x;
            sumXY += x * y;
        }

        double denominator = n * sumXX - sumX * sumX;
        if (Math.abs(denominator) < 1e-12) {
            return new Line(0.0, sumY / n);
        }

        double slope = (n * sumXY - sumX * sumY) / denominator;
        double intercept = (sumY - slope * sumX) / n;
        return new Line(slope, intercept);
    }

    private static double sortedMedian(double[] sorted) {
        int n = sorted.length;
        if ((n & 1) == 1) {
            return sorted[n / 2];
        }
        return 0.5 * (sorted[n / 2 - 1] + sorted[n / 2]);
    }

    private static double quantile(double[] sorted, double q) {
        double position = q * (sorted.length - 1);
        int lo = (int) Math.floor(position);
        int hi = (int) Math.ceil(position);

        if (lo == hi) {
            return sorted[lo];
        }

        double alpha = position - lo;
        return sorted[lo] + alpha * (sorted[hi] - sorted[lo]);
    }

    private static double clamp(double value, double lower, double upper) {
        if (lower > upper) {
            double tmp = lower;
            lower = upper;
            upper = tmp;
        }
        return Math.max(lower, Math.min(value, upper));
    }

    private static WindowRange centeredWindow(int center, int n, int window) {
        int width = Math.min(window, n);
        int start = center - width / 2;
        int end = start + width;

        if (start < 0) {
            end -= start;
            start = 0;
        }
        if (end > n) {
            start -= end - n;
            end = n;
        }

        return new WindowRange(Math.max(0, start), Math.min(n, end));
    }

    private static boolean allTrue(boolean[] values) {
        for (boolean value : values) {
            if (!value) {
                return false;
            }
        }
        return values.length > 0;
    }

    private static double bglbLowerBound(double[] cleaned1, double[] cleaned2, double c, int window) {
        return BGLB.estimate(cleaned1, cleaned2, c, window);
    }

    private static void validateCleanerInput(double[] t, int window, double threshold) {
        if (t == null) {
            throw new IllegalArgumentException("time series must not be null");
        }
        if (window <= 0) {
            throw new IllegalArgumentException("window must be positive");
        }
        if (threshold <= 0.0) {
            throw new IllegalArgumentException("threshold must be positive");
        }
    }

    private record WindowRange(int start, int end) {}

    private record Line(double slope, double intercept) {
        private double valueAt(int x) {
            return slope * x + intercept;
        }
    }
}
