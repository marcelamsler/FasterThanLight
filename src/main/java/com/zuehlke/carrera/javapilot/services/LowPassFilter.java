package com.zuehlke.carrera.javapilot.services;


public class LowPassFilter {

    private double smoothed = 0.0;
    private int smoothingFactor;
    private long previousTimestamp = 0;

    public LowPassFilter(int smoothingFactor){
        this.smoothingFactor = smoothingFactor;
    }

    public double smoothen(double value, long currentTimestamp) {
        double elapsedTime = currentTimestamp - previousTimestamp;
        double diff = value - smoothed;
        if (elapsedTime < 60) {
            smoothed += elapsedTime * diff / smoothingFactor;
        } else {
            smoothed += diff / (smoothingFactor / 50);
        }
        previousTimestamp = currentTimestamp;
        return smoothed;
    }
}
