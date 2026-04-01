package com.javaloudnorm;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Public API for computing integrated loudness (LUFS) from an audio file.
 */
public class LoudnessCalculator {

    private LoudnessCalculator() {
    }

    /**
     * Computes the integrated loudness of an audio file in LUFS.
     *
     * @param filePath path to the audio file (WAV, FLAC, MP3, OGG, etc.)
     * @return loudness in LUFS as a double
     * @throws IOException                   if the file cannot be read
     * @throws UnsupportedAudioFileException if the audio format is not supported
     */
    public static double calculate(String filePath) throws IOException, UnsupportedAudioFileException {
        AudioData audioData = AudioFileReader.read(filePath);
        return LoudnessAnalyzer.calculateLoudness(audioData.getSamples(), audioData.getSampleRate());
    }

    /**
     * Computes the integrated loudness of audio from an InputStream in LUFS.
     *
     * @param inputStream the audio input stream (WAV, FLAC, MP3, OGG, etc.)
     * @return loudness in LUFS as a double
     * @throws IOException                   if the stream cannot be read
     * @throws UnsupportedAudioFileException if the audio format is not supported
     */
    public static double calculate(InputStream inputStream) throws IOException, UnsupportedAudioFileException {
        AudioData audioData = AudioFileReader.read(inputStream);
        return LoudnessAnalyzer.calculateLoudness(audioData.getSamples(), audioData.getSampleRate());
    }
}
