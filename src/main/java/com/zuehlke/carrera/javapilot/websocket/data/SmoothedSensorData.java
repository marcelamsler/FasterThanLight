package com.zuehlke.carrera.javapilot.websocket.data;

/**
 * @author Kirusanth Poopalasingam ( pkirusanth@gmail.com )
 */
public class SmoothedSensorData {
    public final double smoothValue;
    public final int currentPower;

    public SmoothedSensorData(double smoothValue, int currentPower) {
        this.smoothValue = smoothValue;
        this.currentPower = currentPower;
    }
}
