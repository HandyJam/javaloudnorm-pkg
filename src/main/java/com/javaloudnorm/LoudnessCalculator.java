package com.javaloudnorm;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public final class LoudnessCalculator {
    private static final double[] DEFAULT_G = {1.0, 1.0, 1.0, 1.41, 1.41};

    private LoudnessCalculator() {
    }

    public static double calculateLoudness(double[][] signal, double fs) {
        return calculateLoudness(signal, fs, DEFAULT_G);
    }

    public static double calculateLoudness(double[][] channelsSamples, double fs, double[] g) {
        // channelsSamples is double[channels][frames]
        double[][] channelsFiltered = new double[channelsSamples.length][];
        if (channelsSamples.length == 1) {
            channelsFiltered[0] = KFilter.kFilter(channelsSamples[0], fs);
        } else {
            IntStream.range(0, channelsSamples.length).parallel().forEach(i -> {
                channelsFiltered[i] = KFilter.kFilter(channelsSamples[i], fs);
            });
        }

        double tg = 0.400;
        double gammaA = -70.0;
        double overlap = 0.75;
        double step = 1 - overlap;

        double totalTime = channelsFiltered[0].length / fs;
        int[] jRange = createWindowRange(totalTime, tg, step);
        double[][] z = new double[channelsFiltered.length][];
        IntStream.range(0, channelsFiltered.length).parallel().forEach(i -> {
            double[] chan = channelsFiltered[i];
            double[] zChan = new double[jRange.length];
            for (int jIndex = 0; jIndex < jRange.length; jIndex++) {
                int j = jRange[jIndex];
                int lowerBound = (int) Math.round(fs * tg * j * step);
                int upperBound = (int) Math.round(fs * tg * (j * step + 1.0));
                double sum = 0.0;
                int safeUpper = Math.min(upperBound, chan.length);
                int len = safeUpper - lowerBound;
                if (len > 0) {
                    int m = len / 4;
                    double sum0 = 0, sum1 = 0, sum2 = 0, sum3 = 0;
                    int base = lowerBound;
                    for (int k = 0; k < m; k++) {
                        double v0 = chan[base++];
                        double v1 = chan[base++];
                        double v2 = chan[base++];
                        double v3 = chan[base++];
                        sum0 += v0 * v0;
                        sum1 += v1 * v1;
                        sum2 += v2 * v2;
                        sum3 += v3 * v3;
                    }
                    sum = sum0 + sum1 + sum2 + sum3;
                    for (int k = m * 4; k < len; k++) {
                        double value = chan[lowerBound + k];
                        sum += value * value;
                    }
                }
                zChan[jIndex] = sum / (tg * fs);
            }
            z[i] = zChan;
        });

        double[] currentG = new double[Math.min(g.length, channelsFiltered.length)];
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
