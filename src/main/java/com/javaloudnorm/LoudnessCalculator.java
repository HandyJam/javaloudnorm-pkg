package com.javaloudnorm;

import java.util.ArrayList;
import java.util.List;

public final class LoudnessCalculator {
    private static final double[] DEFAULT_G = {1.0, 1.0, 1.0, 1.41, 1.41};

    private LoudnessCalculator() {
    }

    public static double calculateLoudness(double[][] signal, double fs) {
        return calculateLoudness(signal, fs, DEFAULT_G);
    }

    public static double calculateLoudness(double[][] signal, double fs, double[] g) {
        double[][] signalFiltered = copyMatrix(signal);

        for (int i = 0; i < signalFiltered[0].length; i++) {
            double[] channel = extractColumn(signalFiltered, i);
            double[] filteredChannel = KFilter.kFilter(channel, fs);
            writeColumn(signalFiltered, i, filteredChannel);
        }

        double tg = 0.400;
        double gammaA = -70.0;
        double overlap = 0.75;
        double step = 1.0 - overlap;

        double totalTime = signalFiltered.length / fs;
        int[] jRange = createWindowRange(totalTime, tg, step);
        double[][] z = new double[signalFiltered[0].length][jRange.length];

        for (int i = 0; i < signalFiltered[0].length; i++) {
            for (int jIndex = 0; jIndex < jRange.length; jIndex++) {
                int j = jRange[jIndex];
                int lowerBound = (int) Math.round(fs * tg * j * step);
                int upperBound = (int) Math.round(fs * tg * (j * step + 1.0));
                z[i][jIndex] = (1.0 / (tg * fs)) * sumSquares(signalFiltered, lowerBound, upperBound, i);
            }
        }

        double[] currentG = new double[Math.min(g.length, signalFiltered[0].length)];
        System.arraycopy(g, 0, currentG, 0, currentG.length);
        int nChannels = currentG.length;

        double[] l = new double[jRange.length];
        for (int jIndex = 0; jIndex < jRange.length; jIndex++) {
            double sum = 0.0;
            for (int i = 0; i < nChannels; i++) {
                sum += currentG[i] * z[i][jIndex];
            }
            l[jIndex] = -0.691 + 10.0 * Math.log10(sum);
        }

        List<Integer> indicesGated = new ArrayList<>();
        for (int idx = 0; idx < l.length; idx++) {
            if (l[idx] > gammaA) {
                indicesGated.add(idx);
            }
        }

        double[] zAvg = new double[nChannels];
        for (int i = 0; i < nChannels; i++) {
            zAvg[i] = mean(z[i], indicesGated);
        }

        double gammaRSum = 0.0;
        for (int i = 0; i < nChannels; i++) {
            gammaRSum += currentG[i] * zAvg[i];
        }
        double gammaR = -0.691 + 10.0 * Math.log10(gammaRSum) - 10.0;

        indicesGated = new ArrayList<>();
        for (int idx = 0; idx < l.length; idx++) {
            if (l[idx] > gammaR) {
                indicesGated.add(idx);
            }
        }

        for (int i = 0; i < nChannels; i++) {
            zAvg[i] = mean(z[i], indicesGated);
        }

        double loudnessSum = 0.0;
        for (int i = 0; i < nChannels; i++) {
            loudnessSum += currentG[i] * zAvg[i];
        }

        return -0.691 + 10.0 * Math.log10(loudnessSum);
    }

    private static double[][] copyMatrix(double[][] source) {
        double[][] copy = new double[source.length][source[0].length];
        for (int i = 0; i < source.length; i++) {
            System.arraycopy(source[i], 0, copy[i], 0, source[i].length);
        }
        return copy;
    }

    private static double[] extractColumn(double[][] matrix, int column) {
        double[] values = new double[matrix.length];
        for (int i = 0; i < matrix.length; i++) {
            values[i] = matrix[i][column];
        }
        return values;
    }

    private static void writeColumn(double[][] matrix, int column, double[] values) {
        for (int i = 0; i < matrix.length; i++) {
            matrix[i][column] = values[i];
        }
    }

    private static int[] createWindowRange(double totalTime, double tg, double step) {
        int length = (int) ((totalTime - tg) / (tg * step));
        if (length <= 0) {
            return new int[0];
        }
        int[] range = new int[length];
        for (int i = 0; i < length; i++) {
            range[i] = i;
        }
        return range;
    }

    private static double sumSquares(double[][] signal, int lowerBound, int upperBound, int channel) {
        double sum = 0.0;
        int safeUpper = Math.min(upperBound, signal.length);
        for (int i = lowerBound; i < safeUpper; i++) {
            double value = signal[i][channel];
            sum += value * value;
        }
        return sum;
    }

    private static double mean(double[] values, List<Integer> indices) {
        if (indices.isEmpty()) {
            return Double.NaN;
        }

        double sum = 0.0;
        for (int index : indices) {
            sum += values[index];
        }
        return sum / indices.size();
    }
}
