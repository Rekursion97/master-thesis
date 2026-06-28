package de.umr.lambda.linearfunction;

import java.util.ArrayList;
import java.util.List;

public final class PiecewiseLinear {
    private PiecewiseLinear() {}

    public static double[] sample(ArrayList<LineFitter.Lin> lines) {
        return sample((List<LineFitter.Lin>) lines);
    }

    public static double[] sample(List<LineFitter.Lin> lines) {
        int n = 0;
        for (LineFitter.Lin l : lines) n += l.end();

        double[] out = new double[n];

        int idx = 0;
        for (LineFitter.Lin l : lines) {
            for (int i = l.start(); i < l.end(); i++) {
                out[idx++] = l.a() * i + l.b();
            }
        }

        return out;
    }
}
