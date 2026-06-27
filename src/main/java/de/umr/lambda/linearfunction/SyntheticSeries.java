package de.umr.lambda.linearfunction;

import java.util.*;

import static de.umr.lambda.linearfunction.LineFitter.fitLine;

public class SyntheticSeries {


    public static class Data {
        public double[][] series;      // [seriesId][i]
        public LineFitter.Lin[][] lines;          // [seriesId][windowId]
    }

    public static Data generate(int m, int n, double wFrac, long seed) {
        Random r = new Random(seed);
        int w = Math.max(2, (int)Math.round(wFrac * n));
        int k = n / w;

        Data d = new Data();
        d.series = new double[m][n];
        d.lines = new LineFitter.Lin[m][k];

        for (int s = 0; s < m; s++) {
            double trend = rand(r, -0.02, 0.02);
            double intercept = rand(r, -2, 2);
            double amp = rand(r, 0.5, 3.0);
            double period = rand(r, 80, 400);
            double phase = rand(r, 0, 2 * Math.PI);

            double walk = 0;
            for (int i = 0; i < n; i++) {
                walk += r.nextGaussian() * 0.08;
                d.series[s][i] =
                        intercept
                                + trend * i
                                + walk
                                + amp * Math.sin(2 * Math.PI * i / period + phase)
                                + r.nextGaussian() * 0.15;
            }

//            for (int win = 0; win < k; win++) {
//                int start = win * w, end = start + w;
//                d.lines[s][win] = fitLine(d.series[s], start, end);
//            }
        }
        return d;
    }



    static double rand(Random r, double lo, double hi) {
        return lo + r.nextDouble() * (hi - lo);
    }

    public static void main(String[] args) {
        int m = 20;
        int n = 2000;

        Data data = generate(m, n, 0.05, 42);

        System.out.println(data.series.length);       // 20
        System.out.println(data.series[0].length);    // 2000
        System.out.println(data.lines[0].length);     // 20

        LineFitter.Lin line = data.lines[0][0];
        System.out.println(line);
    }
}