package com.zuehlke.carrera.javapilot.akka.events;


public class LengthOfTrackComputedEvent {

    private final double length;

    public LengthOfTrackComputedEvent(double length) {
        this.length = length;
    }

    public double getLength() {
        return length;
    }
}
