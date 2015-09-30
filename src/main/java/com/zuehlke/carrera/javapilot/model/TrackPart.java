package com.zuehlke.carrera.javapilot.model;


import org.apache.commons.math3.stat.StatUtils;

import java.util.ArrayList;


public class TrackPart {

    private ArrayList<Double> sensorValues;

    private TrackType type;

    public TrackPart(){
        sensorValues = new ArrayList<>();
        type = TrackType.UNKNOWN;
    }

    public void pushSensorValue(double value){
        sensorValues.add(value);
    }

    public ArrayList<Double> getSensorValues(){
        return sensorValues;
    }

    public int getSize(){
        return sensorValues.size();
    }

    public TrackType getType() {
        return type;
    }

    public void setType(TrackType type) {
        this.type = type;
    }

    public double getMean(){
        double sum = 0.0;
        if(!sensorValues.isEmpty()) {
            for (double value : sensorValues) {
                sum += value;
            }
            return sum / sensorValues.size();
        }
        return sum;
    }
}
