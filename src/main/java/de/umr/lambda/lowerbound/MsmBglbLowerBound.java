package de.umr.lambda.lowerbound;

public final class MsmBglbLowerBound implements LowerBound {
    public enum Type {
        PAPER,
        SEGMENT_RECTANGLE
    }

    private final Type type;

    public MsmBglbLowerBound() {
        this(Type.PAPER);
    }

    public MsmBglbLowerBound(Type type) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        this.type = type;
    }

    @Override
    public String name() {
        return type == Type.PAPER ? "BGLB_MSM" : "BGLB_MSM_RECT";
    }

    @Override
    public double estimate(double[] x, double[] y, LowerBoundContext context) {
        return estimate(x, y, context.msmC(), context.window());
    }

    public double estimate(double[] x, double[] y, double c, int window) {
        return estimate(x, y, c, window, type);
    }

    public double estimate(double[] x, double[] y, double c, int window, Type type) {
        validate(x, y, c, window);
        return Math.max(directed(x, y, c, window, type), directed(y, x, c, window, type));
    }

    public double[] contributions(double[] x, double[] y, double c, int window) {
        validate(x, y, c, window);

        DirectedContribution xFirst = directedContribution(x, y, c, window, type);
        DirectedContribution yFirst = directedContribution(y, x, c, window, type);
        return xFirst.sum() >= yFirst.sum() ? xFirst.values() : yFirst.values();
    }

    private static double directed(double[] x, double[] y, double c, int window, Type type) {
        return directedContribution(x, y, c, window, type).sum();
    }

    private static DirectedContribution directedContribution(double[] x, double[] y, double c, int window, Type type) {
        if (type == Type.SEGMENT_RECTANGLE) {
            return directedRectangleContribution(x, y, c, window);
        }

        double[] contributions = new double[x.length];
        double[] baseWeights = baseWeights(x, y, c, window);

        double base = 0.0;
        for (int i = 1; i < x.length - 1; i++) {
            base += baseWeights[i];
            contributions[i] += baseWeights[i];
        }

        double augmentation = 0.0;
        for (int j = 1; j < y.length - 1; j++) {
            Range xEnvelope = envelope(x, j, window);

            double delta;
            if (y[j] > xEnvelope.max()) {
                delta = delta(y[j], xEnvelope.max(), c);
            } else if (y[j] < xEnvelope.min()) {
                delta = delta(y[j], xEnvelope.min(), c);
            } else {
                delta = 0.0;
            }

            double maxBaseInWindow = windowMax(baseWeights, j, window);
            double contribution = Math.min(Math.max(delta - maxBaseInWindow, 0.0), c);
            augmentation += contribution;
            contributions[j] += contribution;
        }

        addBoundary(contributions, x, y, c);
        return new DirectedContribution(contributions, boundary(x, y, c) + base + augmentation);
    }

    private static DirectedContribution directedRectangleContribution(
            double[] x,
            double[] y,
            double c,
            int window) {

        double[] contributions = new double[x.length];
        double[] baseWeights = rectangleBaseWeights(x, y, c, window);

        double base = 0.0;
        for (int i = 1; i < x.length - 1; i++) {
            base += baseWeights[i];
            contributions[i] += baseWeights[i];
        }

        double augmentation = 0.0;
        int segmentLength = segmentLength(window);
        int segments = segmentCount(y.length, segmentLength);
        for (int segment = 0; segment < segments; segment++) {
            IndexRange ySegment = segmentRange(segment, y.length, segmentLength);
            IndexRange rectangle = adjacentSegmentRange(segment, x.length, segmentLength);
            Range xEnvelope = envelope(x, rectangle);
            double baseUpperBound = maxBaseUpperBound(x, y, segment, segmentLength, c);

            for (int j = Math.max(1, ySegment.start()); j < Math.min(y.length - 1, ySegment.end()); j++) {
                double delta = deltaOutside(y[j], xEnvelope, c);
                double contribution = Math.min(Math.max(delta - baseUpperBound, 0.0), c);
                augmentation += contribution;
                contributions[j] += contribution;
            }
        }

        // The rectangular envelopes are deliberately wider than the paper's
        // point envelopes. Augmentation subtracts a conservative upper bound on
        // the paper base, because subtracting the weaker rectangular base can
        // otherwise make this approximation too large.
        addBoundary(contributions, x, y, c);
        return new DirectedContribution(contributions, boundary(x, y, c) + base + augmentation);
    }

    private static double[] baseWeights(double[] x, double[] y, double c, int window) {
        double[] weights = new double[x.length];

        for (int i = 1; i < x.length - 1; i++) {
            Range yEnvelope = envelope(y, i, window);

            if (x[i] > yEnvelope.max()) {
                weights[i] = delta(x[i], yEnvelope.max(), c);
            } else if (x[i] < yEnvelope.min()) {
                weights[i] = delta(x[i], yEnvelope.min(), c);
            }
        }

        return weights;
    }

    private static double[] rectangleBaseWeights(double[] x, double[] y, double c, int window) {
        double[] weights = new double[x.length];
        int segmentLength = segmentLength(window);
        int segments = segmentCount(x.length, segmentLength);

        for (int segment = 0; segment < segments; segment++) {
            IndexRange xSegment = segmentRange(segment, x.length, segmentLength);
            IndexRange rectangle = adjacentSegmentRange(segment, y.length, segmentLength);
            Range yEnvelope = envelope(y, rectangle);

            for (int i = Math.max(1, xSegment.start()); i < Math.min(x.length - 1, xSegment.end()); i++) {
                weights[i] = deltaOutside(x[i], yEnvelope, c);
            }
        }

        return weights;
    }

    private static double maxBaseUpperBound(
            double[] source,
            double[] target,
            int centerSegment,
            int segmentLength,
            double c) {

        double max = 0.0;
        int startSegment = Math.max(0, centerSegment - 1);
        int endSegmentExclusive = Math.min(segmentCount(source.length, segmentLength), centerSegment + 2);

        for (int segment = startSegment; segment < endSegmentExclusive; segment++) {
            max = Math.max(max, baseUpperBound(source, target, segment, segmentLength, c));
        }

        return max;
    }

    private static double baseUpperBound(
            double[] source,
            double[] target,
            int segment,
            int segmentLength,
            double c) {

        Range sourceRange = envelope(source, segmentRange(segment, source.length, segmentLength));
        Range targetEnvelope = envelope(target, adjacentSegmentRange(segment, target.length, segmentLength));

        double maxDistance = 0.0;
        maxDistance = Math.max(maxDistance, Math.abs(sourceRange.min() - targetEnvelope.min()));
        maxDistance = Math.max(maxDistance, Math.abs(sourceRange.min() - targetEnvelope.max()));
        maxDistance = Math.max(maxDistance, Math.abs(sourceRange.max() - targetEnvelope.min()));
        maxDistance = Math.max(maxDistance, Math.abs(sourceRange.max() - targetEnvelope.max()));
        return Math.min(maxDistance, c);
    }

    private static double boundary(double[] x, double[] y, double c) {
        if (x.length == 1 && y.length == 1) {
            return Math.abs(x[0] - y[0]);
        }

        double first = Math.abs(x[0] - y[0]);
        double last = Math.min(Math.abs(x[x.length - 1] - y[y.length - 1]), c);
        return first + last;
    }

    private static void addBoundary(double[] contributions, double[] x, double[] y, double c) {
        if (x.length == 1 && y.length == 1) {
            contributions[0] += Math.abs(x[0] - y[0]);
            return;
        }

        contributions[0] += Math.abs(x[0] - y[0]);
        contributions[x.length - 1] += Math.min(Math.abs(x[x.length - 1] - y[y.length - 1]), c);
    }

    private static double delta(double value, double boundary, double c) {
        return Math.min(Math.abs(value - boundary), c);
    }

    private static double deltaOutside(double value, Range envelope, double c) {
        if (value > envelope.max()) {
            return delta(value, envelope.max(), c);
        }
        if (value < envelope.min()) {
            return delta(value, envelope.min(), c);
        }
        return 0.0;
    }

    private static Range envelope(double[] values, int center, int window) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        for (int k = center - window; k <= center + window; k++) {
            double value = values[clamp(k, 0, values.length - 1)];
            min = Math.min(min, value);
            max = Math.max(max, value);
        }

        return new Range(min, max);
    }

    private static Range envelope(double[] values, IndexRange range) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        for (int i = range.start(); i < range.end(); i++) {
            double value = values[i];
            min = Math.min(min, value);
            max = Math.max(max, value);
        }

        return new Range(min, max);
    }

    private static double windowMax(double[] values, int center, int window) {
        double max = 0.0;

        for (int k = center - window; k <= center + window; k++) {
            max = Math.max(max, values[clamp(k, 0, values.length - 1)]);
        }

        return max;
    }

    private static int segmentLength(int window) {
        return Math.max(1, window);
    }

    private static int segmentCount(int n, int segmentLength) {
        return (n + segmentLength - 1) / segmentLength;
    }

    private static IndexRange segmentRange(int segment, int n, int segmentLength) {
        int start = segment * segmentLength;
        int end = Math.min(n, start + segmentLength);
        return new IndexRange(start, end);
    }

    private static IndexRange adjacentSegmentRange(int segment, int n, int segmentLength) {
        int startSegment = Math.max(0, segment - 1);
        int endSegmentExclusive = Math.min(segmentCount(n, segmentLength), segment + 2);
        return new IndexRange(
                startSegment * segmentLength,
                Math.min(n, endSegmentExclusive * segmentLength));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private static void validate(double[] x, double[] y, double c, int window) {
        if (x == null || y == null) {
            throw new IllegalArgumentException("series must not be null");
        }
        if (x.length == 0 || y.length == 0) {
            throw new IllegalArgumentException("MSM BGLB requires non-empty series");
        }
        if (x.length != y.length) {
            throw new IllegalArgumentException("MSM BGLB currently follows the paper's equal-length setting");
        }
        if (c < 0.0) {
            throw new IllegalArgumentException("MSM c must be non-negative");
        }
        if (window < 0) {
            throw new IllegalArgumentException("window must be non-negative");
        }
    }

    private record Range(double min, double max) {}

    private record IndexRange(int start, int end) {}

    private record DirectedContribution(double[] values, double sum) {}
}
