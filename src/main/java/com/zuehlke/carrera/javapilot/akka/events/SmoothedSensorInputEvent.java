package com.zuehlke.carrera.javapilot.akka.events;


public class SmoothedSensorInputEvent{
    private final double value;
    private final double raw;

    public SmoothedSensorInputEvent(double value, double raw) {
        this.value = value;
        this.raw = raw;
    }

    public double getValue() {
        return value;
    }

    public double getRaw() {
        return raw;
    }
}
