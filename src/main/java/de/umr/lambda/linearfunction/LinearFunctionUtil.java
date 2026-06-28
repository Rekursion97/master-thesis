package de.umr.lambda.linearfunction;

import de.umr.lambda.lowerbound.ClippedBandLineBound;
import de.umr.lambda.lowerbound.LowerBoundContext;
import de.umr.lambda.lowerbound.PiecewiseLinearEnvelopeLowerBound;

public final class LinearFunctionUtil {
    private LinearFunctionUtil() {}

    public static double lowerBound(
            double a,
            double b,
            int n,
            double minY,
            double maxY,
            double c) {

        return ClippedBandLineBound.sum(a, b, n, minY, maxY, c);
    }

    public static double[] lowerBoundWithMaxValue(
            double a,
            double b,
            int n,
            double minY,
            double maxY,
            double c,
            double subtract) {

        ClippedBandLineBound.Contribution contribution =
                ClippedBandLineBound.sumWithMax(a, b, n, minY, maxY, c, subtract);
        return new double[]{contribution.sum(), contribution.max()};
    }

    public static double lowerBound(
            double[] tsX,
            double[] tsY,
            double c,
            double w,
            LineFitter.FitType fitType,
            double cFit,
            int iterations) {

        return PiecewiseLinearEnvelopeLowerBound.oneSided()
                .estimate(tsX, tsY, context(c, w, fitType, cFit, iterations));
    }

    public static double lowerBoundWithMaxValuesTwoSided(
            double[] tsX,
            double[] tsY,
            double c,
            double w,
            LineFitter.FitType fitType,
            double cFit,
            int iterations) {

        return PiecewiseLinearEnvelopeLowerBound.twoSidedRepair()
                .estimate(tsX, tsY, context(c, w, fitType, cFit, iterations));
    }

    private static LowerBoundContext context(
            double c,
            double window,
            LineFitter.FitType fitType,
            double fitC,
            int iterations) {

        return new LowerBoundContext(c, Math.max(1, (int) window), fitType, fitC, iterations);
    }

}
