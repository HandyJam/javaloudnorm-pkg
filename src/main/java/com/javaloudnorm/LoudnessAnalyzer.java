package com.javaloudnorm;

/**
 * Computes integrated loudness in LUFS per ITU BS.1770-4 / EBU R-128.
 */
public class LoudnessAnalyzer {

    private static final double[] DEFAULT_G = {1.0, 1.0, 1.0, 1.41, 1.41};

    private LoudnessAnalyzer() {
    }

    /**
     * Calculates the integrated loudness of the given audio signal.
     *
     * @param samples    deinterleaved audio samples [channel][sample], values in [-1.0, 1.0]
     * @param sampleRate sample rate in Hz
     * @return loudness in LUFS
     */
    public static double calculateLoudness(double[][] samples, int sampleRate) {
        return calculateLoudness(samples, sampleRate, DEFAULT_G);
    }

    /**
     * Calculates the integrated loudness with custom channel weighting.
     *
     * @param samples    deinterleaved audio samples [channel][sample], values in [-1.0, 1.0]
     * @param sampleRate sample rate in Hz
     * @param G          channel weighting coefficients (ITU default: [1.0, 1.0, 1.0, 1.41, 1.41])
     * @return loudness in LUFS
     */
    public static double calculateLoudness(double[][] samples, int sampleRate, double[] G) {
        int numChannels = samples.length;
        int numSamples = samples[0].length;

        // Apply K-weighting filter to each channel (copy first to avoid mutating input)
        double[][] filtered = new double[numChannels][];
        for (int i = 0; i < numChannels; i++) {
            filtered[i] = new double[numSamples];
            System.arraycopy(samples[i], 0, filtered[i], 0, numSamples);
            KFilter.apply(filtered[i], sampleRate);
        }

        // Gating parameters
        double T_g = 0.400;       // 400 ms gating block
        double Gamma_a = -70.0;   // absolute threshold: -70 LKFS
        double overlap = 0.75;
        double step = 1.0 - overlap;

        double T = (double) numSamples / sampleRate; // measurement interval in seconds
        int numBlocks = (int) ((T - T_g) / (T_g * step));

        // Channel weighting — use only as many as we have channels
        int nCh = Math.min(numChannels, G.length);
        double[] Gc = new double[nCh];
        System.arraycopy(G, 0, Gc, 0, nCh);

        // Compute mean square energy per channel per block: z[channel][block]
        double[][] z = new double[nCh][numBlocks];
        for (int i = 0; i < nCh; i++) {
            for (int j = 0; j < numBlocks; j++) {
                int lbound = (int) Math.round(sampleRate * T_g * j * step);
                int hbound = (int) Math.round(sampleRate * T_g * (j * step + 1));
                if (hbound > numSamples) hbound = numSamples;

                double sum = 0.0;
                for (int k = lbound; k < hbound; k++) {
                    sum += filtered[i][k] * filtered[i][k];
                }
                z[i][j] = sum / (T_g * sampleRate);
            }
        }

        // Compute loudness per block
        double[] l = new double[numBlocks];
        for (int j = 0; j < numBlocks; j++) {
            double sum = 0.0;
            for (int i = 0; i < nCh; i++) {
                sum += Gc[i] * z[i][j];
            }
            l[j] = -0.691 + 10.0 * Math.log10(sum);
        }

        // Absolute gating: discard blocks below -70 LKFS
        double[] zAvg = averageGated(z, l, Gamma_a, nCh, numBlocks);

        // Relative threshold
        double sumGated = 0.0;
        for (int i = 0; i < nCh; i++) {
            sumGated += Gc[i] * zAvg[i];
        }
        double Gamma_r = -0.691 + 10.0 * Math.log10(sumGated) - 10.0;

        // Relative gating: discard blocks below relative threshold
        zAvg = averageGated(z, l, Gamma_r, nCh, numBlocks);

        // Final loudness
        double sumFinal = 0.0;
        for (int i = 0; i < nCh; i++) {
            sumFinal += Gc[i] * zAvg[i];
        }
        return -0.691 + 10.0 * Math.log10(sumFinal);
    }

    /**
     * Computes the average of z values for blocks whose loudness exceeds the threshold.
     */
    private static double[] averageGated(double[][] z, double[] l, double threshold, int nCh, int numBlocks) {
        double[] zAvg = new double[nCh];
        int count = 0;

        for (int j = 0; j < numBlocks; j++) {
            if (l[j] > threshold) {
                for (int i = 0; i < nCh; i++) {
                    zAvg[i] += z[i][j];
                }
                count++;
            }
        }

        if (count > 0) {
            for (int i = 0; i < nCh; i++) {
                zAvg[i] /= count;
            }
        }

        return zAvg;
    }
}

