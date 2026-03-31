package com.javaloudnorm;

import java.nio.file.Path;

public class Main {
    public static void main(String[] args) {
//        if (args.length != 1) {
//            System.err.println("Usage: java -jar javaloudnorm.jar <audio-file>");
//            return;
//        }

        try {
            String testFile = "/home/jordy/Music/01 Bones.flac";
//            AudioData audioData = AudioFileReader.read(Path.of(args[0]));
            AudioData audioData = AudioFileReader.read(Path.of(testFile));

            long startTime = System.nanoTime();
            double loudness = LoudnessCalculator.calculateLoudness(audioData.samples(), audioData.sampleRate());
            long endTime = System.nanoTime();
            double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
            System.out.println("Analysis completed in " + String.format("%.2f", durationSeconds) + " seconds");

            System.out.printf("File: %s%n", audioData.path());
            System.out.printf("Sample rate: %.0f Hz%n", audioData.sampleRate());
            System.out.printf("Channels: %d%n", audioData.channelCount());
            System.out.printf("Frames: %d%n", audioData.frameCount());
            System.out.printf("Integrated loudness: %.2f LKFS%n", loudness);
        } catch (Exception e) {
            System.err.println("Failed to analyze audio file: " + e.getMessage());
        }
    }
}