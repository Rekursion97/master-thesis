package de.umr.lambda.msm;

import java.util.Arrays;

public final class MSM {
    private MSM() {}

    public static double distance(double[] x, double[] y, double c) {
        return distance(x, y, c, Math.max(x.length, y.length));
    }

    public static double distance(double[] x, double[] y, double c, int window) {
        int n = x.length, m = y.length;
        if (n == 0 || m == 0) {
            throw new IllegalArgumentException("MSM requires non-empty series");
        }

        // Must at least allow the endpoint to be reachable.
        window = Math.max(window, Math.abs(n - m));

        final double INF = Double.POSITIVE_INFINITY;

        double[] prev = new double[m];
        double[] curr = new double[m];

        Arrays.fill(prev, INF);
        Arrays.fill(curr, INF);

        prev[0] = Math.abs(x[0] - y[0]);

        // first row
        int jEnd0 = Math.min(m - 1, window);
        for (int j = 1; j <= jEnd0; j++) {
            prev[j] = prev[j - 1] + cost(y[j], x[0], y[j - 1], c);
        }

        for (int i = 1; i < n; i++) {
            Arrays.fill(curr, INF);

            int jStart = Math.max(0, i - window);
            int jEnd   = Math.min(m - 1, i + window);

            if (jStart == 0) {
                curr[0] = prev[0] + cost(x[i], x[i - 1], y[0], c);
                jStart = 1;
            }

            for (int j = jStart; j <= jEnd; j++) {
                double move = prev[j - 1] + Math.abs(x[i] - y[j]);
                double splitMergeX = prev[j] + cost(x[i], x[i - 1], y[j], c);
                double splitMergeY = curr[j - 1] + cost(y[j], x[i], y[j - 1], c);

                curr[j] = Math.min(move, Math.min(splitMergeX, splitMergeY));
            }

            double[] tmp = prev;
            prev = curr;
            curr = tmp;
        }

        return prev[m - 1];
    }

    private static double cost(double newPoint, double prevPoint, double otherPoint, double c) {
        if ((prevPoint <= newPoint && newPoint <= otherPoint) ||
                (prevPoint >= newPoint && newPoint >= otherPoint)) {
            return c;
        }

        return c + Math.min(
                Math.abs(newPoint - prevPoint),
                Math.abs(newPoint - otherPoint)
        );
    }
}