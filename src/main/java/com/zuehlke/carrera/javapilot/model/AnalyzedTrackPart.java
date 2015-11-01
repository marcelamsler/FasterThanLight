package com.zuehlke.carrera.javapilot.model;


public class AnalyzedTrackPart extends TrackPart{
    private final int angle;
    private final double length;
    private final double radius;

    public AnalyzedTrackPart(TrackType type,int angle, double length, double radius) {
        this.type = type;
        this.angle = angle;
        this.length = length;
        this.radius = radius;
    }

    public int getAngle() {
        return angle;
    }

    public double getLength() {
        return length;
    }

    public double getRadius() {
        return radius;
    }
}
