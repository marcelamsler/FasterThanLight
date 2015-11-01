package com.zuehlke.carrera.javapilot.akka.events;


public class SmoothedSensorInputEvent{
    private final double value;

    public SmoothedSensorInputEvent(double value) {
        this.value = value;
    }

    public double getValue() {
        return value;
    }
}
