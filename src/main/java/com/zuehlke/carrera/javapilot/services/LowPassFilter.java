package com.zuehlke.carrera.javapilot.services;


public class LowPassFilter {

    private double smoothed = 0.0;
    private int smoothing = 160;
    private long previousTimestamp = 0;

    public double smoothen(double value, long currentTimestamp) {
        double elapsedTime = currentTimestamp - previousTimestamp;
        double diff = value - smoothed;
        if (elapsedTime < 60) {
            smoothed += elapsedTime * diff / smoothing;
        } else {
            smoothed += diff / (smoothing / 50);
        }
        previousTimestamp = currentTimestamp;
        return smoothed;
    }
}
