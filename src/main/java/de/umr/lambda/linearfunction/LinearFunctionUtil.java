package de.umr.lambda.linearfunction;

import de.umr.lambda.msm.MSM;

import java.util.ArrayList;
import java.util.List;

public class LinearFunctionUtil {



    public static double lowerBound(
            double a, double b,
            int n,
            double minY, double maxY,
            double c) {

        if (n <= 0) return 0.0;

        double upper = sumClippedPositive(a, b - maxY, n, c);
        double lower = sumClippedPositive(-a, minY - b, n, c);
        return upper + lower;
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


        int firstPos = (int)Math.floor(-b / a) + 1;
        int firstCap = (int)Math.ceil((c - b) / a);

        int rampL = clamp(firstPos, 0, n);
        int rampR = clamp(firstCap, 0, n) - 1;

        double total = sumLinear(a, b, rampL, rampR);

        int capL = clamp(firstCap, 0, n);
        total += (n - capL) * c;

        return Math.max(total, 0.0);
    }




    private static double sumLinear(double a, double b, int L, int R) {
        if (L > R) return 0.0;

        double len = (double)R - L + 1.0;
        double sumI = len * ((double)L + R) * 0.5;

        return a * sumI + b * len;
    }

    private static int clamp(int x, int lo, int hi) {
        return x < lo ? lo : (Math.min(x, hi));
    }



    //-------------------------------------------------------------
    //WITH MAX VAL
    //-------------------------------------------------------------

    public static double[] lowerBoundWithMaxValue(
            double a, double b,
            int n,
            double minY, double maxY,
            double c,
            double subtract) {

        if (n <= 0) return new double[]{0.0, 0.0};

        double[] upper = sumClippedPositiveWithMaxValue(a, b - maxY, n, c, subtract);
        double[] lower = sumClippedPositiveWithMaxValue(-a, minY - b, n, c, subtract);

        return new double[]{
                upper[0] + lower[0],
                Math.max(upper[1], lower[1])
        };
    }

    private static double[] sumClippedPositiveWithMaxValue(
            double a, double b,
            int n,
            double c,
            double subtract) {

        if (n <= 0) return new double[]{0.0, 0.0};

        if (subtract >= c) return new double[]{0.0, 0.0};

        if (a == 0.0) {
            double v = Math.min(Math.max(0.0, b), c);
            double term = Math.max(0.0, v - subtract);
            return new double[]{n * term, term};
        }

        if (a < 0.0) {
            b = b + a * (n - 1);
            a = -a;
        }

        int firstActive = (int)Math.floor((subtract - b) / a) + 1;
        int firstCap = (int)Math.ceil((c - b) / a);

        int rampL = clamp(firstActive, 0, n);
        int rampR = clamp(firstCap, 0, n) - 1;


        double total = sumLinearSubtract(a, b, rampL, rampR, subtract);

        int capL = clamp(firstCap, 0, n);
        double cappedTerm = c - subtract;
        total += (n - capL) * cappedTerm;

        double maxRaw = b + a * (n - 1);
        double maxContribution =
                Math.max(0.0, Math.min(Math.max(0.0, maxRaw), c) - subtract);

        return new double[]{Math.max(0.0, total), maxContribution};
    }

    private static double sumLinearSubtract(
            double a, double b,
            int L, int R,
            double subtract) {

        if (L > R) return 0.0;

        double len = (double) R - L + 1.0;
        double sumI = len * ((double) L + R) * 0.5;

        return a * sumI + (b - subtract) * len;
    }

    public static void lowerBound(double[] tsX, double[] tsY, double c, double w, LineFitter.FitType fitType, double cFit, int iterations){
        double bdy = Math.abs(tsX[0]-tsY[0]) + Math.min(Math.abs(tsX[tsX.length-1]-tsY[tsY.length-1]),c);
        ArrayList<LineFitter.Lin> tsXL = new ArrayList<>();
        ArrayList<LineFitter.Lin> tsYL = new ArrayList<>();
        for(int i = 0; i < tsX.length; i += (int) w){
            tsXL.add(LineFitter.fitLine(tsX,i, (int) Math.min(i +w,tsX.length),fitType,cFit,iterations));
        }
        for(int i = 0; i < tsY.length; i += (int) w){
            tsYL.add(LineFitter.fitLine(tsY,i, (int) Math.min(i+w,tsY.length),fitType,cFit,iterations));
        }
        double[] approxX = PiecewiseLinear.sample(tsXL);
        double[] approxY = PiecewiseLinear.sample(tsYL);

        double msmX = MSM.distance(tsX, approxX, c, (int) w);
        double msmY = MSM.distance(tsY, approxY, c, (int) w);
        double msmApprox = MSM.distance(approxX,approxY,c, (int) w);
        double total = -1*(msmX + msmY);
        double linApproxTotal = 0;
        int pointsMapped = 0;
        for(int i = 0; i < tsXL.size(); i++){
            double minY;
            double maxY;
            if(i == 0){
                minY = Math.min(tsYL.getFirst().minValue(), tsYL.get(1).minValue());
                maxY = Math.max(tsYL.getFirst().maxValue(), tsYL.get(1).maxValue());
            } else if(i < tsXL.size()-1) {
                minY = Math.min(tsYL.get(i-1).minValue(), Math.min(tsYL.get(i).minValue(),tsYL.get(i+1).minValue()));
                maxY = Math.max(tsYL.get(i-1).maxValue(), Math.max(tsYL.get(i).maxValue(),tsYL.get(i+1).maxValue()));
            } else {
                minY = Math.min(tsYL.getLast().minValue(), tsYL.get(tsYL.size()-2).minValue());
                maxY = Math.max(tsYL.getLast().maxValue(), tsYL.get(tsYL.size()-2).maxValue());
            }
            LineFitter.Lin line = tsXL.get(i);
            linApproxTotal += lowerBound(line.a(),line.b(),line.end(),minY,maxY,c);
            pointsMapped += line.end();
        }
        total += linApproxTotal;
        double msmNormal = MSM.distance(tsX,tsY,c,(int) w);
        if(total/msmNormal>1)
            System.out.println("FAILURE");
        //System.out.println(Math.max(total,0));
        System.out.println(Math.max(0,total)/MSM.distance(tsX,tsY,c,(int) w));
    }

    public static void lowerBoundWithMaxValuesTwoSided(double[] tsX, double[] tsY, double c, double w, LineFitter.FitType fitType, double cFit, int iterations){
        ArrayList<LineFitter.Lin> tsXL = new ArrayList<>();
        ArrayList<LineFitter.Lin> tsYL = new ArrayList<>();
        for(int i = 0; i < tsX.length; i += (int) w){
            tsXL.add(LineFitter.fitLine(tsX,i, (int) Math.min(i + w,tsX.length),fitType,cFit,iterations));
        }
        for(int i = 0; i < tsY.length; i += (int) w){
            tsYL.add(LineFitter.fitLine(tsY,i, (int) Math.min(i+ w,tsY.length),fitType,cFit,iterations));
        }

        double[] approxX = PiecewiseLinear.sample(tsXL);
        double[] approxY = PiecewiseLinear.sample(tsYL);
        double msmX = MSM.distance(tsX, approxX, c, (int) w);
        double msmY = MSM.distance(tsY, approxY, c, (int) w);

        double lowerBoundX = processLines(tsXL,tsYL,c);
        double lowerBoundY = processLines(tsYL,tsXL,c);
        double total = -1*(msmX+msmY);
        double msmApprox = MSM.distance(approxX,approxY,c, (int) w);
        total = Math.max(lowerBoundX+total,lowerBoundY+total);
        double msmNormal = MSM.distance(tsX,tsY,c,(int) w);
        if(total/msmNormal>1)
            System.out.println("FAILURE");
        //System.out.println(Math.max(total,0));
        System.out.println(Math.max(0,total)/MSM.distance(tsX,tsY,c,(int) w));
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
            Range r = envelope(target, Math.max(0, i - 1), Math.min(n - 1, i + 1));

            LineFitter.Lin line = source.get(i);
            double[] sumAndMax = lowerBoundWithMaxValue(
                    line.a(), line.b(), line.end(),
                    r.min(), r.max(),
                    c, 0.0);

            sum += sumAndMax[0];
            sourceMax[i] = sumAndMax[1];
        }

        for (int j = 0; j < n; j++) {
            int lo = Math.max(0, j - 1);
            int hi = Math.min(n - 1, j + 1);

            Range r = envelope(source, lo, hi);
            double subtract = max(sourceMax, lo, hi);

            LineFitter.Lin line = target.get(j);
            double[] sumAndMax = lowerBoundWithMaxValue(
                    line.a(), line.b(), line.end(),
                    r.min(), r.max(),
                    c, subtract);

            sum += sumAndMax[0];
        }

        return sum;
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
        double m = 0.0;
        for (int i = lo; i <= hi; i++) {
            m = Math.max(m, values[i]);
        }
        return m;
    }


}