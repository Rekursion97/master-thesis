package de.umr.lambda.lowerbound;

public final class ClippedBandLineBound {
    private ClippedBandLineBound() {}

    public record Contribution(double sum, double max) {}

    public static double sum(
            double a,
            double b,
            int n,
            double minY,
            double maxY,
            double c) {

        if (n <= 0 || c <= 0.0) {
            return 0.0;
        }

        double upper = sumClippedPositive(a, b - maxY, n, c);
        double lower = sumClippedPositive(-a, minY - b, n, c);
        return upper + lower;
    }

    public static Contribution sumWithMax(
            double a,
            double b,
            int n,
            double minY,
            double maxY,
            double c,
            double subtract) {

        if (n <= 0 || c <= 0.0) {
            return new Contribution(0.0, 0.0);
        }

        Contribution upper = sumClippedPositiveWithMax(a, b - maxY, n, c, subtract);
        Contribution lower = sumClippedPositiveWithMax(-a, minY - b, n, c, subtract);

        return new Contribution(
                upper.sum() + lower.sum(),
                Math.max(upper.max(), lower.max()));
    }

    public static double point(
            double a,
            double b,
            int i,
            double minY,
            double maxY,
            double c,
            double subtract) {

        if (c <= 0.0 || subtract >= c) {
            return 0.0;
        }

        double y = a * i + b;
        double value = 0.0;
        if (y > maxY) {
            value = Math.min(y - maxY, c);
        } else if (y < minY) {
            value = Math.min(minY - y, c);
        }

        return Math.max(0.0, value - subtract);
    }

    private static double sumClippedPositive(double a, double b, int n, double c) {
        if (a == 0.0) {
            if (b <= 0.0) return 0.0;
            if (b >= c) return n * c;
            return n * b;
        }

        if (a < 0.0) {
            b = b + a * (n - 1);
            a = -a;
        }

        int firstPos = (int) Math.floor(-b / a) + 1;
        int firstCap = (int) Math.ceil((c - b) / a);

        int rampL = clamp(firstPos, 0, n);
        int rampR = clamp(firstCap, 0, n) - 1;

        double total = sumLinear(a, b, rampL, rampR);

        int capL = clamp(firstCap, 0, n);
        total += (n - capL) * c;

        return Math.max(total, 0.0);
    }

    private static Contribution sumClippedPositiveWithMax(
            double a,
            double b,
            int n,
            double c,
            double subtract) {

        if (n <= 0 || subtract >= c) {
            return new Contribution(0.0, 0.0);
        }

        if (a == 0.0) {
            double clipped = Math.min(Math.max(0.0, b), c);
            double term = Math.max(0.0, clipped - subtract);
            return new Contribution(n * term, term);
        }

        if (a < 0.0) {
            b = b + a * (n - 1);
            a = -a;
        }

        int firstActive = (int) Math.floor((subtract - b) / a) + 1;
        int firstCap = (int) Math.ceil((c - b) / a);

        int rampL = clamp(firstActive, 0, n);
        int rampR = clamp(firstCap, 0, n) - 1;

        double total = sumLinearSubtract(a, b, rampL, rampR, subtract);

        int capL = clamp(firstCap, 0, n);
        double cappedTerm = c - subtract;
        total += (n - capL) * cappedTerm;

        double maxRaw = b + a * (n - 1);
        double maxContribution =
                Math.max(0.0, Math.min(Math.max(0.0, maxRaw), c) - subtract);

        return new Contribution(Math.max(0.0, total), maxContribution);
    }

    private static double sumLinear(double a, double b, int left, int right) {
        if (left > right) return 0.0;

        double len = (double) right - left + 1.0;
        double sumI = len * ((double) left + right) * 0.5;

        return a * sumI + b * len;
    }

    private static double sumLinearSubtract(
            double a,
            double b,
            int left,
            int right,
            double subtract) {

        if (left > right) return 0.0;

        double len = (double) right - left + 1.0;
        double sumI = len * ((double) left + right) * 0.5;

        return a * sumI + (b - subtract) * len;
    }

    private static int clamp(int x, int lo, int hi) {
        return x < lo ? lo : Math.min(x, hi);
    }
}
