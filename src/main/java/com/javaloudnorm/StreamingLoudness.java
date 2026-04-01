package com.javaloudnorm;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class StreamingLoudness {

    // Default channel weighting (L, R, C, Ls, Rs)
    private static final double[] G = {1.0, 1.0, 1.0, 1.41, 1.41};

    public static double analyze(InputStream inputStream) throws Exception {
        try (AudioInputStream baseStream = AudioSystem.getAudioInputStream(new BufferedInputStream(inputStream))) {

            AudioFormat baseFormat = baseStream.getFormat();
            int sampleSize = baseFormat.getSampleSizeInBits();
            if (sampleSize == AudioSystem.NOT_SPECIFIED) sampleSize = 16;

            AudioFormat decodedFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.getSampleRate(),
                    sampleSize,
                    baseFormat.getChannels(),
                    baseFormat.getChannels() * (sampleSize / 8),
                    baseFormat.getSampleRate(),
                    false
            );

            try (AudioInputStream pcmStream = AudioSystem.getAudioInputStream(decodedFormat, baseStream)) {
                double fs = decodedFormat.getSampleRate();
                int channels = decodedFormat.getChannels();
                int bytesPerSample = sampleSize / 8;
                int frameSize = channels * bytesPerSample;

                // 1. Initialize stateful K-Filters for each audio channel
                Biquad[] filter1 = new Biquad[channels];
                Biquad[] filter2 = new Biquad[channels];
                for (int i = 0; i < channels; i++) {
                    filter1[i] = createPreFilter1(fs);
                    filter2[i] = createPreFilter2(fs);
                }

                // 2. Prepare 100ms block trackers
                int samplesPer100ms = (int) Math.round(fs * 0.1);
                List<double[]> blockEnergies = new ArrayList<>();
                double[] currentBlockEnergy = new double[channels];
                int sampleCountInBlock = 0;

                // Tiny 4KB read buffer. This is what keeps RAM usage near zero!
                byte[] buffer = new byte[4096 - (4096 % frameSize)];
                int bytesRead;

                // 3. Stream and process on the fly
                while ((bytesRead = pcmStream.read(buffer)) != -1) {
                    int framesRead = bytesRead / frameSize;
                    int byteIndex = 0;

                    for (int f = 0; f < framesRead; f++) {
                        for (int c = 0; c < channels; c++) {
                            double sample = parseSample(buffer, byteIndex, bytesPerSample);
                            byteIndex += bytesPerSample;

                            // Apply K-Filters and square
                            sample = filter1[c].process(sample);
                            sample = filter2[c].process(sample);
                            currentBlockEnergy[c] += (sample * sample);
                        }

                        sampleCountInBlock++;

                        // When we hit 100ms, save the sum and start a new block
                        if (sampleCountInBlock == samplesPer100ms) {
                            blockEnergies.add(currentBlockEnergy.clone());
                            currentBlockEnergy = new double[channels];
                            sampleCountInBlock = 0;
                        }
                    }
                }

                if (sampleCountInBlock > 0) {
                    blockEnergies.add(currentBlockEnergy.clone());
                }

                // 4. Calculate final LUFS using the 100ms block energies
                return calculateGatedLoudness(blockEnergies, channels, fs);
            }
        }
    }

    private static double calculateGatedLoudness(List<double[]> blockEnergies, int channels, double fs) {
        // A 400ms window with 75% overlap is exactly 4 consecutive 100ms blocks
        int numWindows = blockEnergies.size() - 3;
        if (numWindows <= 0) return -100.0;

        double[] l = new double[numWindows];
        double[][] z = new double[channels][numWindows];

        for (int j = 0; j < numWindows; j++) {
            double z_sum = 0;
            for (int i = 0; i < channels; i++) {
                // Sum the 4 consecutive 100ms blocks for this channel
                double sumSquare = blockEnergies.get(j)[i] +
                        blockEnergies.get(j + 1)[i] +
                        blockEnergies.get(j + 2)[i] +
                        blockEnergies.get(j + 3)[i];
                z[i][j] = sumSquare / (0.4 * fs);

                if (i < G.length) z_sum += G[i] * z[i][j];
            }
            l[j] = -0.691 + 10.0 * Math.log10(z_sum);
        }

        // Absolute threshold gate (-70 LUFS)
        double Gamma_a = -70.0;
        double[] z_avg_abs = new double[channels];
        int count_abs = 0;
        for (int j = 0; j < numWindows; j++) {
            if (l[j] > Gamma_a) {
                count_abs++;
                for (int i = 0; i < channels; i++) z_avg_abs[i] += z[i][j];
            }
        }

        if (count_abs == 0) return -70.0;
        for (int i = 0; i < channels; i++) z_avg_abs[i] /= count_abs;

        double sum_z_avg_abs = 0;
        for (int i = 0; i < channels; i++) {
            if (i < G.length) sum_z_avg_abs += G[i] * z_avg_abs[i];
        }

        double Gamma_r = -0.691 + 10.0 * Math.log10(sum_z_avg_abs) - 10.0;

        // Relative threshold gate
        double[] z_avg_rel = new double[channels];
        int count_rel = 0;
        for (int j = 0; j < numWindows; j++) {
            if (l[j] > Gamma_r) {
                count_rel++;
                for (int i = 0; i < channels; i++) z_avg_rel[i] += z[i][j];
            }
        }

        if (count_rel == 0) return -70.0;
        for (int i = 0; i < channels; i++) z_avg_rel[i] /= count_rel;

        double sum_z_avg_rel = 0;
        for (int i = 0; i < channels; i++) {
            if (i < G.length) sum_z_avg_rel += G[i] * z_avg_rel[i];
        }

        return -0.691 + 10.0 * Math.log10(sum_z_avg_rel);
    }

    private static double parseSample(byte[] buffer, int index, int bytesPerSample) {
        if (bytesPerSample == 2) {
            int low = buffer[index] & 0xFF;
            int high = buffer[index + 1];
            return ((high << 8) | low) / 32768.0;
        } else if (bytesPerSample == 3) {
            int b1 = buffer[index] & 0xFF;
            int b2 = buffer[index + 1] & 0xFF;
            int b3 = buffer[index + 2];
            return ((b3 << 16) | (b2 << 8) | b1) / 8388608.0;
        } else if (bytesPerSample == 4) {
            int b1 = buffer[index] & 0xFF;
            int b2 = buffer[index + 1] & 0xFF;
            int b3 = buffer[index + 2] & 0xFF;
            int b4 = buffer[index + 3];
            return ((b4 << 24) | (b3 << 16) | (b2 << 8) | b1) / 2147483648.0;
        } else if (bytesPerSample == 1) {
            return buffer[index] / 128.0;
        }
        return 0;
    }

    // Stateful Biquad Filter to keep history across chunks
    private static class Biquad {
        double b0, b1, b2, a1, a2;
        double x1 = 0, x2 = 0, y1 = 0, y2 = 0;

        Biquad(double[] b, double[] a) {
            this.b0 = b[0]; this.b1 = b[1]; this.b2 = b[2];
            this.a1 = a[1]; this.a2 = a[2];
        }

        double process(double x) {
            double y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1; x1 = x;
            y2 = y1; y1 = y;
            return y;
        }
    }

    private static Biquad createPreFilter1(double fs) {
        double f0 = 1681.9744509555319, G = 3.99984385397, Q = 0.7071752369554193;
        double K = Math.tan(Math.PI * f0 / fs);
        double Vh = Math.pow(10.0, G / 20.0);
        double Vb = Math.pow(Vh, 0.499666774155);
        double a0_ = 1.0 + K / Q + K * K;

        double[] b = {
                (Vh + Vb * K / Q + K * K) / a0_,
                2.0 * (K * K - Vh) / a0_,
                (Vh - Vb * K / Q + K * K) / a0_
        };
        double[] a = {
                1.0,
                2.0 * (K * K - 1.0) / a0_,
                (1.0 - K / Q + K * K) / a0_
        };
        return new Biquad(b, a);
    }

    private static Biquad createPreFilter2(double fs) {
        double f0 = 38.13547087613982, Q = 0.5003270373253953;
        double K = Math.tan(Math.PI * f0 / fs);
        double a0_ = 1.0 + K / Q + K * K;

        double[] b = {1.0, -2.0, 1.0};
        double[] a = {
                1.0,
                2.0 * (K * K - 1.0) / a0_,
                (1.0 - K / Q + K * K) / a0_
        };
        return new Biquad(b, a);
    }
}