package de.umr.lambda;

import java.util.*;

public class MSMRandomTest {

    enum Op { START, MOVE, SPLIT, MERGE }

    static class Step {
        Op op;
        int i, j;

        Step(Op op, int i, int j) {
            this.op = op;
            this.i = i;
            this.j = j;
        }
    }

    static class MSMResult {
        double distance;
        List<Step> path = new ArrayList<>();
    }

    static MSMResult msm(double[] x, double[] y, double msmC) {
        int n = x.length;
        int m = y.length;

        double[][] dp = new double[n][m];
        Op[][] parent = new Op[n][m];

        dp[0][0] = Math.abs(x[0] - y[0]);
        parent[0][0] = Op.START;

        for (int i = 1; i < n; i++) {
            dp[i][0] = dp[i - 1][0] + cost(x[i], x[i - 1], y[0], msmC);
            parent[i][0] = Op.SPLIT;
        }

        for (int j = 1; j < m; j++) {
            dp[0][j] = dp[0][j - 1] + cost(y[j], x[0], y[j - 1], msmC);
            parent[0][j] = Op.MERGE;
        }

        for (int i = 1; i < n; i++) {
            for (int j = 1; j < m; j++) {
                double move = dp[i - 1][j - 1] + Math.abs(x[i] - y[j]);
                double split = dp[i - 1][j] + cost(x[i], x[i - 1], y[j], msmC);
                double merge = dp[i][j - 1] + cost(y[j], x[i], y[j - 1], msmC);

                if (move <= split && move <= merge) {
                    dp[i][j] = move;
                    parent[i][j] = Op.MOVE;
                } else if (split <= merge) {
                    dp[i][j] = split;
                    parent[i][j] = Op.SPLIT;
                } else {
                    dp[i][j] = merge;
                    parent[i][j] = Op.MERGE;
                }
            }
        }

        MSMResult result = new MSMResult();
        result.distance = dp[n - 1][m - 1];

        int i = n - 1;
        int j = m - 1;

        while (!(i == 0 && j == 0)) {
            Op op = parent[i][j];
            result.path.add(new Step(op, i, j));

            if (op == Op.MOVE) {
                i--;
                j--;
            } else if (op == Op.SPLIT) {
                i--;
            } else if (op == Op.MERGE) {
                j--;
            } else {
                throw new IllegalStateException("Invalid parent");
            }
        }

        result.path.add(new Step(Op.START, 0, 0));
        Collections.reverse(result.path);

        return result;
    }

    static double reconstructCostOnPath(List<Step> path, double[] x, double[] y, double msmC) {
        double sum = 0.0;

        for (Step step : path) {
            int i = step.i;
            int j = step.j;

            switch (step.op) {
                case START:
                case MOVE:
                    sum += Math.abs(x[i] - y[j]);
                    break;

                case SPLIT:
                    sum += cost(x[i], x[i - 1], y[j], msmC);
                    break;

                case MERGE:
                    sum += cost(y[j], x[i], y[j - 1], msmC);
                    break;
            }
        }

        return sum;
    }

    static double cost(double x, double y, double z, double msmC) {
        if ((y <= x && x <= z) || (z <= x && x <= y)) {
            return msmC;
        }
        return msmC + Math.min(Math.abs(x - y), Math.abs(x - z));
    }

    static double[] line(double slope, double offset, int n) {
        double[] t = new double[n];

        for (int i = 0; i < n; i++) {
            t[i] = slope * i + offset;
        }

        return t;
    }

    static double[] transformToMakeT1Zero(double[] t, double a, double b) {
        double lambda = 1.0 / Math.sqrt(1.0 + a * a);
        double[] out = new double[t.length];

        for (int i = 0; i < t.length; i++) {
            out[i] = (t[i] - (a * i + b)) * lambda;
        }

        return out;
    }

    static double randomDouble(Random rng, double min, double max) {
        return min + rng.nextDouble() * (max - min);
    }

    public static void main(String[] args) {
        Random rng = new Random(45);

        int tests = 10_000;
        int n = 200;
        double msmC = 1.0;
        double eps = 1e-9;

        int transformedPathMoreExpensive = 0;
        int equal = 0;
        int impossibleCheaper = 0;

        double maxExtraCost = 0.0;

        for (int test = 0; test < tests; test++) {
            double a = randomDouble(rng, -10.0, 10.0);
            double b = randomDouble(rng, -10.0, 10.0);

            double cSlope = randomDouble(rng, -10.0, 10.0);
            double d = randomDouble(rng, -10.0, 10.0);

            double[] t1 = line(a, b, n);
            double[] t2 = line(cSlope, d, n);

            double[] t1Transformed = transformToMakeT1Zero(t1, a, b);
            double[] t2Transformed = transformToMakeT1Zero(t2, a, b);

            MSMResult original = msm(t1, t2, msmC);
            MSMResult transformed = msm(t1Transformed, t2Transformed, msmC);

            if(original.distance < transformed.distance)
                System.out.println("happened");

            double transformedPathOnOriginal =
                    reconstructCostOnPath(transformed.path, t1, t2, msmC);

            double diff = transformedPathOnOriginal - original.distance;

            if (diff > eps) {
                transformedPathMoreExpensive++;
                maxExtraCost = Math.max(maxExtraCost, diff);
            } else if (Math.abs(diff) <= eps) {
                equal++;
            } else {
                // This should not happen, except tiny floating-point noise,
                // because original.distance is the optimal cost on original data.
                impossibleCheaper++;
                System.out.println("Unexpected cheaper path!");
                System.out.println("diff = " + diff);
            }
        }

        System.out.println("tests = " + tests);
        System.out.println("transformed path more expensive on original data = "
                + transformedPathMoreExpensive);
        System.out.println("equal to original optimum = " + equal);
        System.out.println("unexpected cheaper cases = " + impossibleCheaper);
        System.out.println("max extra cost = " + maxExtraCost);
    }
}