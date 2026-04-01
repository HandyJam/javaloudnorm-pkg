package com.javaloudnorm;

/**
 * Holds decoded audio samples and sample rate.
 * samples[channel][sampleIndex] contains normalized float values in [-1.0, 1.0].
 */
public class AudioData {

    private final double[][] samples;
    private final int sampleRate;

    public AudioData(double[][] samples, int sampleRate) {
        this.samples = samples;
        this.sampleRate = sampleRate;
    }

    public double[][] getSamples() {
        return samples;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public int getNumChannels() {
        return samples.length;
    }

    public int getNumSamples() {
        return samples.length > 0 ? samples[0].length : 0;
    }
}

