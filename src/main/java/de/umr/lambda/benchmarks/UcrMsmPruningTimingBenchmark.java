package de.umr.lambda.benchmarks;

import de.umr.lambda.UCRMSMInterpolationExperiment;
import de.umr.lambda.msm.MSM;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static de.umr.lambda.UCRMSMInterpolationExperiment.findDatasetDirs;
import static de.umr.lambda.UCRMSMInterpolationExperiment.readDataset;

public final class UcrMsmPruningTimingBenchmark {
    private static final Path UCR_ROOT = Paths.get(
            "C:\\Users\\gries\\Desktop\\Masterarbeit\\data\\UCRArchive_2018\\UCRArchive_2018");
    private static final int DEFAULT_PAIRS_PER_DATASET = 1000;
    private static final int DEFAULT_MAX_SERIES_PER_DATASET = 100;
    private static final int DEFAULT_REPETITIONS = 3;
    private static final int DEFAULT_WARMUP_REPETITIONS = 2;
    private static final long DEFAULT_SEED = 91L;
    private static final double MSM_C = 0.5;
    private static final double EPS = 1e-9;
    private static final Method[] METHODS = Method.values();

    private static volatile double blackhole;
    private static boolean warmed;

    private UcrMsmPruningTimingBenchmark() {}

    public static void main(String[] args) throws IOException {
        Config config = Config.parse(args);
        Random random = new Random(config.seed());
        Summary overall = new Summary("OVERALL");

        System.out.printf(
                Locale.US,
                "UCR MSM timing benchmark filter=%s pairsPerDataset=%d maxSeries=%d "
                        + "c=%.3f repetitions=%d warmup=%d seed=%d%n",
                config.datasetFilter() == null ? "all" : config.datasetFilter(),
                config.pairsPerDataset(),
                config.maxSeriesPerDataset(),
                MSM_C,
                config.repetitions(),
                config.warmupRepetitions(),
                config.seed());

        int pairOrder = 0;
        for (Path directory : findDatasetDirs(UCR_ROOT)) {
            String dataset = directory.getFileName().toString();
            if (config.datasetFilter() != null
                    && !config.datasetFilter().equals(dataset)) {
                continue;
            }

            Path train = directory.resolve(dataset + "_TRAIN.tsv");
            Path test = directory.resolve(dataset + "_TEST.tsv");
            if (!Files.exists(train) || !Files.exists(test)) {
                continue;
            }

            UCRMSMInterpolationExperiment.Dataset raw = readDataset(train, test);
            List<double[]> series = usableSeries(
                    raw.trainSeries,
                    config.maxSeriesPerDataset());
            if (series.size() < 2) {
                continue;
            }

            Summary datasetSummary = new Summary(dataset);
            System.out.printf(
                    Locale.US,
                    "%nDataset: %s  series=%d  length=%d%n",
                    dataset,
                    series.size(),
                    series.get(0).length);

            for (int pair = 1; pair <= config.pairsPerDataset(); pair++) {
                Pair sample = samplePair(series, random);
                Result result = evaluate(sample, config, pairOrder++);
                datasetSummary.add(result);
                overall.add(result);
                printPair(pair, result);
            }
            datasetSummary.print();
        }

        System.out.println();
        overall.print();
        blackhole = Math.nextUp(blackhole);
    }

    private static Result evaluate(Pair sample, Config config, int pairOrder) {
        double[] x = Arrays.copyOf(sample.x(), sample.x().length);
        double[] y = Arrays.copyOf(sample.y(), sample.y().length);
        zNormalize(x);
        zNormalize(y);

        if (!warmed) {
            warmUp(x, y, config.warmupRepetitions());
            warmed = true;
        }

        long[] elapsedNanos = new long[METHODS.length];
        double exact = Double.NaN;
        for (int repetition = 0; repetition < config.repetitions(); repetition++) {
            double[] distances = new double[METHODS.length];
            for (int offset = 0; offset < METHODS.length; offset++) {
                int methodIndex = (pairOrder + repetition + offset) % METHODS.length;
                long start = System.nanoTime();
                double distance = METHODS[methodIndex].distance(x, y);
                elapsedNanos[methodIndex] += System.nanoTime() - start;
                distances[methodIndex] = distance;
                blackhole = distance;
            }
            exact = distances[Method.EXACT.ordinal()];
            for (Method method : METHODS) {
                double actual = distances[method.ordinal()];
                if (!Double.isFinite(exact) || !Double.isFinite(actual)
                        || Math.abs(exact - actual) > EPS) {
                    throw new IllegalStateException(String.format(
                            Locale.US,
                            "%s changed the MSM distance: exact=%.12f actual=%.12f",
                            method.label,
                            exact,
                            actual));
                }
            }
        }

        double[] millis = new double[METHODS.length];
        for (Method method : METHODS) {
            millis[method.ordinal()] = elapsedNanos[method.ordinal()]
                    / (1_000_000.0 * config.repetitions());
        }
        return new Result(x.length, y.length, exact, millis);
    }

    private static void warmUp(double[] x, double[] y, int repetitions) {
        for (int repetition = 0; repetition < repetitions; repetition++) {
            for (Method method : METHODS) {
                blackhole = method.distance(x, y);
            }
        }
    }

    private static void printPair(int pair, Result result) {
        System.out.printf(
                Locale.US,
                "Pair %02d  lengths=%d x %d  distance=%12.6f%n",
                pair,
                result.xLength(),
                result.yLength(),
                result.distance());
        printTimes("         ", result.millis());
    }

    private static void printTimes(String prefix, double[] millis) {
        double exactMillis = millis[Method.EXACT.ordinal()];
        for (Method method : METHODS) {
            double methodMillis = millis[method.ordinal()];
            System.out.printf(
                    Locale.US,
                    "%s%-29s %10.3f ms  speedup=%7.3f%n",
                    prefix,
                    method.label,
                    methodMillis,
                    exactMillis / methodMillis);
        }
    }

    private static List<double[]> usableSeries(double[][] rawSeries, int limit) {
        ArrayList<double[]> result = new ArrayList<>();
        for (double[] raw : rawSeries) {
            double[] values = trimTrailingNaN(raw);
            if (values.length > 1 && allFinite(values)) {
                result.add(values);
            }
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private static Pair samplePair(List<double[]> series, Random random) {
        int xIndex = random.nextInt(series.size());
        int yIndex = random.nextInt(series.size() - 1);
        if (yIndex >= xIndex) {
            yIndex++;
        }
        return new Pair(series.get(xIndex), series.get(yIndex));
    }

    private static double[] trimTrailingNaN(double[] values) {
        int end = values.length;
        while (end > 0 && Double.isNaN(values[end - 1])) {
            end--;
        }
        return Arrays.copyOf(values, end);
    }

    private static boolean allFinite(double[] values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                return false;
            }
        }
        return true;
    }

    private static void zNormalize(double[] values) {
        double mean = 0.0;
        for (double value : values) {
            mean += value;
        }
        mean /= values.length;

        double variance = 0.0;
        for (double value : values) {
            double centered = value - mean;
            variance += centered * centered;
        }

        double standardDeviation = Math.sqrt(variance / values.length);
        if (standardDeviation < 1e-12 || !Double.isFinite(standardDeviation)) {
            Arrays.fill(values, 0.0);
            return;
        }
        for (int i = 0; i < values.length; i++) {
            values[i] = (values[i] - mean) / standardDeviation;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() || "all".equalsIgnoreCase(value)
                ? null
                : value;
    }

    private enum Method {
        EXACT("exact MSM") {
            @Override
            double distance(double[] x, double[] y) {
                return MSM.distance(x, y, MSM_C);
            }
        },
        TV("TV pruning") {
            @Override
            double distance(double[] x, double[] y) {
                return MSM.distancePrunedByTotalVariation(x, y, MSM_C);
            }
        };
//        DIRECTED_VARIATION("directed variation") {
//            @Override
//            double distance(double[] x, double[] y) {
//                return MSM.distancePrunedByDirectedVariation(x, y, MSM_C);
//            }
//        },
//        TWO_BIN("2-bin directed crossing") {
//            @Override
//            double distance(double[] x, double[] y) {
//                return MSM.distancePrunedByTwoBinDirectedCrossing(x, y, MSM_C);
//            }
//        },
//        ZERO_REFERENCE("zero-reference fixed UB") {
//            @Override
//            double distance(double[] x, double[] y) {
//                return MSM.distancePrunedByConstantReference(x, y, MSM_C);
//            }
//        },
//        ZERO_REFERENCE_UPDATED("zero-reference updated UB") {
//            @Override
//            double distance(double[] x, double[] y) {
//                return MSM.distancePrunedByConstantReferenceWithUpperBoundUpdates(
//                        x, y, MSM_C);
//            }
//        },
//        CONSTANT_UB_TV("constant UB updates + TV") {
//            @Override
//            double distance(double[] x, double[] y) {
//                return MSM.distancePrunedByTotalVariationWithConstantUpperBoundUpdates(
//                        x, y, MSM_C);
//            }
//        };

        private final String label;

        Method(String label) {
            this.label = label;
        }

        abstract double distance(double[] x, double[] y);
    }

    private static final class Summary {
        private final String name;
        private final double[] totalMillis = new double[METHODS.length];
        private int pairs;

        private Summary(String name) {
            this.name = name;
        }

        private void add(Result result) {
            for (Method method : METHODS) {
                totalMillis[method.ordinal()] += result.millis()[method.ordinal()];
            }
            pairs++;
        }

        private void print() {
            if (pairs == 0) {
                return;
            }
            double[] averageMillis = new double[METHODS.length];
            for (Method method : METHODS) {
                averageMillis[method.ordinal()] = totalMillis[method.ordinal()] / pairs;
            }
            System.out.printf(Locale.US, "%n%s pairs=%d average per distance:%n", name, pairs);
            printTimes("  ", averageMillis);
        }
    }

    private record Config(
            String datasetFilter,
            int pairsPerDataset,
            int maxSeriesPerDataset,
            long seed,
            int repetitions,
            int warmupRepetitions) {

        private static Config parse(String[] args) {
            String filter = args.length > 0 ? blankToNull(args[0]) : null;
            int pairs = args.length > 1
                    ? Integer.parseInt(args[1])
                    : DEFAULT_PAIRS_PER_DATASET;
            int maxSeries = args.length > 2
                    ? Integer.parseInt(args[2])
                    : DEFAULT_MAX_SERIES_PER_DATASET;
            long seed = args.length > 3 ? Long.parseLong(args[3]) : DEFAULT_SEED;
            int repetitions = args.length > 4
                    ? Integer.parseInt(args[4])
                    : DEFAULT_REPETITIONS;
            int warmup = args.length > 5
                    ? Integer.parseInt(args[5])
                    : DEFAULT_WARMUP_REPETITIONS;
            if (pairs <= 0 || maxSeries < 2 || repetitions <= 0 || warmup < 0) {
                throw new IllegalArgumentException(
                        "pairs and repetitions must be positive, maxSeries at least 2, "
                                + "and warmup non-negative");
            }
            return new Config(filter, pairs, maxSeries, seed, repetitions, warmup);
        }
    }

    private record Pair(double[] x, double[] y) {}

    private record Result(
            int xLength,
            int yLength,
            double distance,
            double[] millis) {}
}
