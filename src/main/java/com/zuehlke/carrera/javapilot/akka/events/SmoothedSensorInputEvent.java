package com.zuehlke.carrera.javapilot.akka.events;

/**
 * Created by mglauser on 14.10.15.
 */
public class SmoothedSensorInputEvent{
    private final double value;

    public SmoothedSensorInputEvent(double value) {
        this.value = value;
    }

    public double getValue() {
        return value;
    }
}
