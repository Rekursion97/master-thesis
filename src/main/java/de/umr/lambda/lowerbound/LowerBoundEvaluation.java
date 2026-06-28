package de.umr.lambda.lowerbound;

public record LowerBoundEvaluation(String name, double lowerBound, double exactDistance) {
    public double ratio() {
        if (exactDistance == 0.0) {
            return lowerBound == 0.0 ? 1.0 : Double.POSITIVE_INFINITY;
        }
        return lowerBound / exactDistance;
    }
}
