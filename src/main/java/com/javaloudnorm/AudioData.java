package com.javaloudnorm;

import java.nio.file.Path;

public record AudioData(
        Path path,
        double sampleRate,
        int channelCount,
        long frameCount,
        double[][] samples
) {
}

