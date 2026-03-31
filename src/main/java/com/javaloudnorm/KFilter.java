package com.javaloudnorm;

public final class KFilter {
    private KFilter() {}

    public static double[] kFilter(double[] signal, double fs) {
        double f0 = 1681.9744509555319;
        double g = 3.99984385397;
        double q = 0.7071752369554193;
        double k = Math.tan(Math.PI * f0 / fs);
        double vh = Math.pow(10.0, g / 20.0);
        double vb = Math.pow(vh, 0.499666774155);
        double a0Prime = 1.0 + k / q + k * k;
        double b0 = (vh + vb * k / q + k * k) / a0Prime;
        double b1 = 2.0 * (k * k - vh) / a0Prime;
        double b2 = (vh - vb * k / q + k * k) / a0Prime;
        double a0 = 1.0;
        double a1 = 2.0 * (k * k - 1.0) / a0Prime;
        double a2 = (1.0 - k / q + k * k) / a0Prime;
        double[] signal1 = lfilter(new double[]{b0, b1, b2}, new double[]{a0, a1, a2}, signal);

        f0 = 38.13547087613982;
        q = 0.5003270373253953;
        k = Math.tan(Math.PI * f0 / fs);
        a0 = 1.0;
        a1 = 2.0 * (k * k - 1.0) / (1.0 + k / q + k * k);
        a2 = (1.0 - k / q + k * k) / (1.0 + k / q + k * k);
        b0 = 1.0;
        b1 = -2.0;
        b2 = 1.0;
        return lfilter(new double[]{b0, b1, b2}, new double[]{a0, a1, a2}, signal1);
    }

    public static double[] lfilter(double[] b, double[] a, double[] signal) {
        int n = signal.length;
        double[] output = new double[n];
        double a0 = a[0];
        if (n > 0) {
            output[0] = b[0] * signal[0] / a0;
        }
        if (n > 1) {
            output[1] = (b[0] * signal[1] + b[1] * signal[0] - a[1] * output[0]) / a0;
        }
        for (int i = 2; i < n; i++) {
            double feedforward = b[0] * signal[i] + b[1] * signal[i - 1] + b[2] * signal[i - 2];
            double feedback = a[1] * output[i - 1] + a[2] * output[i - 2];
            output[i] = (feedforward - feedback) / a0;
        }
        return output;
    }
}
