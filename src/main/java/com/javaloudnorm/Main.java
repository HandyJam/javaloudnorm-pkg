package com.javaloudnorm;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {

    public static void main(String[] args) {
        String filePath = "/home/jordy/Music/09 Waves.flac";
        File audioFile = new File(filePath);

        if (!audioFile.exists() || !audioFile.isFile()) {
            System.err.println("Error: Could not find the file at " + audioFile.getAbsolutePath());
            return;
        }

        // Restored to your original test requirement
        int concurrentRequests = 1;
        System.out.println("Starting concurrency test with " + concurrentRequests + " simultaneous requests...");

        System.gc();
        printMemoryUsage("Baseline (Before execution)");

        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
        List<Callable<Void>> tasks = new ArrayList<>();

        long startTime = System.currentTimeMillis();

        for (int i = 1; i <= concurrentRequests; i++) {
            final int taskId = i;
            tasks.add(() -> {
                System.out.println("Task " + taskId + " started.");
                try (InputStream inputStream = new FileInputStream(audioFile)) {

                    // Call the new streaming class directly
                    double lufs = StreamingLoudness.analyze(inputStream);

                    System.out.printf("Task %d finished: %.2f LUFS%n", taskId, lufs);

                    printMemoryUsage("During Execution (Task " + taskId + ")");
                } catch (Exception e) {
                    System.err.println("Task " + taskId + " failed: " + e.getMessage());
                }
                return null;
            });
        }

        try {
            executor.invokeAll(tasks);
        } catch (InterruptedException e) {
            System.err.println("Execution was interrupted.");
        } finally {
            executor.shutdown();
        }

        long duration = System.currentTimeMillis() - startTime;
        System.out.println("\nAll " + concurrentRequests + " tasks completed in " + duration + " ms.");

        System.gc();
        printMemoryUsage("Final (After execution and GC cleanup)");
    }

    private static void printMemoryUsage(String phase) {
        Runtime runtime = Runtime.getRuntime();
        long usedMemoryMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long totalMemoryMB = runtime.totalMemory() / (1024 * 1024);
        System.out.printf("[%s] Used Memory: %d MB / %d MB%n", phase, usedMemoryMB, totalMemoryMB);
    }
}