package com.javaloudnorm;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Reads audio files into raw PCM samples using javax.sound.sampled.
 * Supports WAV natively; MP3, FLAC, and OGG via SPI providers on the classpath.
 */
public class AudioFileReader {

    private AudioFileReader() {
    }

    /**
     * Reads an audio file from a file path.
     *
     * @param filePath path to the audio file
     * @return AudioData containing deinterleaved samples and sample rate
     * @throws IOException                   if the file cannot be read
     * @throws UnsupportedAudioFileException if the format is not supported
     */
    public static AudioData read(String filePath) throws IOException, UnsupportedAudioFileException {
        AudioInputStream originalStream = AudioSystem.getAudioInputStream(new File(filePath));
        return decodeStream(originalStream);
    }

    /**
     * Reads audio from an InputStream.
     * The stream must support mark/reset (e.g. BufferedInputStream) for format detection.
     * If it does not, it will be wrapped in a BufferedInputStream automatically.
     *
     * @param inputStream the audio input stream
     * @return AudioData containing deinterleaved samples and sample rate
     * @throws IOException                   if the stream cannot be read
     * @throws UnsupportedAudioFileException if the format is not supported
     */
    public static AudioData read(InputStream inputStream) throws IOException, UnsupportedAudioFileException {
        InputStream buffered = inputStream.markSupported() ? inputStream : new BufferedInputStream(inputStream);
        AudioInputStream originalStream = AudioSystem.getAudioInputStream(buffered);
        return decodeStream(originalStream);
    }

    private static AudioData decodeStream(AudioInputStream originalStream) throws IOException, UnsupportedAudioFileException {
        AudioFormat originalFormat = originalStream.getFormat();

        // Decode to PCM signed 16-bit little-endian (universally supported target)
        AudioFormat decodedFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                originalFormat.getSampleRate(),
                16,
                originalFormat.getChannels(),
                originalFormat.getChannels() * 2,
                originalFormat.getSampleRate(),
                false // little-endian
        );

        AudioInputStream decodedStream;
        if (AudioSystem.isConversionSupported(decodedFormat, originalFormat)) {
            decodedStream = AudioSystem.getAudioInputStream(decodedFormat, originalStream);
        } else {
            // Already PCM — try to use as-is, converting bit depth if needed
            decodedStream = originalStream;
            decodedFormat = originalFormat;
        }

        // Read all bytes
        byte[] allBytes = readAllBytes(decodedStream);
        decodedStream.close();
        originalStream.close();

        int channels = decodedFormat.getChannels();
        int sampleRate = (int) decodedFormat.getSampleRate();
        int sampleSizeInBits = decodedFormat.getSampleSizeInBits();
        boolean bigEndian = decodedFormat.isBigEndian();
        int bytesPerSample = sampleSizeInBits / 8;
        int frameSize = channels * bytesPerSample;
        int numFrames = allBytes.length / frameSize;

        // Deinterleave and normalize to [-1.0, 1.0]
        double[][] samples = new double[channels][numFrames];
        ByteBuffer buffer = ByteBuffer.wrap(allBytes).order(bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);

        for (int frame = 0; frame < numFrames; frame++) {
            for (int ch = 0; ch < channels; ch++) {
                double sample;
                switch (sampleSizeInBits) {
                    case 8:
                        // 8-bit PCM is unsigned
                        sample = (buffer.get() & 0xFF) / 128.0 - 1.0;
                        break;
                    case 16:
                        sample = buffer.getShort() / 32768.0;
                        break;
                    case 24:
                        int b0 = buffer.get() & 0xFF;
                        int b1 = buffer.get() & 0xFF;
                        int b2 = buffer.get() & 0xFF;
                        int value;
                        if (bigEndian) {
                            value = (b0 << 16) | (b1 << 8) | b2;
                        } else {
                            value = (b2 << 16) | (b1 << 8) | b0;
                        }
                        // Sign extend from 24 bits
                        if (value >= 0x800000) {
                            value -= 0x1000000;
                        }
                        sample = value / 8388608.0;
                        break;
                    case 32:
                        if (decodedFormat.getEncoding() == AudioFormat.Encoding.PCM_FLOAT) {
                            sample = buffer.getFloat();
                        } else {
                            sample = buffer.getInt() / 2147483648.0;
                        }
                        break;
                    default:
                        throw new UnsupportedAudioFileException("Unsupported sample size: " + sampleSizeInBits + " bits");
                }
                samples[ch][frame] = sample;
            }
        }

        return new AudioData(samples, sampleRate);
    }

    private static byte[] readAllBytes(AudioInputStream stream) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int bytesRead;
        while ((bytesRead = stream.read(buf)) != -1) {
            baos.write(buf, 0, bytesRead);
        }
        return baos.toByteArray();
    }
}
