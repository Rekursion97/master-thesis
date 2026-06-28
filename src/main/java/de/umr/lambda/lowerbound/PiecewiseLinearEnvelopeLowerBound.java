package de.umr.lambda.lowerbound;

import de.umr.lambda.linearfunction.LineFitter;
import de.umr.lambda.linearfunction.PiecewiseLinear;
import de.umr.lambda.msm.MSM;

import java.util.ArrayList;
import java.util.List;

public final class PiecewiseLinearEnvelopeLowerBound implements LowerBound {
    private final boolean twoSidedRepair;

    public PiecewiseLinearEnvelopeLowerBound(boolean twoSidedRepair) {
        this.twoSidedRepair = twoSidedRepair;
    }

    public static PiecewiseLinearEnvelopeLowerBound oneSided() {
        return new PiecewiseLinearEnvelopeLowerBound(false);
    }

    public static PiecewiseLinearEnvelopeLowerBound twoSidedRepair() {
        return new PiecewiseLinearEnvelopeLowerBound(true);
    }

    @Override
    public String name() {
        return twoSidedRepair
                ? "piecewise-linear-envelope-two-sided"
                : "piecewise-linear-envelope";
    }

    @Override
    public double estimate(double[] x, double[] y, LowerBoundContext context) {
        List<LineFitter.Lin> xLines = fitSegments(x, context);
        List<LineFitter.Lin> yLines = fitSegments(y, context);

        double approximationPenalty = approximationPenalty(x, y, xLines, yLines, context);
        double rawBound = twoSidedRepair
                ? estimateTwoSided(xLines, yLines, context.msmC()) - approximationPenalty
                : estimateOneSided(xLines, yLines, context.msmC()) - approximationPenalty;

        return Math.max(0.0, rawBound);
    }

    public double[] contributions(double[] x, double[] y, LowerBoundContext context) {
        List<LineFitter.Lin> xLines = fitSegments(x, context);
        List<LineFitter.Lin> yLines = fitSegments(y, context);

        double[] rawContributions = twoSidedRepair
                ? estimateTwoSidedContributions(x.length, xLines, yLines, context.msmC(), context.window())
                : estimateOneSidedContributions(x.length, xLines, yLines, context.msmC(), context.window());

        double rawSum = sum(rawContributions);
        if (rawSum <= 0.0) {
            return rawContributions;
        }

        double approximationPenalty = approximationPenalty(x, y, xLines, yLines, context);
        double lowerBound = Math.max(0.0, rawSum - approximationPenalty);
        double scale = lowerBound / rawSum;

        for (int i = 0; i < rawContributions.length; i++) {
            rawContributions[i] *= scale;
        }
        return rawContributions;
    }

    public LowerBoundEvaluation evaluate(double[] x, double[] y, LowerBoundContext context) {
        double lowerBound = estimate(x, y, context);
        double exact = MSM.distance(x, y, context.msmC(), context.window());
        return new LowerBoundEvaluation(name(), lowerBound, exact);
    }

    private static List<LineFitter.Lin> fitSegments(double[] series, LowerBoundContext context) {
        ArrayList<LineFitter.Lin> lines = new ArrayList<>();
        int window = context.window();

        for (int start = 0; start < series.length; start += window) {
            int end = Math.min(start + window, series.length);
            lines.add(LineFitter.fitLine(
                    series,
                    start,
                    end,
                    context.fitType(),
                    context.fitC(),
                    context.fitIterations()));
        }

        return lines;
    }

    private static double approximationPenalty(
            double[] x,
            double[] y,
            List<LineFitter.Lin> xLines,
            List<LineFitter.Lin> yLines,
            LowerBoundContext context) {

        double[] approxX = PiecewiseLinear.sample(xLines);
        double[] approxY = PiecewiseLinear.sample(yLines);

        double xPenalty = MSM.distance(x, approxX, context.msmC(), context.window());
        double yPenalty = MSM.distance(y, approxY, context.msmC(), context.window());

        return xPenalty + yPenalty;
    }

    private static double estimateOneSided(
            List<LineFitter.Lin> source,
            List<LineFitter.Lin> target,
            double c) {

        int n = Math.min(source.size(), target.size());
        double sum = 0.0;

        for (int i = 0; i < n; i++) {
            Range targetEnvelope = envelope(target, Math.max(0, i - 1), Math.min(n - 1, i + 1));
            LineFitter.Lin line = source.get(i);

            sum += ClippedBandLineBound.sum(
                    line.a(),
                    line.b(),
                    line.end(),
                    targetEnvelope.min(),
                    targetEnvelope.max(),
                    c);
        }

        return sum;
    }

    private static double[] estimateOneSidedContributions(
            int length,
            List<LineFitter.Lin> source,
            List<LineFitter.Lin> target,
            double c,
            int window) {

        int n = Math.min(source.size(), target.size());
        double[] contributions = new double[length];

        for (int i = 0; i < n; i++) {
            Range targetEnvelope = envelope(target, Math.max(0, i - 1), Math.min(n - 1, i + 1));
            LineFitter.Lin line = source.get(i);
            addLineContributions(
                    contributions,
                    i * window,
                    line,
                    targetEnvelope.min(),
                    targetEnvelope.max(),
                    c,
                    0.0);
        }

        return contributions;
    }

    private static double estimateTwoSided(
            List<LineFitter.Lin> xLines,
            List<LineFitter.Lin> yLines,
            double c) {

        double xFirst = processLines(xLines, yLines, c);
        double yFirst = processLines(yLines, xLines, c);
        return Math.max(xFirst, yFirst);
    }

    private static double[] estimateTwoSidedContributions(
            int length,
            List<LineFitter.Lin> xLines,
            List<LineFitter.Lin> yLines,
            double c,
            int window) {

        double[] xFirst = processLinesContributions(length, xLines, yLines, c, window);
        double[] yFirst = processLinesContributions(length, yLines, xLines, c, window);
        return sum(xFirst) >= sum(yFirst) ? xFirst : yFirst;
    }

    private static double processLines(
            List<LineFitter.Lin> source,
            List<LineFitter.Lin> target,
            double c) {

        int n = Math.min(source.size(), target.size());
        if (n == 0) return 0.0;

        double sum = 0.0;
        double[] sourceMax = new double[n];

        for (int i = 0; i < n; i++) {
            Range targetEnvelope = envelope(target, Math.max(0, i - 1), Math.min(n - 1, i + 1));
            LineFitter.Lin line = source.get(i);

            ClippedBandLineBound.Contribution contribution =
                    ClippedBandLineBound.sumWithMax(
                            line.a(),
                            line.b(),
                            line.end(),
                            targetEnvelope.min(),
                            targetEnvelope.max(),
                            c,
                            0.0);

            sum += contribution.sum();
            sourceMax[i] = contribution.max();
        }

        for (int j = 0; j < n; j++) {
            int lo = Math.max(0, j - 1);
            int hi = Math.min(n - 1, j + 1);

            Range sourceEnvelope = envelope(source, lo, hi);
            double subtract = max(sourceMax, lo, hi);
            LineFitter.Lin line = target.get(j);

            sum += ClippedBandLineBound.sumWithMax(
                    line.a(),
                    line.b(),
                    line.end(),
                    sourceEnvelope.min(),
                    sourceEnvelope.max(),
                    c,
                    subtract).sum();
        }

        return sum;
    }

    private static double[] processLinesContributions(
            int length,
            List<LineFitter.Lin> source,
            List<LineFitter.Lin> target,
            double c,
            int window) {

        int n = Math.min(source.size(), target.size());
        double[] contributions = new double[length];
        if (n == 0) return contributions;

        double[] sourceMax = new double[n];

        for (int i = 0; i < n; i++) {
            Range targetEnvelope = envelope(target, Math.max(0, i - 1), Math.min(n - 1, i + 1));
            LineFitter.Lin line = source.get(i);

            addLineContributions(
                    contributions,
                    i * window,
                    line,
                    targetEnvelope.min(),
                    targetEnvelope.max(),
                    c,
                    0.0);

            sourceMax[i] = ClippedBandLineBound.sumWithMax(
                    line.a(),
                    line.b(),
                    line.end(),
                    targetEnvelope.min(),
                    targetEnvelope.max(),
                    c,
                    0.0).max();
        }

        for (int j = 0; j < n; j++) {
            int lo = Math.max(0, j - 1);
            int hi = Math.min(n - 1, j + 1);

            Range sourceEnvelope = envelope(source, lo, hi);
            double subtract = max(sourceMax, lo, hi);
            LineFitter.Lin line = target.get(j);

            addLineContributions(
                    contributions,
                    j * window,
                    line,
                    sourceEnvelope.min(),
                    sourceEnvelope.max(),
                    c,
                    subtract);
        }

        return contributions;
    }

    private static void addLineContributions(
            double[] contributions,
            int offset,
            LineFitter.Lin line,
            double min,
            double max,
            double c,
            double subtract) {

        for (int i = 0; i < line.end(); i++) {
            int index = offset + i;
            if (index >= contributions.length) {
                return;
            }

            contributions[index] += ClippedBandLineBound.point(
                    line.a(),
                    line.b(),
                    i,
                    min,
                    max,
                    c,
                    subtract);
        }
    }

    private record Range(double min, double max) {}

    private static Range envelope(List<LineFitter.Lin> lines, int lo, int hi) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        for (int i = lo; i <= hi; i++) {
            LineFitter.Lin line = lines.get(i);
            min = Math.min(min, line.minValue());
            max = Math.max(max, line.maxValue());
        }

        return new Range(min, max);
    }

    private static double max(double[] values, int lo, int hi) {
        double max = 0.0;
        for (int i = lo; i <= hi; i++) {
            max = Math.max(max, values[i]);
        }
        return max;
    }

    private static double sum(double[] values) {
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum;
    }
}
