package de.umr.lambda.linearfunction;

import java.util.*;

public class LineFitter {

    public enum FitType {
        L2,
        L1,
        MSM_ITERATIVE,
        MSM_SHAPE_AWARE,
        MSM_BEST_TO_POINTS
    }

    public record Lin(double a, double b, int start, int end) {
        public double value(int i) {
            return a * i + b;
        }

        public double minValue() {
            return Math.min(b, a * (end - 1) + b);
        }

        public double maxValue() {
            return Math.max(b, a * (end - 1) + b);
        }
    }

    public static Lin fitLine(double[] y, int start, int end,
                              FitType type, double c, int iterations) {
        return switch (type) {
            case MSM_BEST_TO_POINTS -> fitLineMSMBestTwoPoint(y,start,end,c);
            case L2 -> fitLineL2(y, start, end);
            case L1 -> fitLineL1(y, start, end);
            case MSM_ITERATIVE -> fitLineMSMIterative(y, start, end, c, iterations);
            case MSM_SHAPE_AWARE -> fitLineMSMShapeAwareIterative(y, start, end, c, iterations);
        };
    }

    public static Lin fitLineL2(double[] y, int start, int end) {
        int len = end - start;
        if (len <= 0) throw new IllegalArgumentException("empty segment");
        if (len == 1) return new Lin(0.0, y[start], 0, 1);

        double sx = 0, sy = 0, sxx = 0, sxy = 0;

        for (int i = 0; i < len; i++) {
            double v = y[start + i];
            sx += i;
            sy += v;
            sxx += (double) i * i;
            sxy += i * v;
        }

        double den = len * sxx - sx * sx;
        double a = (len * sxy - sx * sy) / den;
        double b = (sy - a * sx) / len;

        return new Lin(a, b, 0, len);
    }

    public static Lin fitLineL1(double[] y, int start, int end) {
        int len = end - start;
        if (len <= 0) throw new IllegalArgumentException("empty segment");
        if (len == 1) return new Lin(0.0, y[start], 0, 1);

        double minY = y[start], maxY = y[start];
        for (int i = start + 1; i < end; i++) {
            minY = Math.min(minY, y[i]);
            maxY = Math.max(maxY, y[i]);
        }

        double span = Math.max(1e-9, maxY - minY);
        double lo = -2.0 * span / Math.max(1.0, len - 1.0);
        double hi =  2.0 * span / Math.max(1.0, len - 1.0);

        for (int it = 0; it < 80; it++) {
            double m1 = lo + (hi - lo) / 3.0;
            double m2 = hi - (hi - lo) / 3.0;

            if (l1ObjectiveForSlope(y, start, end, m1)
                    < l1ObjectiveForSlope(y, start, end, m2)) {
                hi = m2;
            } else {
                lo = m1;
            }
        }

        double a = 0.5 * (lo + hi);
        double b = medianResidual(y, start, end, a);

        return new Lin(a, b, 0, len);
    }

    public static Lin fitLineMSMIterative(
            double[] y, int start, int end, double c, int iterations) {

        int len = end - start;
        if (len <= 0) throw new IllegalArgumentException("empty segment");
        if (len == 1) return new Lin(0.0, y[start], 0, 1);

        Lin line = fitLineL1(y, start, end);

        for (int it = 0; it < iterations; it++) {
            double[] approx = sample(line);
            int[][] pairs = msmDiagonalPairs(y, start, end, approx, c);

            if (pairs.length < 2) break;

            line = fitLineL1FromPairs(y, start, pairs, len);
        }

        return line;
    }

    public static Lin fitLineMSMShapeAwareIterative(
            double[] y, int start, int end, double c, int iterations) {

        int len = end - start;
        if (len <= 0) throw new IllegalArgumentException("empty segment");
        if (len == 1) return new Lin(0.0, y[start], 0, 1);

        double endpointSlope = (y[end - 1] - y[start]) / (len - 1.0);
        boolean negativeTrend = endpointSlope < 0.0;

        Lin line = new Lin(endpointSlope, y[start], 0, len);

        for (int it = 0; it < iterations; it++) {
            double[] approx = sample(line);
            int[][] pairs = msmDiagonalPairs(y, start, end, approx, c);

            if (pairs.length < 2) break;

            line = fitShapeAwareFromPairs(
                    y, start, pairs, len, negativeTrend, endpointSlope);
        }

        return line;
    }

    private static Lin fitShapeAwareFromPairs(
            double[] y, int start, int[][] pairs, int len,
            boolean negativeTrend, double endpointSlope) {

        double minY = y[start], maxY = y[start];
        for (int i = 0; i < len; i++) {
            minY = Math.min(minY, y[start + i]);
            maxY = Math.max(maxY, y[start + i]);
        }

        double span = Math.max(1e-9, maxY - minY);
        double radius = 2.0 * span / Math.max(1.0, len - 1.0);

        double lo = endpointSlope - radius;
        double hi = endpointSlope + radius;

        if (negativeTrend) hi = Math.min(hi, 0.0);
        else lo = Math.max(lo, 0.0);

        for (int it = 0; it < 80; it++) {
            double m1 = lo + (hi - lo) / 3.0;
            double m2 = hi - (hi - lo) / 3.0;

            if (shapeAwareObjective(y, start, pairs, len, m1, negativeTrend)
                    < shapeAwareObjective(y, start, pairs, len, m2, negativeTrend)) {
                hi = m2;
            } else {
                lo = m1;
            }
        }

        double a = 0.5 * (lo + hi);
        double b = shapeAwareBestIntercept(y, start, pairs, len, a, negativeTrend);

        return new Lin(a, b, 0, len);
    }

    private static double shapeAwareObjective(
            double[] y, int start, int[][] pairs, int len,
            double a, boolean negativeTrend) {

        double b = shapeAwareBestIntercept(y, start, pairs, len, a, negativeTrend);
        return shapeAwareObjectiveWithB(y, start, pairs, len, a, b, negativeTrend);
    }

    private static double shapeAwareBestIntercept(
            double[] y, int start, int[][] pairs, int len,
            double a, boolean negativeTrend) {

        double base = pairMedianResidual(y, start, pairs, a);

        double minY = y[start], maxY = y[start];
        for (int i = 0; i < len; i++) {
            minY = Math.min(minY, y[start + i]);
            maxY = Math.max(maxY, y[start + i]);
        }

        double step = Math.max(1e-9, (maxY - minY) / 30.0);

        double bestB = base;
        double bestObj = shapeAwareObjectiveWithB(
                y, start, pairs, len, a, base, negativeTrend);

        for (int s = -30; s <= 30; s++) {
            double b = base + s * step;
            double obj = shapeAwareObjectiveWithB(
                    y, start, pairs, len, a, b, negativeTrend);

            if (obj < bestObj) {
                bestObj = obj;
                bestB = b;
            }
        }

        return bestB;
    }

    private static double shapeAwareObjectiveWithB(
            double[] y, int start, int[][] pairs, int len,
            double a, double b, boolean negativeTrend) {

        double obj = 0.0;

        for (int[] p : pairs) {
            int yi = p[0];
            int xi = p[1];
            obj += Math.abs(y[start + yi] - (a * xi + b));
        }

        double f0 = b;
        double fLast = a * (len - 1) + b;

        obj += 6.0 * Math.abs(f0 - y[start]);
        obj += 3.0 * Math.abs(fLast - y[start + len - 1]);

        double shapePenalty = 0.0;

        if (negativeTrend) {
            for (int i = 0; i < len; i++) {
                double lineVal = a * i + b;
                double tsVal = y[start + i];

                if (tsVal > lineVal) {
                    shapePenalty += tsVal - lineVal;
                }
            }

            if (a > 0.0) shapePenalty += 1000.0 * a;

        } else {
            for (int i = 0; i < len; i++) {
                double lineVal = a * i + b;
                double tsVal = y[start + i];

                if (tsVal < lineVal) {
                    shapePenalty += lineVal - tsVal;
                }
            }

            if (a < 0.0) shapePenalty += 1000.0 * (-a);
        }

        obj += 0.5 * shapePenalty;

        return obj;
    }

    private static Lin fitLineL1FromPairs(
            double[] y, int start, int[][] pairs, int len) {

        double minY = y[start], maxY = y[start];
        for (int[] p : pairs) {
            double v = y[start + p[0]];
            minY = Math.min(minY, v);
            maxY = Math.max(maxY, v);
        }

        double span = Math.max(1e-9, maxY - minY);
        double lo = -2.0 * span / Math.max(1.0, len - 1.0);
        double hi =  2.0 * span / Math.max(1.0, len - 1.0);

        for (int it = 0; it < 80; it++) {
            double m1 = lo + (hi - lo) / 3.0;
            double m2 = hi - (hi - lo) / 3.0;

            if (pairObjective(y, start, pairs, m1)
                    < pairObjective(y, start, pairs, m2)) {
                hi = m2;
            } else {
                lo = m1;
            }
        }

        double a = 0.5 * (lo + hi);
        double b = pairMedianResidual(y, start, pairs, a);

        return new Lin(a, b, 0, len);
    }

    private static double l1ObjectiveForSlope(
            double[] y, int start, int end, double a) {

        double b = medianResidual(y, start, end, a);
        double sum = 0.0;

        for (int i = 0; i < end - start; i++) {
            sum += Math.abs(y[start + i] - (a * i + b));
        }

        return sum;
    }

    private static double medianResidual(
            double[] y, int start, int end, double a) {

        int len = end - start;
        double[] r = new double[len];

        for (int i = 0; i < len; i++) {
            r[i] = y[start + i] - a * i;
        }

        Arrays.sort(r);

        if ((len & 1) == 1) return r[len / 2];
        return 0.5 * (r[len / 2 - 1] + r[len / 2]);
    }

    private static double pairObjective(
            double[] y, int start, int[][] pairs, double a) {

        double b = pairMedianResidual(y, start, pairs, a);
        double sum = 0.0;

        for (int[] p : pairs) {
            int yi = p[0];
            int xi = p[1];
            sum += Math.abs(y[start + yi] - (a * xi + b));
        }

        return sum;
    }

    private static double pairMedianResidual(
            double[] y, int start, int[][] pairs, double a) {

        double[] r = new double[pairs.length];

        for (int k = 0; k < pairs.length; k++) {
            int yi = pairs[k][0];
            int xi = pairs[k][1];
            r[k] = y[start + yi] - a * xi;
        }

        Arrays.sort(r);

        if ((r.length & 1) == 1) return r[r.length / 2];
        return 0.5 * (r[r.length / 2 - 1] + r[r.length / 2]);
    }

    private static double[] sample(Lin line) {
        int len = line.end() - line.start();
        double[] out = new double[len];

        for (int i = 0; i < len; i++) {
            out[i] = line.a * i + line.b;
        }

        return out;
    }

    private static int[][] msmDiagonalPairs(
            double[] y, int start, int end, double[] approx, double c) {

        int n = end - start;
        int m = approx.length;

        double[][] dp = new double[n][m];
        byte[][] parent = new byte[n][m];

        dp[0][0] = Math.abs(y[start] - approx[0]);

        for (int i = 1; i < n; i++) {
            dp[i][0] = dp[i - 1][0]
                    + msmCost(y[start + i], y[start + i - 1], approx[0], c);
            parent[i][0] = 1;
        }

        for (int j = 1; j < m; j++) {
            dp[0][j] = dp[0][j - 1]
                    + msmCost(approx[j], y[start], approx[j - 1], c);
            parent[0][j] = 2;
        }

        for (int i = 1; i < n; i++) {
            for (int j = 1; j < m; j++) {
                double diag = dp[i - 1][j - 1]
                        + Math.abs(y[start + i] - approx[j]);

                double up = dp[i - 1][j]
                        + msmCost(y[start + i], y[start + i - 1], approx[j], c);

                double left = dp[i][j - 1]
                        + msmCost(approx[j], y[start + i], approx[j - 1], c);

                if (diag <= up && diag <= left) {
                    dp[i][j] = diag;
                    parent[i][j] = 0;
                } else if (up <= left) {
                    dp[i][j] = up;
                    parent[i][j] = 1;
                } else {
                    dp[i][j] = left;
                    parent[i][j] = 2;
                }
            }
        }

        ArrayList<int[]> pairs = new ArrayList<>();

        int i = n - 1;
        int j = m - 1;

        while (i > 0 || j > 0) {
            byte p = parent[i][j];

            if (p == 0) {
                pairs.add(new int[]{i, j});
                i--;
                j--;
            } else if (p == 1) {
                i--;
            } else {
                j--;
            }
        }

        pairs.add(new int[]{0, 0});
        Collections.reverse(pairs);

        return pairs.toArray(new int[pairs.size()][]);
    }

    private static double msmCost(
            double newPoint, double prevPoint, double otherPoint, double c) {

        if ((prevPoint <= newPoint && newPoint <= otherPoint) ||
                (prevPoint >= newPoint && newPoint >= otherPoint)) {
            return c;
        }

        return c + Math.min(
                Math.abs(newPoint - prevPoint),
                Math.abs(newPoint - otherPoint)
        );
    }

    public static Lin fitLineMSMBestTwoPoint(double[] y, int start, int end, double c) {
        int len = end - start;
        if (len <= 0) throw new IllegalArgumentException("empty segment");
        if (len == 1) return new Lin(0.0, y[start], 0, 1);

        double bestA = 0.0;
        double bestB = y[start];
        double bestDist = Double.POSITIVE_INFINITY;

        for (int p = 0; p < len; p++) {
            for (int q = p + 1; q < len; q++) {
                double a = (y[start + q] - y[start + p]) / (q - p);
                double b = y[start + p] - a * p;

                double dist = msmDistanceToLine(y, start, len, a, b, c);

                if (dist < bestDist) {
                    bestDist = dist;
                    bestA = a;
                    bestB = b;
                }
            }
        }

        return new Lin(bestA, bestB, 0, len);
    }

    private static double msmDistanceToLine(
            double[] y, int start, int len, double a, double b, double c) {

        double[] line = new double[len];
        for (int i = 0; i < len; i++) {
            line[i] = a * i + b;
        }

        return msmDistance(y, start, len, line, c);
    }

    private static double msmDistance(
            double[] y, int start, int len, double[] line, double c) {

        double[][] dp = new double[len][len];

        dp[0][0] = Math.abs(y[start] - line[0]);

        for (int i = 1; i < len; i++) {
            dp[i][0] = dp[i - 1][0]
                    + msmCost(y[start + i], y[start + i - 1], line[0], c);
        }

        for (int j = 1; j < len; j++) {
            dp[0][j] = dp[0][j - 1]
                    + msmCost(line[j], y[start], line[j - 1], c);
        }

        for (int i = 1; i < len; i++) {
            for (int j = 1; j < len; j++) {
                double move = dp[i - 1][j - 1] + Math.abs(y[start + i] - line[j]);
                double up = dp[i - 1][j] + msmCost(y[start + i], y[start + i - 1], line[j], c);
                double left = dp[i][j - 1] + msmCost(line[j], y[start + i], line[j - 1], c);

                dp[i][j] = Math.min(move, Math.min(up, left));
            }
        }

        return dp[len - 1][len - 1];
    }
}