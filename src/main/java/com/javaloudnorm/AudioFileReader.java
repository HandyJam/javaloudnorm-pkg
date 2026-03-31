package com.javaloudnorm;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AudioFileReader {
    private AudioFileReader() {
    }

    public static AudioData read(Path path) throws IOException, UnsupportedAudioFileException {
        if (!Files.exists(path)) {
            throw new IOException("File does not exist: " + path);
        }

        try (AudioInputStream sourceStream = AudioSystem.getAudioInputStream(path.toFile())) {
            AudioFormat baseFormat = sourceStream.getFormat();
            AudioFormat pcmFormat = toPcmSigned(baseFormat);

            try (AudioInputStream pcmStream = AudioSystem.getAudioInputStream(pcmFormat, sourceStream)) {
                byte[] audioBytes = readAllBytes(pcmStream);
                int channels = pcmFormat.getChannels();
                int frameSize = pcmFormat.getFrameSize();
                int sampleSizeBytes = pcmFormat.getSampleSizeInBits() / 8;
                long frameCount = audioBytes.length / frameSize;
                double[][] samples = decodePcm(audioBytes, channels, sampleSizeBytes, frameCount, pcmFormat.isBigEndian());
                return new AudioData(path, pcmFormat.getSampleRate(), channels, frameCount, samples);
            }
        }
    }

    private static AudioFormat toPcmSigned(AudioFormat format) {
        if (AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())) {
            return format;
        }

        int sampleSize = format.getSampleSizeInBits() > 0 ? format.getSampleSizeInBits() : 16;
        int channels = format.getChannels();
        float sampleRate = format.getSampleRate();
        return new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                sampleRate,
                sampleSize,
                channels,
                channels * (sampleSize / 8),
                sampleRate,
                false
        );
    }

    private static byte[] readAllBytes(AudioInputStream stream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = stream.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    private static double[][] decodePcm(byte[] audioBytes, int channels, int sampleSizeBytes, long frameCount, boolean bigEndian) {
        if (sampleSizeBytes != 2 && sampleSizeBytes != 3 && sampleSizeBytes != 4) {
            throw new IllegalArgumentException("Unsupported PCM sample size: " + (sampleSizeBytes * 8) + " bits");
        }

        double[][] samples = new double[(int) frameCount][channels];
        int frameSize = channels * sampleSizeBytes;
        double scale = Math.pow(2.0, (double) (sampleSizeBytes * 8 - 1));

        for (int frame = 0; frame < frameCount; frame++) {
            int frameOffset = frame * frameSize;
            for (int channel = 0; channel < channels; channel++) {
                int sampleOffset = frameOffset + channel * sampleSizeBytes;
                int raw = readSignedSample(audioBytes, sampleOffset, sampleSizeBytes, bigEndian);
                samples[frame][channel] = raw / scale;
            }
        }

        return samples;
    }

    private static int readSignedSample(byte[] bytes, int offset, int sampleSizeBytes, boolean bigEndian) {
        int value = 0;

        if (bigEndian) {
            for (int i = 0; i < sampleSizeBytes; i++) {
                value = (value << 8) | (bytes[offset + i] & 0xFF);
            }
        } else {
            for (int i = sampleSizeBytes - 1; i >= 0; i--) {
                value = (value << 8) | (bytes[offset + i] & 0xFF);
            }
        }

        int shift = 32 - sampleSizeBytes * 8;
        return (value << shift) >> shift;
    }
}
