package de.umr.lambda.benchmarks;

import de.umr.lambda.UCRMSMInterpolationExperiment;
import de.umr.lambda.linearfunction.LineFitter;
import de.umr.lambda.linearfunction.SyntheticSeries;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static de.umr.lambda.UCRMSMInterpolationExperiment.findDatasetDirs;
import static de.umr.lambda.UCRMSMInterpolationExperiment.readDataset;
import static de.umr.lambda.linearfunction.LinearFunctionUtil.lowerBound;
import static de.umr.lambda.linearfunction.LinearFunctionUtil.lowerBoundWithMaxValuesTwoSided;

public class Benchmark {
    static void main() throws IOException {

        Path root = Paths.get("C:\\Users\\gries\\Desktop\\Masterarbeit\\data\\UCRArchive_2018\\UCRArchive_2018");
        List<Path> datasetDirs = findDatasetDirs(root);

        for (Path dsDir : datasetDirs) {
            String name = dsDir.getFileName().toString();

            Path train = dsDir.resolve(name + "_TRAIN.tsv");
            Path test = dsDir.resolve(name + "_TEST.tsv");

            if (!Files.exists(train) || !Files.exists(test)) {
                continue;
            }

            long t0 = System.nanoTime();
            //System.out.println(name);
            UCRMSMInterpolationExperiment.Dataset raw = readDataset(train, test);
            double c = 0.1;
            for (int i = 0; i < Math.min(raw.trainSeries.length,20); i++) {
                for (int j = i+1; j < Math.min(raw.trainSeries.length,20); j++) {
                    int n = raw.trainSeries.length;
                    double[] tsX = trimTrailingNaN(raw.trainSeries[(int)(Math.random()*n)]);
                    zNormalize(tsX);
                    double[] tsY = trimTrailingNaN(raw.trainSeries[(int)(Math.random()*n)]);
                    zNormalize(tsY);
                    if(tsX.length != tsY.length ||name.compareTo("StarLightCurves")!=0)
                        continue;

//                    System.out.println(name);
//                    System.out.println(tsX.length);
                    //System.out.println("TSX length: " + tsX.length + ", TSY length: " + tsY.length);
                    System.out.println();
                    System.out.println("L1:");
                    //lowerBound(tsX, tsY, c, Math.max(Math.min(tsX.length,tsY.length) * 0.05,2), LineFitter.FitType.L1,0,0);
                    lowerBoundWithMaxValuesTwoSided(tsX, tsY, c, Math.max(Math.min(tsX.length,tsY.length) * 0.05,2), LineFitter.FitType.L1,0,0);
                    System.out.println("L2:");
                    //lowerBound(tsX, tsY, c, Math.max(Math.min(tsX.length,tsY.length) * 0.05,2), LineFitter.FitType.L2,0,0);
                    lowerBoundWithMaxValuesTwoSided(tsX, tsY, c, Math.max(Math.min(tsX.length,tsY.length) * 0.05,2), LineFitter.FitType.L2,0,0);
                    System.out.println("Iterative");
                    //lowerBound(tsX, tsY, c, Math.max(Math.min(tsX.length,tsY.length) * 0.05,2), LineFitter.FitType.MSM_ITERATIVE,c,20);
                    lowerBoundWithMaxValuesTwoSided(tsX, tsY, c, Math.max(Math.min(tsX.length,tsY.length) * 0.05,2), LineFitter.FitType.MSM_ITERATIVE,c,200);
                    System.out.println("Shape aware");
                    //lowerBound(tsX, tsY, c, Math.max(Math.min(tsX.length,tsY.length) * 0.05,2), LineFitter.FitType.MSM_SHAPE_AWARE,c,20);
                    lowerBoundWithMaxValuesTwoSided(tsX, tsY, c, Math.max(Math.min(tsX.length,tsY.length) * 0.05,2), LineFitter.FitType.MSM_SHAPE_AWARE,c,200);
                    System.out.println("Best two points");
                    lowerBoundWithMaxValuesTwoSided(tsX, tsY, c, Math.max(Math.min(tsX.length,tsY.length) * 0.05,2), LineFitter.FitType.MSM_BEST_TO_POINTS,c,200);
//                    System.out.println("switch");
//                    System.out.println("L1:");
//                    lowerBound(tsY, tsX, c, Math.max(Math.min(tsX.length,tsY.length) * 0.05,2), LineFitter.FitType.L1,0,0);
//                    System.out.println("L2:");
//                    lowerBound(tsY, tsX, c, Math.max(Math.min(tsX.length,tsY.length) * 0.05,2), LineFitter.FitType.L2,0,0);
//                    System.out.println("Iterative");
//                    lowerBound(tsY, tsX, c, Math.max(Math.min(tsX.length,tsY.length) * 0.05,2), LineFitter.FitType.MSM_ITERATIVE,c,200);
                }
            }
        }
    }

    private static double[] trimTrailingNaN(double[] ts) {
        int end = ts.length;
        while (end > 0 && Double.isNaN(ts[end - 1])) {
            end--;
        }

        if (end == ts.length) {
            return ts; // no trailing NaNs
        }

        return java.util.Arrays.copyOf(ts, end);
    }

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
}
