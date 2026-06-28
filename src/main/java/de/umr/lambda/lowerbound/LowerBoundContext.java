package de.umr.lambda.lowerbound;

import de.umr.lambda.linearfunction.LineFitter;

public record LowerBoundContext(
        double msmC,
        int window,
        LineFitter.FitType fitType,
        double fitC,
        int fitIterations) {

    public LowerBoundContext {
        if (msmC < 0.0) {
            throw new IllegalArgumentException("MSM c must be non-negative");
        }
        if (window <= 0) {
            throw new IllegalArgumentException("window must be positive");
        }
        if (fitType == null) {
            throw new IllegalArgumentException("fitType must not be null");
        }
    }
}
