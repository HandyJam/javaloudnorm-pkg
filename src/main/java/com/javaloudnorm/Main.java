package com.javaloudnorm;

import java.io.FileInputStream;
import java.io.InputStream;

public class Main {
    public static void main(String[] args) {
        try {
            long startTime = System.currentTimeMillis();
            try (InputStream is = new FileInputStream("/home/jordy/Music/01 Bones.flac")) {
                double loudness = LoudnessCalculator.calculate(is);
                long endTime = System.currentTimeMillis();
                double durationSeconds = (endTime - startTime) / 1000.0;
                System.out.println("Loudness calculated in " + String.format("%.2f", durationSeconds) + " seconds");
                System.out.println(loudness);
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}