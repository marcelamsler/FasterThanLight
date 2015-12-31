package com.zuehlke.carrera.javapilot.akka.events;


public class SmoothedSensorInputEvent{
    private final double value;
    private final double raw;
    private final long timestamp;

    public SmoothedSensorInputEvent(double value, double raw,long timestamp) {
        this.value = value;
        this.raw = raw;
        this.timestamp = timestamp;
    }

    public double getValue() {
        return value;
    }

    public double getRaw() {
        return raw;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
