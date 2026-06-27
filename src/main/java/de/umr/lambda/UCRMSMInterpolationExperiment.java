package de.umr.lambda;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import java.util.stream.IntStream;

public class UCRMSMInterpolationExperiment {

    // =========================================================
    // Data container
    // =========================================================
    public static class Dataset {
        int[] trainLabels;
        public double[][] trainSeries;
        int[] testLabels;
        double[][] testSeries;
    }

    // =========================================================
    // Thread-local reusable DP buffers for MSM
    // =========================================================
    static final class MSMWorkspace {
        double[] prev = new double[0];
        double[] curr = new double[0];

        void ensureCapacity(int m) {
            if (prev.length < m) {
                prev = new double[m];
                curr = new double[m];
            }
        }
    }

    static final ThreadLocal<MSMWorkspace> TLS =
            ThreadLocal.withInitial(MSMWorkspace::new);

    // =========================================================
    // Fast MSM with early abandon
    // =========================================================
    static final class MSM {

        static double cost(double x, double y, double z, double c) {
            if ((y <= x && x <= z) || (z <= x && x <= y)) {
                return c;
            }
            double a = Math.abs(x - y);
            double b = Math.abs(x - z);
            return c + (a < b ? a : b);
        }

        /**
         * Exact MSM with early abandon.
         * If partial row minimum already exceeds cutoff, returns +INF.
         */
        static double distance(double[] x, double[] y, double c, double cutoff) {
            final int n = x.length;
            final int m = y.length;

            MSMWorkspace ws = TLS.get();
            ws.ensureCapacity(m);

            double[] prev = ws.prev;
            double[] curr = ws.curr;

            prev[0] = Math.abs(x[0] - y[0]);

            for (int j = 1; j < m; j++) {
                prev[j] = prev[j - 1] + cost(y[j], x[0], y[j - 1], c);
            }

            double rowMin = prev[0];
            for (int j = 1; j < m; j++) {
                if (prev[j] < rowMin) rowMin = prev[j];
            }
            if (rowMin > cutoff) return Double.POSITIVE_INFINITY;

            for (int i = 1; i < n; i++) {
                final double xi = x[i];
                final double xim1 = x[i - 1];

                curr[0] = prev[0] + cost(xi, xim1, y[0], c);
                rowMin = curr[0];

                for (int j = 1; j < m; j++) {
                    final double yj = y[j];
                    final double yjm1 = y[j - 1];

                    final double move = prev[j - 1] + Math.abs(xi - yj);
                    final double split = prev[j] + cost(xi, xim1, yj, c);
                    final double merge = curr[j - 1] + cost(yj, xi, yjm1, c);

                    double best = move;
                    if (split < best) best = split;
                    if (merge < best) best = merge;

                    curr[j] = best;
                    if (best < rowMin) rowMin = best;
                }

                if (rowMin > cutoff) return Double.POSITIVE_INFINITY;

                double[] tmp = prev;
                prev = curr;
                curr = tmp;

                ws.prev = prev;
                ws.curr = curr;
            }

            return prev[m - 1];
        }
    }

    // =========================================================
    // Read dataset
    // =========================================================
    public static Dataset readDataset(Path trainFile, Path testFile) throws IOException {
        Dataset ds = new Dataset();

        List<String> trainLines = Files.readAllLines(trainFile);
        List<String> testLines = Files.readAllLines(testFile);

        ds.trainLabels = new int[trainLines.size()];
        ds.trainSeries = new double[trainLines.size()][];

        ds.testLabels = new int[testLines.size()];
        ds.testSeries = new double[testLines.size()][];

        for (int i = 0; i < trainLines.size(); i++) {
            parseLine(trainLines.get(i), ds.trainLabels, ds.trainSeries, i);
        }
        for (int i = 0; i < testLines.size(); i++) {
            parseLine(testLines.get(i), ds.testLabels, ds.testSeries, i);
        }

        return ds;
    }

    static void parseLine(String line, int[] labels, double[][] series, int idx) {
        String[] tok = line.trim().split("\\s+|,|\t");
        labels[idx] = (int) Double.parseDouble(tok[0]);

        double[] x = new double[tok.length - 1];
        for (int j = 1; j < tok.length; j++) {
            x[j - 1] = Double.parseDouble(tok[j]);
        }
        series[idx] = x;
    }

    // =========================================================
    // Deep copy dataset
    // =========================================================
    static Dataset deepCopyDataset(Dataset ds) {
        Dataset out = new Dataset();

        out.trainLabels = Arrays.copyOf(ds.trainLabels, ds.trainLabels.length);
        out.testLabels = Arrays.copyOf(ds.testLabels, ds.testLabels.length);

        out.trainSeries = new double[ds.trainSeries.length][];
        out.testSeries = new double[ds.testSeries.length][];

        for (int i = 0; i < ds.trainSeries.length; i++) {
            out.trainSeries[i] = Arrays.copyOf(ds.trainSeries[i], ds.trainSeries[i].length);
        }
        for (int i = 0; i < ds.testSeries.length; i++) {
            out.testSeries[i] = Arrays.copyOf(ds.testSeries[i], ds.testSeries[i].length);
        }

        return out;
    }

    // =========================================================
    // z-normalization
    // =========================================================
    static void zNormalize(double[] x) {
        int n = x.length;
        double sum = 0.0;
        for (double v : x) sum += v;
        double mean = sum / n;

        double var = 0.0;
        for (double v : x) {
            double d = v - mean;
            var += d * d;
        }
        double std = Math.sqrt(var / n);

        if (std < 1e-12) {
            Arrays.fill(x, 0.0);
            return;
        }

        for (int i = 0; i < n; i++) {
            x[i] = (x[i] - mean) / std;
        }
    }

    static void zNormalizeAll(double[][] data) {
        for (double[] s : data) zNormalize(s);
    }

    // =========================================================
    // Linear interpolation
    // inserts k points between every consecutive pair
    // =========================================================
    static double[] interpolateSeries(double[] x, int k) {
        if (k <= 0 || x.length <= 1) {
            return Arrays.copyOf(x, x.length);
        }

        int n = x.length;
        int newLen = n + (n - 1) * k;
        double[] out = new double[newLen];

        int p = 0;
        for (int i = 0; i < n - 1; i++) {
            double a = x[i];
            double b = x[i + 1];

            out[p++] = a;

            double step = (b - a) / (k + 1.0);
            for (int t = 1; t <= k; t++) {
                out[p++] = a + t * step;
            }
        }
        out[p] = x[n - 1];
        return out;
    }

    static double[][] interpolateAll(double[][] data, int k) {
        double[][] out = new double[data.length][];
        for (int i = 0; i < data.length; i++) {
            out[i] = interpolateSeries(data[i], k);
        }
        return out;
    }

    // =========================================================
    // Build transformed dataset:
    // interpolate first, then z-normalize
    // =========================================================
    static Dataset makeInterpolatedDataset(Dataset original, int k) {
        Dataset ds = new Dataset();

        ds.trainLabels = Arrays.copyOf(original.trainLabels, original.trainLabels.length);
        ds.testLabels = Arrays.copyOf(original.testLabels, original.testLabels.length);

        ds.trainSeries = interpolateAll(original.trainSeries, k);
        ds.testSeries = interpolateAll(original.testSeries, k);

        zNormalizeAll(ds.trainSeries);
        zNormalizeAll(ds.testSeries);

        return ds;
    }

    // =========================================================
    // Build baseline dataset:
    // only z-normalize original
    // =========================================================
    static Dataset makeBaselineDataset(Dataset original) {
        Dataset ds = deepCopyDataset(original);
        zNormalizeAll(ds.trainSeries);
        zNormalizeAll(ds.testSeries);
        return ds;
    }

    // =========================================================
    // Parallel 1-NN classification
    // =========================================================
    static double classify1NNParallel(Dataset ds, double msmC) {
        final int correct = IntStream.range(0, ds.testSeries.length)
                .parallel()
                .map(i -> {
                    double[] q = ds.testSeries[i];
                    int trueLabel = ds.testLabels[i];

                    double bestDist = Double.POSITIVE_INFINITY;
                    int bestLabel = Integer.MIN_VALUE;

                    for (int j = 0; j < ds.trainSeries.length; j++) {
                        double d = MSM.distance(q, ds.trainSeries[j], msmC, bestDist);
                        if (d < bestDist) {
                            bestDist = d;
                            bestLabel = ds.trainLabels[j];
                        }
                    }

                    return bestLabel == trueLabel ? 1 : 0;
                })
                .sum();

        return (double) correct / ds.testSeries.length;
    }

    // =========================================================
    // Find UCR dataset dirs
    // =========================================================
    public static List<Path> findDatasetDirs(Path root) throws IOException {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(root)) {
            s.filter(Files::isDirectory).forEach(out::add);
        }
        out.sort(Comparator.comparing(Path::getFileName));
        return out;
    }

    // =========================================================
    // Main experiment
    // args:
    //   0 = UCR root
    //   1 = msm c
    //   2 = interpolation points k
    // =========================================================
    public static void main(String[] args) throws Exception {

        Path root = Paths.get("C:\\Users\\gries\\Desktop\\Masterarbeit\\data\\UCRArchive_2018\\UCRArchive_2018");
        double msmC = 0.1;
        int k = 50;

        List<Path> datasetDirs = findDatasetDirs(root);

        int numDone = 0;
        double baselineSum = 0.0;
        double interpSum = 0.0;
        int improved = 0;
        int worse = 0;
        int equal = 0;

        long globalStart = System.nanoTime();

        for (Path dsDir : datasetDirs) {
            String name = dsDir.getFileName().toString();

            Path train = dsDir.resolve(name + "_TRAIN.tsv");
            Path test = dsDir.resolve(name + "_TEST.tsv");

            if (!Files.exists(train) || !Files.exists(test)) {
                continue;
            }

            long t0 = System.nanoTime();
            //System.out.println(name);
            Dataset raw = readDataset(train, test);
            if(raw.trainSeries[0].length>1500) {
                System.out.println(raw.trainSeries[0].length);
                continue;
            }
            if(raw.testLabels.length>300)
                continue;

            Dataset baseline = makeBaselineDataset(raw);
            Dataset interpolated = makeInterpolatedDataset(raw, k);

            double accBase = classify1NNParallel(baseline, msmC);
            double accInterp = classify1NNParallel(interpolated, msmC);

            long t1 = System.nanoTime();
            double sec = (t1 - t0) * 1e-9;

            double delta = accInterp - accBase;
            if (delta > 1e-12) improved++;
            else if (delta < -1e-12) worse++;
            else equal++;

            baselineSum += accBase;
            interpSum += accInterp;
            numDone++;

            int lenBase = baseline.trainSeries[0].length;
            int lenInterp = interpolated.trainSeries[0].length;

            System.out.printf(
                    Locale.US,
                    "%-30s  base=%.4f  interp=%.4f  delta=%+.4f   len %d -> %d   time=%.2fs%n",
                    name, accBase, accInterp, delta, lenBase, lenInterp, sec
            );
        }

        long globalEnd = System.nanoTime();
        double totalSec = (globalEnd - globalStart) * 1e-9;

        if (numDone == 0) {
            System.out.println("No valid UCR datasets found.");
            return;
        }

        System.out.println();
        System.out.println("========================================");
        System.out.printf(Locale.US, "Datasets evaluated: %d%n", numDone);
        System.out.printf(Locale.US, "MSM c = %.6f%n", msmC);
        System.out.printf(Locale.US, "Interpolation points per segment k = %d%n", k);
        System.out.printf(Locale.US, "Mean baseline accuracy     = %.4f%n", baselineSum / numDone);
        System.out.printf(Locale.US, "Mean interpolated accuracy = %.4f%n", interpSum / numDone);
        System.out.printf(Locale.US, "Mean delta                 = %+.4f%n", (interpSum - baselineSum) / numDone);
        System.out.printf(Locale.US, "Improved datasets: %d%n", improved);
        System.out.printf(Locale.US, "Worse datasets:    %d%n", worse);
        System.out.printf(Locale.US, "Equal datasets:    %d%n", equal);
        System.out.printf(Locale.US, "Total time: %.2fs%n", totalSec);
    }
}