package de.umr.lambda.benchmarks;

import de.umr.lambda.UCRMSMInterpolationExperiment;
import de.umr.lambda.linearfunction.LineFitter;
import de.umr.lambda.lowerbound.LowerBound;
import de.umr.lambda.lowerbound.LowerBoundContext;
import de.umr.lambda.lowerbound.MsmBglbLowerBound;
import de.umr.lambda.lowerbound.PiecewiseLinearEnvelopeLowerBound;
import de.umr.lambda.msm.MSM;
import de.umr.lambda.preprocessing.TimeSeriesCleaner;
import de.umr.lambda.preprocessing.TimeSeriesCleaner.CleaningType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import static de.umr.lambda.UCRMSMInterpolationExperiment.findDatasetDirs;
import static de.umr.lambda.UCRMSMInterpolationExperiment.readDataset;

public class Benchmark {
    private static final Path UCR_ROOT = Paths.get(
            "C:\\Users\\gries\\Desktop\\Masterarbeit\\data\\UCRArchive_2018\\UCRArchive_2018");

    private static final String DATASET_FILTER = "StarLightCurves";
    private static final int MAX_SERIES_PER_DATASET = 2;
    private static final int PAIRS_PER_DATASET = 2;
    private static final long SEED = 100L;

    private static final double MSM_C = 0.5;
    private static final double LINE_WINDOW_FRACTION = 0.15;
    private static final int MIN_LINE_WINDOW = 2;
    private static final double CLEAN_THRESHOLD = 2.5;
    private static final double IMPROVEMENT_EPS = 1e-9;

    public static void main(String[] args) throws IOException {
        String datasetFilter = args.length > 0 ? blankToNull(args[0]) : DATASET_FILTER;
        int pairsPerDataset = args.length > 1 ? Integer.parseInt(args[1]) : PAIRS_PER_DATASET;

        List<MethodSpec> methods = methods();
        List<MethodStats> stats = methods.stream()
                .map(method -> new MethodStats(method.name()))
                .toList();
        Map<String, CleanerStats> cleanerStats = cleanerStats();

        Random random = new Random(SEED);
        List<Path> datasetDirs = findDatasetDirs(UCR_ROOT);
        int totalPairs = 0;
        int improvedPairs = 0;

        for (Path dsDir : datasetDirs) {
            String name = dsDir.getFileName().toString();
//            if (datasetFilter != null && !datasetFilter.equals(name)) {
//                continue;
//            }

            Path train = dsDir.resolve(name + "_TRAIN.tsv");
            Path test = dsDir.resolve(name + "_TEST.tsv");
            if (!Files.exists(train) || !Files.exists(test)) {
                continue;
            }

            UCRMSMInterpolationExperiment.Dataset raw = readDataset(train, test);
            List<double[]> series = usableSeries(raw.trainSeries, MAX_SERIES_PER_DATASET);
            if (series.size() < 2) {
                continue;
            }

            boolean printedDatasetHeader = false;

            for (int pair = 0; pair < pairsPerDataset; pair++) {
                double[] x = series.get(random.nextInt(series.size()));
                double[] y = series.get(random.nextInt(series.size()));
                if (x == y) {
                    pair--;
                    continue;
                }

                int window = benchmarkWindow(x.length);
                double exact = MSM.distance(x, y, MSM_C, window);
                if (!Double.isFinite(exact)) {
                    pair--;
                    continue;
                }

                totalPairs++;
                ArrayList<MethodResult> results = new ArrayList<>();
                for (MethodSpec method : methods) {
                    double lb = safeEstimate(method, x, y, MSM_C, window, CLEAN_THRESHOLD);
                    results.add(new MethodResult(method.name(), lb, exact));
                }

                PairCleaningSummary pairSummary =
                        summarizeCleaning(results, name, pair + 1, window, exact, cleanerStats);
                if (!pairSummary.anyImproved()) {
                    continue;
                }

                improvedPairs++;
                pairSummary.apply();
                if (!printedDatasetHeader) {
                    System.out.println();
                    System.out.println("Dataset: " + name + "  length=" + series.getFirst().length);
                    printedDatasetHeader = true;
                }
                System.out.printf(Locale.US, "%nPair %02d  exact=%.6f  window=%d%n", pair + 1, exact, window);

                for (int i = 0; i < methods.size(); i++) {
                    MethodSpec method = methods.get(i);
                    MethodResult result = results.get(i);
                    MethodStats methodStats = stats.get(i);
                    methodStats.add(result.lb(), result.exact());

                    printMethodResult(method.name(), result.lb(), result.exact());
                }
            }
        }

        System.out.println();
        System.out.println("Summary");
        printSummary(stats);
        printImprovementRate(totalPairs, improvedPairs);
        printCleanerSummary(cleanerStats, totalPairs, improvedPairs);
    }

    private static void printSummary(List<MethodStats> stats) {
        Map<String, MethodStats> byName = new HashMap<>();
        for (MethodStats stat : stats) {
            byName.put(stat.name, stat);
        }

        stats.stream()
                .filter(MethodStats::hasData)
                .sorted(Comparator.comparingDouble(MethodStats::meanRatio).reversed())
                .forEach(stat -> stat.print(byName));
    }

    private static PairCleaningSummary summarizeCleaning(
            List<MethodResult> results,
            String dataset,
            int pair,
            int window,
            double exact,
            Map<String, CleanerStats> cleanerStats) {

        Map<String, MethodResult> byName = new HashMap<>();
        for (MethodResult result : results) {
            byName.put(result.name(), result);
        }

        PairCleaningSummary summary = new PairCleaningSummary();
        for (MethodResult result : results) {
            String baseName = cleanedBaseName(result.name());
            if (baseName == null || !Double.isFinite(result.lb())) {
                continue;
            }

            MethodResult plain = byName.get(baseName);
            if (plain != null
                    && Double.isFinite(plain.lb())
                    && result.lb() > plain.lb() + IMPROVEMENT_EPS) {
                String cleanerName = cleanerName(result.name());
                CleanerStats stats = cleanerStats.get(cleanerName);
                if (stats == null) {
                    continue;
                }

                double absoluteGain = result.lb() - plain.lb();
                double percentageGain = percentageGain(plain.lb(), result.lb());
                Improvement improvement = new Improvement(
                        cleanerName,
                        dataset,
                        pair,
                        window,
                        baseName,
                        result.name(),
                        plain.lb(),
                        result.lb(),
                        exact,
                        absoluteGain,
                        percentageGain);
                summary.add(stats, improvement);
            }
        }

        return summary;
    }

    private static String cleanedBaseName(String name) {
        int marker = name.indexOf(" + max(");
        return marker < 0 ? null : name.substring(0, marker);
    }

    private static String cleanerName(String name) {
        int start = name.indexOf(" + max(");
        if (start < 0) {
            return null;
        }

        start += " + max(".length();
        int end = name.indexOf(')', start);
        return end < 0 ? null : name.substring(start, end);
    }

    private static double percentageGain(double plainLb, double cleanedLb) {
        double gain = cleanedLb - plainLb;
        if (plainLb == 0.0) {
            return gain > 0.0 ? Double.POSITIVE_INFINITY : 0.0;
        }
        return 100.0 * gain / Math.abs(plainLb);
    }

    private static void printImprovementRate(int totalPairs, int improvedPairs) {
        double percentage = totalPairs == 0 ? 0.0 : 100.0 * improvedPairs / totalPairs;
        System.out.printf(
                Locale.US,
                "%nCleaning helped on %d/%d pairs (%.2f%%).%n",
                improvedPairs,
                totalPairs,
                percentage);
    }

    private static Map<String, CleanerStats> cleanerStats() {
        Map<String, CleanerStats> stats = new HashMap<>();
        for (CleaningType type : CleaningType.values()) {
            String label = label(type);
            stats.put(label, new CleanerStats(label));
        }
        return stats;
    }

    private static void printCleanerSummary(
            Map<String, CleanerStats> cleanerStats,
            int totalPairs,
            int improvedPairs) {

        System.out.println();
        System.out.println("Cleaning Method Summary");
        cleanerStats.values().stream()
                .sorted(Comparator.comparingInt(CleanerStats::helpedPairs).reversed())
                .forEach(stats -> stats.print(totalPairs, improvedPairs));
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank() || "all".equalsIgnoreCase(value)) {
            return null;
        }
        return value;
    }

    private static List<MethodSpec> methods() {
        ArrayList<MethodSpec> methods = new ArrayList<>();

        MsmBglbLowerBound bglb = new MsmBglbLowerBound();
        addMethodWithCleaners(methods, bglb.name(), (x, y, c, window, threshold) ->
                bglb.estimate(x, y, c, window));

        MsmBglbLowerBound rectangleBglb = new MsmBglbLowerBound(MsmBglbLowerBound.Type.SEGMENT_RECTANGLE);
        addMethodWithCleaners(methods, rectangleBglb.name(), (x, y, c, window, threshold) ->
                rectangleBglb.estimate(x, y, c, window));

        addLineMethods(methods, LineFitter.FitType.L1, 0.0, 0);
        addLineMethods(methods, LineFitter.FitType.L2, 0.0, 0);
//        addLineMethods(methods, LineFitter.FitType.MSM_ITERATIVE, MSM_C, 20);
//        addLineMethods(methods, LineFitter.FitType.MSM_SHAPE_AWARE, MSM_C, 20);
//        addLineMethods(methods, LineFitter.FitType.MSM_BEST_TO_POINTS, MSM_C, 0);
        addHybridPointwiseMaxMethod(methods);

        return methods;
    }

    private static double safeEstimate(
            MethodSpec method,
            double[] x,
            double[] y,
            double c,
            int window,
            double threshold) {

        try {
            double value = method.estimate(x, y, c, window, threshold);
            return Double.isFinite(value) ? value : Double.NaN;
        } catch (RuntimeException ex) {
            return Double.NaN;
        }
    }

    private static void printMethodResult(String name, double lb, double exact) {
        if (!Double.isFinite(lb)) {
            System.out.printf(Locale.US, "  %-58s skipped%n", name);
            return;
        }

        System.out.printf(
                Locale.US,
                "  %-58s lb=%10.6f  ratio=%8.4f%n",
                name,
                lb,
                ratio(lb, exact));
    }

    private static void addLineMethods(
            List<MethodSpec> methods,
            LineFitter.FitType fitType,
            double fitC,
            int iterations) {

        addLineMethod(methods, PiecewiseLinearEnvelopeLowerBound.oneSided(), fitType, fitC, iterations);
        addLineMethod(methods, PiecewiseLinearEnvelopeLowerBound.twoSidedRepair(), fitType, fitC, iterations);
    }

    private static void addLineMethod(
            List<MethodSpec> methods,
            LowerBound lowerBound,
            LineFitter.FitType fitType,
            double fitC,
            int iterations) {

        String name = lowerBound.name() + " " + fitType;
        addMethodWithCleaners(methods, name, (x, y, c, window, threshold) -> {
            LowerBoundContext context = new LowerBoundContext(c, window, fitType, fitC, iterations);
            return lowerBound.estimate(x, y, context);
        });
    }

    private static void addMethodWithCleaners(
            List<MethodSpec> methods,
            String name,
            MethodEstimator estimator) {

        methods.add(new MethodSpec(name, estimator));
        for (CleaningType type : CleaningType.values()) {
            methods.add(new MethodSpec(
                    name + " + max(" + label(type) + ")",
                    (x, y, c, window, threshold) ->
                            combinedCleanedEstimate(estimator, x, y, c, window, threshold, type)));
        }
    }

    private static double combinedCleanedEstimate(
            MethodEstimator estimator,
            double[] x,
            double[] y,
            double c,
            int window,
            double threshold,
            CleaningType type) {

        double plain = estimator.estimate(x, y, c, window, threshold);

        double[] cleanX = TimeSeriesCleaner.clean(x, window, threshold, type);
        double[] cleanY = TimeSeriesCleaner.clean(y, window, threshold, type);
        double errX = MSM.distance(x, cleanX, c, window);
        double errY = MSM.distance(y, cleanY, c, window);
        double cleanLowerBound = estimator.estimate(cleanX, cleanY, c, window, threshold);
        double triangle = Math.max(0.0, cleanLowerBound - errX - errY);

        return maxFinite(plain, triangle);
    }

    private static double maxFinite(double a, double b) {
        boolean finiteA = Double.isFinite(a);
        boolean finiteB = Double.isFinite(b);
        if (finiteA && finiteB) {
            return Math.max(a, b);
        }
        if (finiteA) {
            return a;
        }
        if (finiteB) {
            return b;
        }
        return Double.NaN;
    }

    private static String label(CleaningType type) {
        return switch (type) {
            case HAMPEL -> "Hampel";
            case WINSORIZED -> "Winsor";
            case LOCAL_LINE_RESIDUAL -> "LocalLine";
            case ROBUST_PEAK -> "RobustPeak";
        };
    }

    private static void addHybridPointwiseMaxMethod(List<MethodSpec> methods) {
        ArrayList<ContributionSpec> contributionMethods = new ArrayList<>();

        MsmBglbLowerBound bglb = new MsmBglbLowerBound();
        contributionMethods.add(new ContributionSpec("BGLB_MSM", (x, y, c, window, threshold) ->
                bglb.contributions(x, y, c, window)));

//        addLineContributionMethod(contributionMethods, PiecewiseLinearEnvelopeLowerBound.oneSided(), LineFitter.FitType.L1, 0.0, 0);
//        addLineContributionMethod(contributionMethods, PiecewiseLinearEnvelopeLowerBound.twoSidedRepair(), LineFitter.FitType.L1, 0.0, 0);
//        addLineContributionMethod(contributionMethods, PiecewiseLinearEnvelopeLowerBound.oneSided(), LineFitter.FitType.L2, 0.0, 0);
//        addLineContributionMethod(contributionMethods, PiecewiseLinearEnvelopeLowerBound.twoSidedRepair(), LineFitter.FitType.L2, 0.0, 0);
//        addLineContributionMethod(contributionMethods, PiecewiseLinearEnvelopeLowerBound.oneSided(), LineFitter.FitType.MSM_BEST_TO_POINTS, MSM_C, 0);
//        addLineContributionMethod(contributionMethods, PiecewiseLinearEnvelopeLowerBound.twoSidedRepair(), LineFitter.FitType.MSM_BEST_TO_POINTS, MSM_C, 0);

    }

    private static void addLineContributionMethod(
            List<ContributionSpec> methods,
            PiecewiseLinearEnvelopeLowerBound lowerBound,
            LineFitter.FitType fitType,
            double fitC,
            int iterations) {

        String name = lowerBound.name() + " " + fitType;
        methods.add(new ContributionSpec(name, (x, y, c, window, threshold) -> {
            LowerBoundContext context = new LowerBoundContext(c, window, fitType, fitC, iterations);
            return lowerBound.contributions(x, y, context);
        }));
    }

    private static double hybridPointwiseMax(
            List<ContributionSpec> methods,
            double[] x,
            double[] y,
            double c,
            int window,
            double threshold) {

        double[] maxByIndex = new double[x.length];
        for (ContributionSpec method : methods) {
            double[] contributions = method.estimate(x, y, c, window, threshold);
            if (contributions.length != x.length) {
                throw new IllegalStateException(method.name() + " returned wrong contribution length");
            }

            for (int i = 0; i < contributions.length; i++) {
                if (!Double.isFinite(contributions[i])) {
                    throw new IllegalStateException(method.name() + " returned non-finite contribution");
                }
                maxByIndex[i] = Math.max(maxByIndex[i], contributions[i]);
            }
        }

        double sum = 0.0;
        for (double value : maxByIndex) {
            sum += value;
        }
        return sum;
    }

    private static List<double[]> usableSeries(double[][] rawSeries, int limit) {
        ArrayList<double[]> series = new ArrayList<>();
        int expectedLength = -1;

        for (double[] raw : rawSeries) {
            double[] copy = trimTrailingNaN(raw);
            if (copy.length == 0) {
                continue;
            }
            if (!allFinite(copy)) {
                continue;
            }
            if (expectedLength < 0) {
                expectedLength = copy.length;
            }
            if (copy.length != expectedLength) {
                continue;
            }

            if (!zNormalize(copy) || !allFinite(copy)) {
                continue;
            }
            series.add(copy);

            if (series.size() >= limit) {
                break;
            }
        }

        return series;
    }

    private static boolean allFinite(double[] values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                return false;
            }
        }
        return true;
    }

    private static int benchmarkWindow(int n) {
        return Math.max(MIN_LINE_WINDOW, (int) Math.round(n * LINE_WINDOW_FRACTION));
    }

    private static double[] trimTrailingNaN(double[] ts) {
        int end = ts.length;
        while (end > 0 && Double.isNaN(ts[end - 1])) {
            end--;
        }

        return Arrays.copyOf(ts, end);
    }

    static boolean zNormalize(double[] x) {
        int n = x.length;
        if (n == 0) {
            return false;
        }

        double sum = 0.0;
        for (double v : x) sum += v;
        double mean = sum / n;

        double var = 0.0;
        for (double v : x) {
            double d = v - mean;
            var += d * d;
        }
        double std = Math.sqrt(var / n);

        if (!Double.isFinite(std)) {
            return false;
        }
        if (std < 1e-12) {
            Arrays.fill(x, 0.0);
            return true;
        }

        for (int i = 0; i < n; i++) {
            x[i] = (x[i] - mean) / std;
        }
        return true;
    }

    private static double ratio(double lb, double exact) {
        if (!Double.isFinite(lb) || !Double.isFinite(exact)) {
            return Double.NaN;
        }
        if (exact == 0.0) {
            return lb == 0.0 ? 1.0 : Double.POSITIVE_INFINITY;
        }
        return lb / exact;
    }

    private record MethodSpec(String name, MethodEstimator estimator) {
        double estimate(double[] x, double[] y, double c, int window, double threshold) {
            return estimator.estimate(x, y, c, window, threshold);
        }
    }

    private record MethodResult(String name, double lb, double exact) {}

    private record Improvement(
            String cleaner,
            String dataset,
            int pair,
            int window,
            String baseMethod,
            String cleanedMethod,
            double plainLb,
            double cleanedLb,
            double exact,
            double absoluteGain,
            double percentageGain) {}

    private record ContributionSpec(String name, ContributionEstimator estimator) {
        double[] estimate(double[] x, double[] y, double c, int window, double threshold) {
            return estimator.estimate(x, y, c, window, threshold);
        }
    }

    @FunctionalInterface
    private interface MethodEstimator {
        double estimate(double[] x, double[] y, double c, int window, double threshold);
    }

    @FunctionalInterface
    private interface ContributionEstimator {
        double[] estimate(double[] x, double[] y, double c, int window, double threshold);
    }

    private static final class PairCleaningSummary {
        private final Map<CleanerStats, CleanerPairImprovements> byCleaner = new HashMap<>();

        private void add(CleanerStats stats, Improvement improvement) {
            byCleaner.computeIfAbsent(stats, ignored -> new CleanerPairImprovements())
                    .add(improvement);
        }

        private boolean anyImproved() {
            return !byCleaner.isEmpty();
        }

        private void apply() {
            byCleaner.forEach(CleanerStats::add);
        }
    }

    private static final class CleanerPairImprovements {
        private Improvement bestAbsolute;
        private Improvement bestPercentage;

        private void add(Improvement improvement) {
            if (bestAbsolute == null
                    || improvement.absoluteGain() > bestAbsolute.absoluteGain()) {
                bestAbsolute = improvement;
            }
            if (bestPercentage == null
                    || improvement.percentageGain() > bestPercentage.percentageGain()) {
                bestPercentage = improvement;
            }
        }
    }

    private static final class CleanerStats {
        private final String name;
        private int helpedPairs;
        private Improvement bestAbsolute;
        private Improvement bestPercentage;

        private CleanerStats(String name) {
            this.name = name;
        }

        private int helpedPairs() {
            return helpedPairs;
        }

        private void add(CleanerPairImprovements improvements) {
            helpedPairs++;
            Improvement absolute = improvements.bestAbsolute;
            Improvement percentage = improvements.bestPercentage;
            if (bestAbsolute == null
                    || absolute.absoluteGain() > bestAbsolute.absoluteGain()) {
                bestAbsolute = absolute;
            }
            if (bestPercentage == null
                    || percentage.percentageGain() > bestPercentage.percentageGain()) {
                bestPercentage = percentage;
            }
        }

        private void print(int totalPairs, int improvedPairs) {
            double totalPct = totalPairs == 0 ? 0.0 : 100.0 * helpedPairs / totalPairs;
            double helpedPct = improvedPairs == 0 ? 0.0 : 100.0 * helpedPairs / improvedPairs;
            System.out.printf(
                    Locale.US,
                    "%-12s helped=%4d/%-4d (%6.2f%% of all, %6.2f%% of helped)%n",
                    name,
                    helpedPairs,
                    totalPairs,
                    totalPct,
                    helpedPct);

            printBest("  best absolute", bestAbsolute, false);
            printBest("  best percent ", bestPercentage, true);
        }

        private static void printBest(String prefix, Improvement improvement, boolean percentage) {
            if (improvement == null) {
                System.out.printf(Locale.US, "%s: none%n", prefix);
                return;
            }

            String gain = percentage
                    ? formatPercentage(improvement.percentageGain())
                    : String.format(Locale.US, "%.6f", improvement.absoluteGain());
            System.out.printf(
                    Locale.US,
                    "%s: gain=%s dataset=%s pair=%d window=%d method=%s plain=%.6f cleaned=%.6f exact=%.6f%n",
                    prefix,
                    gain,
                    improvement.dataset(),
                    improvement.pair(),
                    improvement.window(),
                    improvement.baseMethod(),
                    improvement.plainLb(),
                    improvement.cleanedLb(),
                    improvement.exact());
        }

        private static String formatPercentage(double value) {
            return Double.isInfinite(value)
                    ? "Infinity%"
                    : String.format(Locale.US, "%.4f%%", value);
        }
    }

    private static final class MethodStats {
        private final String name;
        private int count;
        private int violations;
        private double ratioSum;
        private double bestRatio;
        private double worstViolation;

        private MethodStats(String name) {
            this.name = name;
        }

        private void add(double lb, double exact) {
            double ratio = ratio(lb, exact);
            if (!Double.isFinite(ratio)) {
                return;
            }
            count++;
            ratioSum += ratio;
            bestRatio = Math.max(bestRatio, ratio);

            double violation = lb - exact;
            if (violation > 1e-9) {
                violations++;
                worstViolation = Math.max(worstViolation, violation);
            }
        }

        private double meanRatio() {
            return count == 0 ? 0.0 : ratioSum / count;
        }

        private boolean hasData() {
            return count > 0;
        }

        private void print(Map<String, MethodStats> byName) {
            double gain = meanRatio() - plainMeanRatio(byName);
            System.out.printf(
                    Locale.US,
                    "%-58s meanRatio=%8.4f  gainVsPlain=%8.4f  bestRatio=%8.4f  violations=%d  worst=%10.6f  n=%d%n",
                    name,
                    meanRatio(),
                    gain,
                    bestRatio,
                    violations,
                    worstViolation,
                    count);
        }

        private double plainMeanRatio(Map<String, MethodStats> byName) {
            String baseName = cleanedBaseName(name);
            if (baseName == null) {
                return meanRatio();
            }

            MethodStats plain = byName.get(baseName);
            return plain == null ? meanRatio() : plain.meanRatio();
        }
    }
}
