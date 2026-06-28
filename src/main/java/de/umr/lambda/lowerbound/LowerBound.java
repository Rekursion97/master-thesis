package de.umr.lambda.lowerbound;

public interface LowerBound {
    String name();

    double estimate(double[] x, double[] y, LowerBoundContext context);
}
