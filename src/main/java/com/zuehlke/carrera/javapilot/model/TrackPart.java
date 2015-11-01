package com.zuehlke.carrera.javapilot.model;


import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.math3.stat.StatUtils;

import java.util.ArrayList;


public class TrackPart {

    protected final ArrayList<Double> rawSensorValues;
    protected ArrayList<Double> sensorValues;

    protected TrackType type;

    public TrackPart(){
        sensorValues = new ArrayList<>();
        rawSensorValues = new ArrayList<>();
        type = TrackType.UNKNOWN;
    }

    public void pushSensorValue(double value, double raw){
        sensorValues.add(value);
        rawSensorValues.add(raw);
    }

    public ArrayList<Double> getRawSensorValues() {
        return rawSensorValues;
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

    public double getMax(){
        double max = 0.0;
        for(double value : sensorValues){
            max = Math.max(max, Math.abs(value));
        }
        return max;
    }

    public double getStdDev(){
        double[] array = ArrayUtils.toPrimitive(rawSensorValues.toArray(new Double[rawSensorValues.size()]));
        return Math.sqrt(StatUtils.variance(array));
    }

    public boolean isCurve(){
        return type == TrackType.LEFT || type == TrackType.RIGHT;
    }

    public boolean isStraight(){
        return type == TrackType.STRAIGHT;
    }

}
