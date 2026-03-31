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
        double[] output = new double[signal.length];

        for (int n = 0; n < signal.length; n++) {
            double value = 0.0;

            for (int i = 0; i < b.length; i++) {
                if (n - i >= 0) {
                    value += b[i] * signal[n - i];
                }
            }

            for (int i = 1; i < a.length; i++) {
                if (n - i >= 0) {
                    value -= a[i] * output[n - i];
                }
            }

            output[n] = value / a[0];
        }

        return output;
    }
}
