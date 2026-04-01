package com.javaloudnorm;

/**
 * K-weighting filter as specified in ITU BS.1770-4 / EBU R-128.
 * Applies two cascaded biquad IIR filters (pre-filter 1: shelving, pre-filter 2: high-pass).
 */
public class KFilter {

    private KFilter() {
    }

    /**
     * Biquad filter coefficients (b0, b1, b2, a0, a1, a2).
     */
    static class Coefficients {
        final double b0, b1, b2;
        final double a0, a1, a2;

        Coefficients(double b0, double b1, double b2, double a0, double a1, double a2) {
            this.b0 = b0;
            this.b1 = b1;
            this.b2 = b2;
            this.a0 = a0;
            this.a1 = a1;
            this.a2 = a2;
        }
    }

    /**
     * Applies K-weighting filter to the signal in-place.
     *
     * @param signal     single-channel audio samples
     * @param sampleRate sample rate in Hz
     * @return filtered signal (same array, modified in-place)
     */
    public static double[] apply(double[] signal, int sampleRate) {
        // Pre-filter 1: shelving filter
        Coefficients c1 = preFilter1Coefficients(sampleRate);
        lfilter(signal, c1);

        // Pre-filter 2: high-pass filter
        Coefficients c2 = preFilter2Coefficients(sampleRate);
        lfilter(signal, c2);

        return signal;
    }

    /**
     * Pre-filter 1 coefficients — high-shelf boost at ~1682 Hz.
     */
    static Coefficients preFilter1Coefficients(int fs) {
        double f0 = 1681.9744509555319;
        double G = 3.99984385397;
        double Q = 0.7071752369554193;

        double K = Math.tan(Math.PI * f0 / fs);
        double Vh = Math.pow(10.0, G / 20.0);
        double Vb = Math.pow(Vh, 0.499666774155);
        double a0_ = 1.0 + K / Q + K * K;

        double b0 = (Vh + Vb * K / Q + K * K) / a0_;
        double b1 = 2.0 * (K * K - Vh) / a0_;
        double b2 = (Vh - Vb * K / Q + K * K) / a0_;
        double a0 = 1.0;
        double a1 = 2.0 * (K * K - 1.0) / a0_;
        double a2 = (1.0 - K / Q + K * K) / a0_;

        return new Coefficients(b0, b1, b2, a0, a1, a2);
    }

    /**
     * Pre-filter 2 coefficients — high-pass at ~38 Hz.
     */
    static Coefficients preFilter2Coefficients(int fs) {
        double f0 = 38.13547087613982;
        double Q = 0.5003270373253953;

        double K = Math.tan(Math.PI * f0 / fs);
        double denom = 1.0 + K / Q + K * K;

        double b0 = 1.0;
        double b1 = -2.0;
        double b2 = 1.0;
        double a0 = 1.0;
        double a1 = 2.0 * (K * K - 1.0) / denom;
        double a2 = (1.0 - K / Q + K * K) / denom;

        return new Coefficients(b0, b1, b2, a0, a1, a2);
    }

    /**
     * Direct form II transposed IIR filter, equivalent to scipy.signal.lfilter.
     * Filters the signal in-place.
     */
    private static void lfilter(double[] signal, Coefficients c) {
        // Normalize by a0 (should be 1.0, but just in case)
        double b0 = c.b0 / c.a0;
        double b1 = c.b1 / c.a0;
        double b2 = c.b2 / c.a0;
        double a1 = c.a1 / c.a0;
        double a2 = c.a2 / c.a0;

        // State variables for direct form II transposed
        double z1 = 0.0;
        double z2 = 0.0;

        for (int n = 0; n < signal.length; n++) {
            double x = signal[n];
            double y = b0 * x + z1;
            z1 = b1 * x - a1 * y + z2;
            z2 = b2 * x - a2 * y;
            signal[n] = y;
        }
    }
}

